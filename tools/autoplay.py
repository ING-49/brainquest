"""自动玩速算英雄：读题→口算→点正确选项，处理胜负对话框。"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui as _ui  # dump 走 exec-out /dev/tty，Git Bash 下稳定（旧 /sdcard+cat 写法见坑 #13）

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"


def sh(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def dump():
    return _ui.dump()


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
            # v1.6.3 起选项拆成「A.」前缀 + 数值两个节点：先试老的单节点形式，再试纯数值节点
            m = re.search(r'text="[A-D]\. ' + re.escape(ans) + r'"[^>]*bounds="(\[[^\"]+\])"', xml)                 or re.search(r'text="' + re.escape(ans) + r'"[^>]*bounds="(\[[^\"]+\])"', xml)
            if m:
                c = center(m.group(1))
                if c:
                    tap(*c)
                    tapped = True
                    solved += 1
                    print(f"Q{solved}: {q} -> {ans} ✔")
                    time.sleep(1.6)
        if not tapped and ans is not None and "提交答案" in xml:
            # 填空题：点输入框 → input text 数字 → 提交（v1.6.8 起支持）
            fm = re.search(r'class="android.widget.EditText"[^>]*bounds="(\[[^\"]+\])"', xml)
            if fm:
                c = center(fm.group(1))
                if c:
                    tap(*c)
                    time.sleep(0.6)
                    subprocess.run([ADB, "shell", "input", "text", ans])
                    time.sleep(0.4)
                    if tap_text(xml, "提交答案"):
                        tapped = True
                        solved += 1
                        print(f"Q{solved}: {q} -> {ans} ✔（填空）")
                        time.sleep(1.6)
        if not tapped:
            print(f"Q: {q} -> {ans} 未找到选项，等待")
            time.sleep(1.0)


if __name__ == "__main__":
    main()
