"""驱动模拟器完成联机对战：逐题作答（数学算/其他点A），最后提交成绩"""
import subprocess
import re
import time

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"


def sh(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True).stdout


def dump():
    for _ in range(4):
        subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
        xml = sh("shell", "cat", "/sdcard/ui.xml")
        if len(xml) > 900:
            return xml
        time.sleep(1)
    return ""


def tap_m(m):
    n = list(map(int, re.findall(r"\d+", m.group(1))))
    subprocess.run([ADB, "shell", "input", "tap", str((n[0] + n[2]) // 2), str((n[1] + n[3]) // 2)])


def tap_text(xml, text, exact=False):
    pat = (r'text="' + re.escape(text) + r'"') if exact else (r'text="[^"]*' + re.escape(text) + r'[^"]*"')
    m = re.search(pat + r'[^>]*bounds="(\[[^\"]+\])"', xml)
    if not m:
        return False
    tap_m(m)
    return True


def shot(name):
    subprocess.run([ADB, "exec-out", "screencap", "-p"],
                   stdout=open(rf"E:/PROJECT_CCC/ZCode_project/APP-learn/.shots/{name}.png", "wb"))


seen = []
for n in range(10):
    xml = dump()
    if not xml:
        continue
    if "胜利" in xml or "惜败" in xml or "平局" in xml:
        print("结果页出现")
        shot("pk_result")
        break
    expr = re.search(r'text="([^"]+?) = \?"', xml)
    fill = "输入答案后提交" in xml
    ans_val = None
    if expr:
        e = (expr.group(1).replace("×", "*").replace("÷", "/").replace("−", "-")
             .replace("?", "").replace("=", "").replace("，", "").strip())
        try:
            ans_val = eval(e)
        except Exception:
            pass
    if fill:
        fld = (re.search(r'text="输入答案后提交"[^>]*bounds="(\[[^\"]+\])"', xml)
               or re.search(r'class="android.widget.EditText"[^>]*bounds="(\[[^\"]+\])"', xml))
        if fld:
            tap_m(fld)
            time.sleep(0.5)
        if ans_val is not None:
            v = int(ans_val) if float(ans_val).is_integer() else ans_val
            subprocess.run([ADB, "shell", "input", "text", str(v)])
        tap_text(dump(), "提交答案")
    else:
        opts = re.findall(r'text="([ABCD])\. ([^\"｜]{1,24})"', xml)
        done = False
        if ans_val is not None:
            for L, v in opts:
                try:
                    if float(v) == float(ans_val):
                        m = re.search(r'text="' + L + r'\. [^\"]*"[^>]*bounds="(\[[^\"]+\])"', xml)
                        if m:
                            tap_m(m)
                            done = True
                        break
                except Exception:
                    pass
        if not done:
            m = re.search(r'text="A\.[^\"]*"[^>]*bounds="(\[[^\"]+\])"', xml)
            if m:
                tap_m(m)
    qtexts = [t for t in re.findall(r'text="([^"]{4,60})"', xml) if "？" in t]
    seen.append(qtexts[0][:34] if qtexts else f"Q{n+1}")
    time.sleep(2.2)
    x2 = dump()
    if not (tap_text(x2, "下一题 →") or tap_text(x2, "完成（提交成绩）")):
        # 可能双方都完成了 → 结果页
        shot("pk_result")
        print("战斗结束")
        break
    time.sleep(1.5)

print("本局题目:")
for t in seen:
    print("  ", t)
shot("pk_end")
