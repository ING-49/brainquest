"""自动玩速算英雄：读题→口算→点正确选项，处理胜负对话框。"""
import re
import subprocess
import time

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"


def sh(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return sh("shell", "cat", "/sdcard/ui.xml")


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))])


def center(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return ((x1 + x2) / 2, (y1 + y2) / 2)


def tap_text(xml, text, exact=True):
    if exact:
        m = re.search(r'text="' + re.escape(text) + r'"[^>]*bounds="(\[[^\"]+\])"', xml)
    else:
        m = re.search(r'text="[^"]*' + re.escape(text) + r'[^"]*"[^>]*bounds="(\[[^\"]+\])"', xml)
    if m:
        c = center(m.group(1))
        if c:
            tap(*c)
            return True
    return False


def solve(text):
    text = text.replace("×", "*").replace("÷", "/").replace("−", "-").replace("?", "").replace("=", "").strip()
    if re.fullmatch(r"[\d\s+\-*/().]+", text):
        try:
            v = eval(text, {"__builtins__": {}}, {})
            if isinstance(v, float) and v.is_integer():
                v = int(v)
            return str(v)
        except Exception:
            return None
    return None


def main():
    solved = 0
    for i in range(60):
        xml = dump()
        if "通关成功" in xml or "BOSS 击败" in xml:
            print(f">>> 胜利！共答对 {solved} 题")
            if not tap_text(xml, "下一关") :
                tap_text(xml, "领取奖励")
            return
        if "战败了" in xml:
            print(f">>> 战败（答对 {solved} 题）")
            tap_text(xml, "撤退")
            return
        qm = re.search(r'text="([^"]*= ?\?)"', xml)
        if not qm:
            time.sleep(0.8)
            continue
        q = qm.group(1)
        ans = solve(q)
        tapped = False
        if ans is not None:
            m = re.search(r'text="[A-D]\. ' + re.escape(ans) + r'"[^>]*bounds="(\[[^\"]+\])"', xml)
            if m:
                c = center(m.group(1))
                if c:
                    tap(*c)
                    tapped = True
                    solved += 1
                    print(f"Q{solved}: {q} -> {ans} ✔")
                    time.sleep(1.6)
        if not tapped:
            print(f"Q: {q} -> {ans} 未找到选项，等待")
            time.sleep(1.0)


if __name__ == "__main__":
    main()
