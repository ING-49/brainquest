"""贪吃蛇吃豆闭环自动验证（暂停分步法）。

原理：暂停时点方向键会把转向缓存(pendingDir)，恢复后下一步必然生效 → 可精确逐步操控，
不依赖计时。像素识别食物/蛇头，逐格逼近吃到豆子。用法：python tools/snake_autoeat.py
"""
import html
import re
import subprocess
import sys
import time

import numpy as np
from PIL import Image

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"
SHOT = r"C:/Users/gk153/AppData/Local/Temp/snake_auto.png"
COLS, ROWS = 15, 20

DPAD = {"up": (540, 1794), "left": (356, 1908), "down": (540, 1908), "right": (724, 1908)}
RESTART = (480, 2090)
PAUSE = (206, 2090)
CELL_PX = 3000.0   # 单个蛇格绘制面积（约 55x55）


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(x), str(y)], capture_output=True)


def screenshot():
    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    return np.array(Image.open(SHOT).convert("RGB"))


def board_box(img):
    green = ((np.abs(img[:, :, 0].astype(int) - 27) < 26) &
             (np.abs(img[:, :, 1].astype(int) - 94) < 32) &
             (np.abs(img[:, :, 2].astype(int) - 32) < 26))
    ys, xs = np.where(green)
    if len(xs) == 0:
        return None
    return xs.min(), xs.max(), ys.min(), ys.max()


def mask_cell(img, box, mask):
    x0, x1, y0, y1 = box
    sub = mask[y0:y1 + 1, x0:x1 + 1]
    ys, xs = np.where(sub)
    if len(xs) == 0:
        return None
    return int(ys.mean() // ((y1 - y0) / ROWS)), int(xs.mean() // ((x1 - x0) / COLS))


def food_cell(img, box):
    return mask_cell(img, box, (img[:, :, 0] > 190) & (img[:, :, 1] < 95) & (img[:, :, 2] < 95))


def head_cell(img, box):
    m = ((np.abs(img[:, :, 0].astype(int) - 124) < 32) &
         (np.abs(img[:, :, 1].astype(int) - 179) < 32) &
         (np.abs(img[:, :, 2].astype(int) - 66) < 34))
    return mask_cell(img, box, m)


def body_len(img):
    m = ((np.abs(img[:, :, 0].astype(int) - 174) < 30) &
         (np.abs(img[:, :, 1].astype(int) - 213) < 30) &
         (np.abs(img[:, :, 2].astype(int) - 129) < 30))
    return m.sum() / CELL_PX


def read_hud():
    """从 uiautomator dump 读 HUD：返回 (得分, 是否结束)"""
    raw = subprocess.run([ADB, "exec-out", "uiautomator", "dump", "/dev/tty"],
                         capture_output=True).stdout.decode("utf-8", "ignore")
    x = html.unescape(raw)
    m = re.search(r'text="[^"]*得分 (\d+)"', x)
    return (int(m.group(1)) if m else None), ("游戏结束" in x)


def main():
    tap(*RESTART)
    time.sleep(1.0)
    img = screenshot()
    box = board_box(img)
    if box is None:
        print("未识别到棋盘"); return 1
    print(f"起始 蛇头 {head_cell(img, box)} 食物 {food_cell(img, box)}")

    # 起步（按当前方向键即可启动），随后立刻暂停进入分步模式
    tap(*DPAD["right"])
    time.sleep(0.15)
    tap(*PAUSE)
    time.sleep(0.4)

    last_dir = "right"
    prev_head = None
    stuck = 0
    for step in range(80):
        score, over = read_hud()
        if score is not None and score >= 1:
            print(f"✅ 吃到豆子！HUD 得分 = {score}")
            return 0
        if over:
            print(f"[{step}] 本局结束（未吃到），重开")
            tap(*RESTART)
            time.sleep(1.0)
            tap(*DPAD["right"])
            time.sleep(0.15)
            tap(*PAUSE)
            time.sleep(0.4)
            prev_head = None
            stuck = 0
            continue
        img = screenshot()
        box = board_box(img)
        head = head_cell(img, box)
        food = food_cell(img, box)
        if head is None or food is None:
            print(f"[{step}] 识别失败"); return 1
        if prev_head == head:
            stuck += 1
        else:
            stuck = 0
        prev_head = head
        if stuck >= 3:
            # 蛇头连续不动 = 本局已结束（弹窗可能被误触关闭，HUD 无提示）
            print(f"[{step}] 蛇头停滞判定本局结束，重开")
            tap(*RESTART)
            time.sleep(1.0)
            tap(*DPAD["right"])
            time.sleep(0.15)
            tap(*PAUSE)
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
        tap(*DPAD[d])          # 暂停中：只缓存转向
        last_dir = d
        # 恢复 → 只放行一格（自校正：确认真的走了一格，否则重试）
        for _ in range(5):
            tap(*PAUSE)
            time.sleep(0.25)
            tap(*PAUSE)
            time.sleep(0.2)
            img2 = screenshot()
            head2 = head_cell(img2, board_box(img2))
            if head2 and (abs(head2[0] - hr) + abs(head2[1] - hc)) >= 1:
                break
        print(f"[{step}] 头({hr},{hc}) 食({fr},{fc}) → {d}")
    print("❌ 60 步内未吃到")
    return 1


if __name__ == "__main__":
    sys.exit(main())
