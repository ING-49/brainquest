"""驱动 App 完成：检查更新 → 内容热更新 → 增量更新 → 系统安装，每步截图。"""
import re
import subprocess
import time

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"
SHOTS = r"E:/PROJECT_CCC/ZCode_project/APP-learn/.shots"


def sh(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True).stdout


def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return sh("shell", "cat", "/sdcard/ui.xml")


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))])


def tap_text(xml, text, exact=False):
    pat = (r'text="' + re.escape(text) + r'"') if exact else (r'text="[^"]*' + re.escape(text) + r'[^"]*"')
    m = re.search(pat + r'[^>]*bounds="(\[[^\"]+\])"', xml)
    if not m:
        return False
    n = list(map(int, re.findall(r"\d+", m.group(1))))
    tap((n[0] + n[2]) / 2, (n[1] + n[3]) / 2)
    return True


def swipe_up(big=False):
    y2 = "500" if big else "700"
    subprocess.run([ADB, "shell", "input", "swipe", "540", "1800", "540", y2, "300"])
    time.sleep(0.8)


def shot(name):
    with open(f"{SHOTS}/{name}.png", "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print("  [截图]", name)


def find_and_tap(text, max_scroll=4, exact=False):
    """滚动查找并点击，返回是否成功"""
    for _ in range(max_scroll):
        xml = dump()
        if tap_text(xml, text, exact):
            return True
        swipe_up()
    xml = dump()
    return tap_text(xml, text, exact)


def scroll_dump(max_scroll=4, keyword=None):
    """滚动直到看到 keyword，返回当时的 dump"""
    for _ in range(max_scroll):
        xml = dump()
        if keyword and keyword in xml:
            return xml
        swipe_up()
    return dump()


def scroll_top():
    for _ in range(6):
        subprocess.run([ADB, "shell", "input", "swipe", "540", "600", "540", "1900", "200"])
    time.sleep(0.5)


print("== 0. 重启 App ==")
subprocess.run([ADB, "shell", "am", "force-stop", "com.brainquest.game"])
subprocess.run([ADB, "shell", "am", "start", "-n", "com.brainquest.game/.MainActivity"])
time.sleep(4)

print("== 1. 进入 设置与更新 ==")
xml = dump()
tap_text(xml, "我的", exact=True)
time.sleep(1.2)
xml = dump()
tap_text(xml, "设置与更新")
time.sleep(1.5)
shot("update_0_settings")

print("== 2. 检查更新 ==")
assert find_and_tap("检查更新"), "找不到检查更新按钮"
time.sleep(4)
xml = scroll_dump(4, "服务器版本")
m = re.search(r'text="([^"]*服务器版本[^"]*)"', xml)
print("  状态:", m.group(1) if m else "未见状态")
shot("update_1_checked")

print("== 3. 内容热更新 ==")
if find_and_tap("更新全部内容包"):
    xml = scroll_dump(5, "已更新")
    time.sleep(1)
    xml = dump()
    m = re.search(r'text="([^"]*(?:已更新|已是最新)[^"]*)"', xml)
    m2 = re.search(r'text="(当前题库：[^"]*)"', xml)
    print("  结果:", m.group(1) if m else "?")
    print("  题库:", m2.group(1) if m2 else "?")
    shot("update_2_packs")
else:
    xml = dump()
    m2 = re.search(r'text="(当前题库：[^"]*)"', xml)
    print("  无待更新内容包（已最新），题库:", m2.group(1) if m2 else "?")

print("== 4. 增量更新 APK ==")
assert find_and_tap("增量更新"), "找不到增量更新按钮"
time.sleep(3)
shot("update_3_downloading")
xml = dump()
print("  下载/合成:", [t for t in re.findall(r'text="([^"]{1,70})"', xml) if any(k in t for k in ["下载", "补丁", "合成", "校验", "安装", "APK"])][:4])

# 等系统安装器
installer = None
for i in range(60):
    xml = dump()
    if ("安装" in xml and ("脑力大冒险" in xml or "brainquest" in xml.lower())) or "版本更低" in xml or "继续安装" in xml:
        installer = xml
        break
    time.sleep(1)
time.sleep(1.5)
shot("update_4_installer")
print("  安装器文本:", [t for t in re.findall(r'text="([^"]{1,50})"', installer or "")][:10])

# 处理安装器（可能先有权限确认页）
for label in ["继续", "继续安装", "安装", "更新", "仍要安装"]:
    xml2 = dump()
    if tap_text(xml2, label, exact=(label in ("安装", "更新"))):
        print("  点击:", label)
        time.sleep(5)
shot("update_5_done")
xml = dump()
print("  最终界面:", [t for t in re.findall(r'text="([^"]{1,40})"', xml) if t.strip()][:8])
