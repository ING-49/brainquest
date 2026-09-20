"""UI 自动化小工具：uiautomator 文本定位 + 点击/滑动/截图。

供验证脚本复用（`from ui import tap_text, swipe, shot`）。
注意：dump 走 `exec-out ... /dev/tty`（Git Bash 下 /sdcard 会被转成本地路径），
并做 html.unescape（emoji 在 dump 里是 &#127968; 形式）。
"""
import html
import re
import subprocess
import time

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"
PKG = "com.brainquest.game"


def dump():
    raw = subprocess.run([ADB, "exec-out", "uiautomator", "dump", "/dev/tty"],
                         capture_output=True).stdout.decode("utf-8", "ignore")
    return html.unescape(raw)


def nodes(x=None):
    """返回 [(text, content_desc, x0, y0, x1, y1)]"""
    x = x or dump()
    out = []
    for m in re.finditer(r"<node[^>]*>", x):
        s = m.group(0)
        t = re.search(r'text="([^"]*)"', s)
        d = re.search(r'content-desc="([^"]*)"', s)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if not b:
            continue
        out.append((
            t.group(1) if t else "",
            d.group(1) if d else "",
            int(b.group(1)), int(b.group(2)), int(b.group(3)), int(b.group(4)),
        ))
    return out


def texts():
    return [n[0] for n in nodes() if n[0]]


def find(text, exact=True, last=False):
    hits = [n for n in nodes() if (n[0] == text if exact else text in n[0])]
    if not hits:
        return None
    return hits[-1] if last else hits[0]


def center(node):
    return (node[2] + node[4]) // 2, (node[3] + node[5]) // 2


def tap(x, y, delay=0.4):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))], capture_output=True)
    time.sleep(delay)


def tap_text(text, exact=True, last=False, delay=0.7, wait=0):
    """按文本点击；wait>0 时先轮询等待该文本出现"""
    deadline = time.time() + wait
    while True:
        n = find(text, exact, last)
        if n is not None:
            tap(*center(n), delay=delay)
            return True
        if time.time() >= deadline:
            return False
        time.sleep(0.5)


def swipe(x1, y1, x2, y2, ms=220, delay=0.4):
    subprocess.run([ADB, "shell", "input", "swipe", str(int(x1)), str(int(y1)),
                    str(int(x2)), str(int(y2)), str(int(ms))], capture_output=True)
    time.sleep(delay)


def shot(path):
    with open(path, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    return path


def key(code="BACK", delay=0.6):
    subprocess.run([ADB, "shell", "input", "keyevent", code], capture_output=True)
    time.sleep(delay)


def start(fresh=True):
    """回到首页：默认冷启动（避免停在上一轮的页面里）"""
    if fresh:
        subprocess.run([ADB, "shell", "am", "force-stop", PKG], capture_output=True)
    subprocess.run([ADB, "shell", "am", "start", "-n", f"{PKG}/.MainActivity"], capture_output=True)
    time.sleep(3)
