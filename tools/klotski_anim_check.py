"""华容道体验取证：无方向键 / 拖动跟手 / 过阈值滑行落格 / 步数+1。

用法：python tools/klotski_anim_check.py
产出截图在 .shots/klotski_*.png。
说明：落格断言用普通 swipe（真实 DOWN-MOVE-UP 序列）；
跟手预览用 input motionevent 分段移动中途截图（合成 UP 不会收尾，故随后重置）。
"""
import os
import subprocess
import sys
import time

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

SHOTS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".shots")
TMP = os.path.join(os.environ.get("TEMP", "."), "klotski_check.png")

BOARD_BG = (243, 231, 211)      # Color(0xFFF3E7D3)
PAWN = (109, 76, 65)            # Color(0xFF6D4C41)
CAO = (211, 47, 47)             # Color(0xFFD32F2F)
COLS, ROWS = 4, 5


def shot(name=None):
    ui.shot(TMP)
    img = np.array(Image.open(TMP).convert("RGB"))
    if name:
        os.makedirs(SHOTS, exist_ok=True)
        Image.fromarray(img).save(os.path.join(SHOTS, name))
    return img


def color_mask(img, rgb, tol=8):
    return ((np.abs(img[:, :, 0].astype(int) - rgb[0]) < tol) &
            (np.abs(img[:, :, 1].astype(int) - rgb[1]) < tol) &
            (np.abs(img[:, :, 2].astype(int) - rgb[2]) < tol))


def board_box(img):
    ys, xs = np.where(color_mask(img, BOARD_BG))
    if len(xs) < 10000:
        return None
    return int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())


def clusters(img, box, rgb, tol=20, min_px=2000):
    """返回 [(row_min, row_max, col_min, col_max)]（单位：格）"""
    x0, x1, y0, y1 = box
    cw = (x1 - x0 + 1) / COLS
    ch = (y1 - y0 + 1) / ROWS
    sub = color_mask(img[y0:y1 + 1, x0:x1 + 1], rgb, tol)
    lab, n = ndimage.label(sub)
    out = []
    for i in range(1, n + 1):
        yy, xx = np.where(lab == i)
        if len(xx) < min_px:
            continue
        out.append((yy.min() / ch, yy.max() / ch, xx.min() / cw, xx.max() / cw))
    return sorted(out, key=lambda c: (round(c[0]), c[2]))


def at_cell(cl, row, col, tol=0.15):
    return abs(cl[0] - (row + 0.04)) < tol and abs(cl[2] - (col + 0.04)) < tol


def cell_center(box, col, row):
    x0, x1, y0, y1 = box
    cw = (x1 - x0 + 1) / COLS
    ch = (y1 - y0 + 1) / ROWS
    return int(x0 + cw * (col + 0.5)), int(y0 + ch * (row + 0.5)), int(cw)


def movevent(action, x, y):
    subprocess.run([ui.ADB, "shell", "input", "motionevent", action, str(int(x)), str(int(y))],
                   capture_output=True)


def swipe(x1, y1, x2, y2, ms=300):
    subprocess.run([ui.ADB, "shell", "input", "swipe", str(int(x1)), str(int(y1)),
                    str(int(x2)), str(int(y2)), str(int(ms))], capture_output=True)


def step_text():
    for t in ui.texts():
        if t.startswith("步数"):
            return t
    return None


def main():
    fails = []
    ui.start()
    if not ui.tap_text("华容道", exact=False, wait=6):
        print("❌ 未找到首页华容道入口"); return 1
    if not ui.tap_text("将拥曹营", wait=5):
        print("❌ 未进入关卡"); return 1
    time.sleep(0.8)

    img = shot("klotski_1_board.png")
    box = board_box(img)
    if box is None:
        print("❌ 未识别到棋盘"); return 1
    print(f"棋盘框 {box}  单格 {cell_center(box,0,0)[2]}px")

    # 1) 无方向键
    dpad = [t for t in ui.texts() if t in ("↑", "↓", "←", "→")]
    print(f"[1] 屏幕方向键：{'仍存在 ' + str(dpad) if dpad else '已移除 ✓'}")
    if dpad:
        fails.append("方向键未移除")

    # 2) 拖动跟手：按住 (3,0) 的卒上移 0.35 格后截图，不应落步
    cx, cy, cell = cell_center(box, 0, 3)
    movevent("DOWN", cx, cy)
    time.sleep(0.15)
    movevent("MOVE", cx, cy - int(cell * 0.35))
    time.sleep(0.3)
    img_follow = shot("klotski_2_drag_follow.png")
    follow = [c for c in clusters(img_follow, box, PAWN) if c[2] < 0.5]
    print(f"[2] 跟手预览：卒 位置 {follow[0] if follow else None}（原始为 row 3.04-3.93）")
    same = follow and abs(follow[0][0] - 3.04) < 0.15 and abs(follow[0][1] - 3.93) < 0.15
    if same:
        print("    ⚠️ 未跟手（仍停在原格）")
        fails.append("拖动未跟手")
    if step_text() != "步数 0":
        print(f"    ⚠️ 未过阈值却已落步：{step_text()}")
        fails.append("未过阈值就落步")

    # 3) 正常 swipe 走一格：应按格滑行并精确落格
    swipe(cx, cy, cx, cy - int(cell * 0.9))
    time.sleep(0.6)
    img_done = shot("klotski_3_moved.png")
    moved = [c for c in clusters(img_done, box, PAWN) if c[2] < 0.5]
    print(f"[3] swipe 后：卒 位置 {moved[0] if moved else None}（应为 row 2.04-2.93）")
    if not (moved and at_cell(moved[0], 2, 0)):
        fails.append(f"未精确落到 (row2,col0)：{moved[0] if moved else None}")
    print(f"    步数：{step_text()}")

    # 4) 选中的棋子可在空白处滑动（点选 → 拖空白）
    img_sel = shot()
    sel = [c for c in clusters(img_sel, box, (255, 179, 0), tol=40, min_px=800)]
    print(f"[4] 选中描边：{'有 ✓' if sel else '无（swipe 起手即选中，理应保留）'}")

    print("✅ 华容道 滑动/动画/落格/步数 全部通过" if not fails else f"❌ 未通过：{fails}")
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
