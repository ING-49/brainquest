"""
把 update-server 产物发布为 GitHub Release（App 通过 releases/latest/download 稳定地址拉取）。

流程：
  1. 读取 update-server/manifest.json，把产物路径改写为扁平文件名（Release 资产不支持子目录）
  2. 用 gh CLI 创建 Release（tag = v<版本名>，若已存在则复用并 --clobber 覆盖资产）
  3. 上传：GitHub版 manifest.json + 全量 APK + 增量补丁 + 内容包
  4. 校验 https://github.com/<repo>/releases/latest/download/manifest.json 可达且内容正确

用法：python tools/publish_github.py [--repo ING-49/brainquest]
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVER = os.path.join(ROOT, "update-server")


def gh(*args, capture=True):
    gh_exe = os.environ.get("GH_EXE", "gh")
    r = subprocess.run([gh_exe, *args], capture_output=capture, text=True)
    if r.returncode != 0:
        raise SystemExit(f"gh {' '.join(args[:2])} 失败:\n{r.stderr}")
    return r.stdout if capture else ""


def flatten(manifest: dict) -> tuple:
    """生成扁平路径的 GitHub 版 manifest 与待上传文件列表"""
    gh_manifest = dict(manifest)

    def flat(path: str) -> str:
        return os.path.basename(path)

    files = []
    if gh_manifest.get("fullApk"):
        files.append(os.path.join(SERVER, gh_manifest["fullApk"]))
        gh_manifest["fullApk"] = flat(gh_manifest["fullApk"])
    gh_manifest["patches"] = []
    for p in manifest.get("patches", []):
        src = os.path.join(SERVER, p["file"])
        files.append(src)
        gh_manifest["patches"].append({**p, "file": flat(p["file"])})
    gh_manifest["contentPacks"] = []
    for p in manifest.get("contentPacks", []):
        src = os.path.join(SERVER, p["file"])
        files.append(src)
        gh_manifest["contentPacks"].append({**p, "file": flat(p["file"])})

    gh_manifest_path = os.path.join(SERVER, "gh_release", "manifest.json")
    os.makedirs(os.path.dirname(gh_manifest_path), exist_ok=True)
    with open(gh_manifest_path, "w", encoding="utf-8") as f:
        json.dump(gh_manifest, f, ensure_ascii=False, indent=2)
    files.append(gh_manifest_path)
    return gh_manifest, files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=None, help="GitHub 仓库，默认用 gh 当前仓库")
    args = ap.parse_args()

    src_manifest_path = os.path.join(SERVER, "manifest.json")
    if not os.path.exists(src_manifest_path):
        raise SystemExit("manifest.json 不存在，请先运行 update-server/release.py")
    with open(src_manifest_path, encoding="utf-8") as f:
        manifest = json.load(f)

    version = manifest["latestVersionName"]
    tag = f"v{version}"
    gh_manifest, files = flatten(manifest)

    repo_args = ["-R", args.repo] if args.repo else []
    repo_url = args.repo or gh("repo", "view", "--json", "url", "--jq", ".url").strip()

    # 已有同名 tag 的 Release 则复用
    gh_exe = os.environ.get("GH_EXE", "gh")
    existing = subprocess.run(
        [gh_exe, "release", "view", tag, *repo_args],
        capture_output=True, text=True,
    )
    if existing.returncode == 0:
        print(f"Release {tag} 已存在，覆盖上传资产")
        gh("release", "upload", tag, *files, "--clobber", *repo_args, capture=False)
    else:
        patches = "、".join(f"{p['from']}→{p['to']}({p['size']//1024}KB)" for p in gh_manifest.get("patches", [])) or "无"
        notes = (
            f"最新版本 v{version}\n\n"
            f"- 全量 APK：{gh_manifest.get('fullApk', 'N/A')}\n"
            f"- 增量补丁：{patches}\n"
            f"- 内容包：{'、'.join(p['id'] + ' v' + str(p['version']) for p in gh_manifest.get('contentPacks', [])) or '无'}\n\n"
            f"App 内「设置 → 检查更新」即可自动更新。\n"
            f"SHA-256: {gh_manifest.get('fullApkSha256', '')[:16]}…"
        )
        notes_file = os.path.join(SERVER, "release_notes.md")
        with open(notes_file, "w", encoding="utf-8") as f:
            f.write(notes)
        print(f"创建 Release {tag} 并上传 {len(files)} 个资产…")
        gh("release", "create", tag,
           "--title", f"脑力大冒险 v{version}",
           "--notes-file", notes_file,
           *repo_args, capture=False)
        gh("release", "upload", tag, *files, "--clobber", *repo_args, capture=False)

    # 校验 latest 地址
    latest = f"https://github.com/{repo_url.split('github.com/')[-1].strip('/') if repo_url.startswith('http') else repo_url}/releases/latest/download/manifest.json"
    print("校验:", latest)
    served = None
    for attempt in range(6):
        try:
            with urllib.request.urlopen(latest, timeout=30) as resp:
                served = json.loads(resp.read())
            if served["latestVersionCode"] == gh_manifest["latestVersionCode"]:
                break
        except Exception as e:
            print(f"  第{attempt+1}次校验未就绪: {e}")
        time.sleep(5)
    assert served and served["latestVersionCode"] == gh_manifest["latestVersionCode"], "latest manifest 校验失败"
    print(f"✓ GitHub 托管生效：latest → v{served['latestVersionName']}(code {served['latestVersionCode']})")
    print(f"App 服务器地址填: {latest.rsplit('/', 1)[0]}")


if __name__ == "__main__":
    main()
