"""贪吃蛇吃豆闭环自动验证（暂停分步法）。

原理：暂停时滑动转向会把方向缓存(pendingDir)，恢复后下一步必然生效 → 可精确逐步操控，
不依赖计时。像素识别食物/蛇头，逐格逼近吃到豆子。用法：python tools/snake_autoeat.py

v1.6.6 起：屏幕方向键已移除 → 转向改用棋盘内滑动；配色改过，色值同步更新。
"""
import html
import os
import re
import subprocess
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

ADB = ui.ADB
SHOT = os.path.join(os.environ.get("TEMP", "."), "snake_auto.png")
COLS, ROWS = 15, 20

# v1.6.6 棋盘配色
BOARD = (22, 48, 27)        # Color(0xFF16301B)
HEAD = (105, 240, 174)      # Color(0xFF69F0AE)
FOOD = (255, 82, 82)        # Color(0xFFFF5252)
RESUME_S = 0.5              # 恢复时长需 > tick（300ms 起）


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(int(x)), str(int(y))], capture_output=True)


def swipe(x1, y1, x2, y2, ms=140):
    subprocess.run([ADB, "shell", "input", "swipe", str(int(x1)), str(int(y1)),
                    str(int(x2)), str(int(y2)), str(ms)], capture_output=True)


def text_node(label, contains=True):
    """按文案定位按钮（emoji 也在 text 里，用包含匹配）"""
    for n in ui.nodes():
        t = n[0]
        if (label in t) if contains else (t == label):
            return ui.center(n)
    return None


def screenshot():
    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    return np.array(Image.open(SHOT).convert("RGB"))


def close_to(img, rgb, tol):
    return ((np.abs(img[:, :, 0].astype(int) - rgb[0]) < tol) &
            (np.abs(img[:, :, 1].astype(int) - rgb[1]) < tol) &
            (np.abs(img[:, :, 2].astype(int) - rgb[2]) < tol))


def dim(rgb, f=0.6):
    """暂停时棋盘上有 40% 黑蒙层 → 观察色 = 原色 × 0.6"""
    return tuple(int(round(c * f)) for c in rgb)


def close_to_any(img, rgb, tol):
    m = close_to(img, rgb, tol)
    m |= close_to(img, dim(rgb), tol)
    return m


def board_box(img):
    """棋盘底色是整个屏幕里最大的一块 → 直接取最高频色的包围盒（自适应蒙层/配色）"""
    band = img[int(img.shape[0] * 0.25):int(img.shape[0] * 0.78)].reshape(-1, 3)
    cols, counts = np.unique(band, axis=0, return_counts=True)
    main = tuple(int(v) for v in cols[int(np.argmax(counts))])
    ys, xs = np.where(close_to(img, main, 8))
    if len(xs) < 10000:
        return None
    return int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())


def mask_cell(img, box, mask):
    x0, x1, y0, y1 = box
    sub = mask[y0:y1 + 1, x0:x1 + 1]
    ys, xs = np.where(sub)
    if len(xs) == 0:
        return None
    return int(ys.mean() // ((y1 - y0) / ROWS)), int(xs.mean() // ((x1 - x0) / COLS))


def food_cell(img, box):
    m = close_to_any(img, FOOD, 42)
    return mask_cell(img, box, m)


def head_cell(img, box):
    return mask_cell(img, box, close_to_any(img, HEAD, 30))


def read_hud():
    """从 uiautomator dump 读 HUD：返回 (得分, 是否结束)"""
    x = ui.dump()
    m = re.search(r'text="[^"]*得分 (\d+)"', x)
    return (int(m.group(1)) if m else None), ("游戏结束" in x)


def turn(box, d):
    """棋盘内滑动转向（暂停中调用 → 只缓存方向）"""
    x0, x1, y0, y1 = box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    dist = 130
    delta = {"up": (0, -dist), "down": (0, dist), "left": (-dist, 0), "right": (dist, 0)}[d]
    swipe(cx, cy, cx + delta[0], cy + delta[1])


def main():
    ui.start()
    if not ui.tap_text("贪吃蛇", exact=False, wait=6):
        print("❌ 未找到贪吃蛇入口"); return 1
    time.sleep(0.5)

    restart = text_node("重开一局")
    pause = text_node("暂停") or text_node("继续")
    if restart is None or pause is None:
        print(f"❌ 未定位到按钮 restart={restart} pause={pause}"); return 1
    print(f"按钮：重开 {restart} 暂停 {pause}")

    tap(*restart)
    time.sleep(0.9)
    img = screenshot()
    box = board_box(img)
    if box is None:
        print("❌ 未识别到棋盘（新配色未同步？）"); return 1
    print(f"棋盘 {box}  起始 蛇头 {head_cell(img, box)} 食物 {food_cell(img, box)}")

    turn(box, "right")          # 起步
    time.sleep(0.2)
    tap(*pause)                 # 立刻暂停 → 分步模式
    time.sleep(0.4)

    last_dir = "right"
    prev_head = None
    stuck = 0
    for step in range(80):
        score, over = read_hud()
        if score is not None and score >= 1:
            print(f"✅ 吃到豆子！HUD 得分 = {score}（步数 {step}）")
            return 0
        if over:
            print(f"[{step}] 本局结束（未吃到），重开")
            tap(*restart)
            time.sleep(0.9)
            turn(box, "right")
            time.sleep(0.2)
            tap(*pause)
            time.sleep(0.4)
            prev_head = None
            stuck = 0
            continue
        img = screenshot()
        box = board_box(img) or box
        head = head_cell(img, box)
        food = food_cell(img, box)
        if head is None or food is None:
            print(f"[{step}] 识别失败 head={head} food={food}")
            return 1
        if prev_head == head:
            stuck += 1
        else:
            stuck = 0
        prev_head = head
        if stuck >= 3:
            print(f"[{step}] 蛇头停滞判定本局结束，重开")
            tap(*restart)
            time.sleep(0.9)
            turn(box, "right")
            time.sleep(0.2)
            tap(*pause)
            time.sleep(0.4)
            prev_head = None
            stuck = 0
            continue
        hr, hc = head
        fr, fc = food
        # 先对齐行，再对齐列（两次转向互相垂直，天然避免掉头）
        if hr != fr:
            d = "down" if fr > hr else "up"
        elif hc != fc:
            d = "right" if fc > hc else "left"
        else:
            d = last_dir
        opposite = {"up": "down", "down": "up", "left": "right", "right": "left"}
        if d == opposite[last_dir]:
            d = "down" if last_dir in ("left", "right") else "right"
        turn(box, d)            # 暂停中：只缓存转向
        last_dir = d
        # 恢复 → 只放行一格（自校正：确认真的走了一格，否则重试）
        for _ in range(5):
            tap(*pause)
            time.sleep(RESUME_S)
            tap(*pause)
            time.sleep(0.2)
            img2 = screenshot()
            box2 = board_box(img2) or box
            head2 = head_cell(img2, box2)
            if head2 and (abs(head2[0] - hr) + abs(head2[1] - hc)) >= 1:
                break
        print(f"[{step}] 头({hr},{hc}) 食({fr},{fc}) → {d}")
    print("❌ 80 步内未吃到")
    return 1


if __name__ == "__main__":
    sys.exit(main())
