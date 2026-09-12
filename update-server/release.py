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
    sdk = os.environ.get("ANDROID_HOME", r"E:/Tools/Android-Studio/Android/SDK")
    exe = "aapt2.exe" if os.name == "nt" else "aapt2"
    aapt2 = os.path.join(sdk, "build-tools", "34.0.0", exe)
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


def build_patch(manifest):
    old, new = latest_pair()
    if not new:
        print("[patch] apks/ 下没有 APK，跳过")
        return
    code_n, name_n = apk_version_code(new)
    if not old:
        manifest["latestVersionName"] = name_n
        manifest["latestVersionCode"] = code_n
        manifest["fullApk"] = f"apks/{os.path.basename(new)}"
        manifest["fullApkSha256"] = sha256(new)
        print(f"[patch] 只有新版本 {os.path.basename(new)} (code={code_n})，登记全量下载")
        return

    code_o, name_o = apk_version_code(old)
    if code_o >= code_n:
        raise SystemExit(f"[patch] 版本号异常：old code={code_o} 应小于 new code={code_n}，请检查 apks/ 下的文件")
    data, new_size = make_custom_patch(old, new)
    patch_path = os.path.join(PATCHES, f"{code_o}_to_{code_n}.patch")
    with open(patch_path, "wb") as f:
        f.write(data)
    size = len(data)
    verify_custom_patch(data, old, sha256(new))
    manifest["latestVersionName"] = name_n
    manifest["latestVersionCode"] = code_n
    manifest["fullApk"] = f"apks/{os.path.basename(new)}"
    manifest["fullApkSha256"] = sha256(new)
    patches = [p for p in manifest.get("patches", []) if not (p["from"] == code_o and p["to"] == code_n)]
    patches.append({
        "from": code_o, "to": code_n,
        "file": f"patches/{code_o}_to_{code_n}.patch",
        "sha256": sha256(patch_path),
        "size": size,
    })
    manifest["patches"] = patches
    print(f"[patch] v{name_o}(code={code_o})→v{name_n}(code={code_n}): {size/1024:.1f} KB（新 APK {new_size/1048576:.1f} MB，压缩率 {size/new_size*100:.1f}%）")


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
