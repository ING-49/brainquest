"""
一键发布更新服务器内容：
  1. 把 packs/src/*.json 打包成 zip 内容包（带 sha256）
  2. 用 bsdiff4 生成 APK 增量补丁（需要 apks/ 里有 old/new 两个 APK）
  3. 生成 manifest.json

用法：
  python release.py                     # 全部三步
  python release.py --packs-only        # 只更新内容包
"""
import argparse
import hashlib
import json
import os
import re
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get("ANDROID_HOME", r"E:/Tools/Android-Studio/Android/SDK")
APKS = os.path.join(ROOT, "apks")
PATCHES = os.path.join(ROOT, "patches")
PACKS = os.path.join(ROOT, "packs")
SRC = os.path.join(PACKS, "src")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_manifest():
    p = os.path.join(ROOT, "manifest.json")
    if os.path.exists(p):
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    return {"latestVersionCode": 0, "latestVersionName": "", "fullApk": "",
            "fullApkSha256": "", "patches": [], "contentPacks": [], "notice": ""}


def build_packs(manifest):
    existing = {p["id"]: p["version"] for p in manifest.get("contentPacks", [])}
    result = []
    for name in sorted(os.listdir(SRC)):
        if not name.endswith(".json"):
            continue
        pack_id = name[:-5]
        src = os.path.join(SRC, name)
        with open(src, encoding="utf-8") as f:
            data = json.load(f)
        version = data.get("version", 1)
        zpath = os.path.join(PACKS, f"{pack_id}_v{version}.zip")
        with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
            z.write(src, name)
        size = os.path.getsize(zpath)
        result.append({
            "id": pack_id,
            "version": version,
            "file": f"packs/{pack_id}_v{version}.zip",
            "sha256": sha256(zpath),
            "size": size,
            "note": data.get("note", f"{data.get('subject','')} 加餐包"),
        })
        print(f"[pack] {pack_id} v{version}  {size/1024:.1f} KB  sha256={result[-1]['sha256'][:12]}…")
    manifest["contentPacks"] = result


def apk_version_code(path):
    """用 aapt2 从 APK 中读取真实 versionCode / versionName"""
    sdk = SDK
    exe = "aapt2.exe" if os.name == "nt" else "aapt2"
    bt = os.path.join(sdk, "build-tools", os.environ.get("ANDROID_BUILD_TOOLS", "34.0.0"))
    if not os.path.isdir(bt):
        # 环境变量指定版本不存在时，任选一个带 aapt2 的版本（CI 兼容）
        for v in sorted(os.listdir(os.path.join(sdk, "build-tools")), reverse=True):
            if os.path.isfile(os.path.join(sdk, "build-tools", v, exe)):
                bt = os.path.join(sdk, "build-tools", v)
                break
    aapt2 = os.path.join(bt, exe)
    import subprocess
    out = subprocess.run([aapt2, "dump", "badging", path], capture_output=True, text=True).stdout
    m = re.search(r"versionCode='(\d+)'", out)
    n = re.search(r"versionName='([^']+)'", out)
    return (int(m.group(1)) if m else 0), (n.group(1) if n else "?")


def latest_pair():
    """按 versionCode 命名约定找 apks/BrainQuest-vX.Y.Z.apk，用 apk 文件名排序取最新两个"""
    import re
    apks = []
    for f in os.listdir(APKS):
        m = re.match(r"BrainQuest-v([\d.]+)\.apk$", f)
        if m:
            apks.append((tuple(map(int, m.group(1).split("."))), f))
    apks.sort()
    if len(apks) >= 2:
        return os.path.join(APKS, apks[-2][1]), os.path.join(APKS, apks[-1][1])
    return None, (os.path.join(APKS, apks[-1][1]) if apks else None)


def make_custom_patch(old_path, new_path):
    """生成自研 BQDELTA1 增量补丁（滚动哈希块匹配，COPY/LIT 操作流，语义完全可控）"""
    import delta
    old = open(old_path, "rb").read()
    new = open(new_path, "rb").read()
    return delta.delta_bytes(old, new), len(new)


def verify_custom_patch(patch_bytes, old_path, expect_new_sha):
    """用 Python 复现 App 端解码逻辑，校验补丁合成结果与新版 APK 一致"""
    import delta
    old = open(old_path, "rb").read()
    delta.verify(patch_bytes, old, expect_new_sha)


def signer_sha(path):
    """读取 APK 签名证书指纹，用于跨签名守卫"""
    import subprocess
    exe = "apksigner.bat" if os.name == "nt" else "apksigner"
    bt = os.environ.get("ANDROID_BUILD_TOOLS", "34.0.0")
    for cand in [bt] + sorted(os.listdir(os.path.join(SDK, "build-tools")), reverse=True):
        signer = os.path.join(SDK, "build-tools", cand, exe)
        if os.path.isfile(signer):
            out = subprocess.run([signer, "verify", "--print-certs", path],
                                 capture_output=True, text=True).stdout
            m = re.search(r"SHA-256 digest: ([0-9a-f]+)", out)
            if m:
                return m.group(1)
    return "unknown"


def build_patch(manifest):
    """多版本补丁链：为 apks/ 中每个签名兼容、版本更老的历史 APK 生成 →最新版 的补丁。
    跨签名（如 debug→release 迁移）自动跳过，只登记全量。
    """
    all_apks = []
    for f in sorted(os.listdir(APKS)):
        p = os.path.join(APKS, f)
        if not f.lower().endswith(".apk"):
            continue
        code, name = apk_version_code(p)
        all_apks.append((code, name, p))
    if not all_apks:
        print("[patch] apks/ 下没有 APK，跳过")
        return
    all_apks.sort()  # 按 versionCode 升序
    code_n, name_n, new_path = all_apks[-1]
    new_size = os.path.getsize(new_path)
    sig_n = signer_sha(new_path)

    manifest["latestVersionName"] = name_n
    manifest["latestVersionCode"] = code_n
    manifest["fullApk"] = f"apks/{os.path.basename(new_path)}"
    manifest["fullApkSha256"] = sha256(new_path)

    patches = []
    for code_o, name_o, old_path in all_apks[:-1]:
        if code_o >= code_n:
            print(f"[patch] 跳过 {name_o}：版本不比最新旧")
            continue
        sig_o = signer_sha(old_path)
        if sig_o != sig_n:
            print(f"[patch] 跳过 v{name_o}(code={code_o})：签名不同（签名迁移版本走全量）")
            continue
        patch_name = f"{code_o}_to_{code_n}.patch"
        patch_path = os.path.join(PATCHES, patch_name)
        if os.path.exists(patch_path) and os.path.getsize(patch_path) > 0:
            # 已生成过（幂等：重跑跳过耗时差分）
            data = open(patch_path, "rb").read()
            try:
                verify_custom_patch(data, old_path, sha256(new_path))
            except AssertionError:
                data = None
            if data:
                patches.append({"from": code_o, "to": code_n, "file": f"patches/{patch_name}",
                                "sha256": sha256(patch_path), "size": len(data)})
                print(f"[patch] 复用已验证补丁 {patch_name}")
                continue
        data, new_size = make_custom_patch(old_path, new_path)
        with open(patch_path, "wb") as f:
            f.write(data)
        verify_custom_patch(data, old_path, sha256(new_path))
        patches.append({"from": code_o, "to": code_n, "file": f"patches/{patch_name}",
                        "sha256": sha256(patch_path), "size": len(data)})
        print(f"[patch] v{name_o}(code={code_o})→v{name_n}: {len(data)/1024:.1f} KB")
    manifest["patches"] = sorted(patches, key=lambda p: p["from"])
    print(f"[patch] 补丁链共 {len(manifest['patches'])} 条（覆盖 v1.1.1+ 全部正式签名版本）")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--packs-only", action="store_true")
    args = ap.parse_args()
    manifest = load_manifest()
    build_packs(manifest)
    if not args.packs_only:
        build_patch(manifest)
    with open(os.path.join(ROOT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print("[manifest] 已生成 manifest.json")


if __name__ == "__main__":
    main()
