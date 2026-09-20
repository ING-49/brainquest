"""五子棋悔棋语义取证：悔棋 = 回到我落子之前（电脑应的那手一并撤销），且电脑不会自动补一手。

用法：python tools/gomoku_undo_check.py
产出截图 .shots/gomoku_*.png
"""
import os
import subprocess
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

SHOTS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".shots")
TMP = os.path.join(os.environ.get("TEMP", "."), "gomoku_check.png")
SIZE = 15
BOARD_BG = (240, 220, 184)   # Color(0xFFF0DCB8)
BLACK = (33, 33, 33)         # Color(0xFF212121)
WHITE = (253, 253, 253)      # Color(0xFFFDFDFD)


def shot(name=None):
    ui.shot(TMP)
    img = np.array(Image.open(TMP).convert("RGB"))
    if name:
        os.makedirs(SHOTS, exist_ok=True)
        Image.fromarray(img).save(os.path.join(SHOTS, name))
    return img


def mask(img, rgb, tol):
    return ((np.abs(img[:, :, 0].astype(int) - rgb[0]) < tol) &
            (np.abs(img[:, :, 1].astype(int) - rgb[1]) < tol) &
            (np.abs(img[:, :, 2].astype(int) - rgb[2]) < tol))


def board_box(img):
    ys, xs = np.where(mask(img, BOARD_BG, 10))
    if len(xs) < 10000:
        return None
    return int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())


def stone_cells(img, box, rgb, tol):
    """返回落子格坐标集合"""
    x0, x1, y0, y1 = box
    cw = (x1 - x0 + 1) / SIZE
    ch = (y1 - y0 + 1) / SIZE
    sub = mask(img[y0:y1 + 1, x0:x1 + 1], rgb, tol)
    out = set()
    for r in range(SIZE):
        for c in range(SIZE):
            block = sub[int(r * ch):int((r + 1) * ch), int(c * cw):int((c + 1) * cw)]
            if (block.sum() > 400):
                out.add((r, c))
    return out


def cell_center(box, r, c):
    x0, x1, y0, y1 = box
    cw = (x1 - x0 + 1) / SIZE
    ch = (y1 - y0 + 1) / SIZE
    return int(x0 + cw * (c + 0.5)), int(y0 + ch * (r + 0.5))


def status():
    for t in ui.texts():
        if t in ("轮到你", "电脑思考中…", "本局结束"):
            return t
    return None


def main():
    fails = []
    ui.start()
    if not ui.tap_text("五子棋", exact=False, wait=6):
        print("❌ 未找到五子棋入口"); return 1
    time.sleep(0.6)

    img = shot("gomoku_1_board.png")
    box = board_box(img)
    if box is None:
        print("❌ 未识别到棋盘"); return 1

    # 预先解析按钮坐标（dump 慢，落子后要立刻点悔棋）
    undo_btn = ui.find("↩️ 悔棋")
    if undo_btn is None:
        print("❌ 未找到悔棋按钮"); return 1
    ux, uy = ui.center(undo_btn)
    print(f"棋盘 {box}  悔棋按钮 {ux},{uy}")

    def counts():
        im = shot()
        return stone_cells(im, box, BLACK, 26), stone_cells(im, box, WHITE, 6)

    # ---- 用例 1：正常应手后悔棋，应同时撤掉我的一手与电脑的应手 ----
    ui.tap(*cell_center(box, 7, 7), delay=0.15)
    time.sleep(0.25)
    b1, w1 = counts()
    print(f"[1] 我落子后：黑 {sorted(b1)} 白 {sorted(w1)}  状态 {status()}")
    time.sleep(1.0)
    b2, w2 = counts()
    print(f"[1] 电脑应手后：黑 {len(b2)} 颗 白 {len(w2)} 颗 {sorted(w2)}  状态 {status()}")
    if len(b1) != 1 or len(w2) != 1:
        fails.append("落子/AI 应手未按预期发生")

    ui.tap(ux, uy, delay=0.6)
    img_after = shot("gomoku_2_after_undo.png")
    b3 = stone_cells(img_after, box, BLACK, 26)
    w3 = stone_cells(img_after, box, WHITE, 6)
    print(f"[1] 悔棋后：黑 {sorted(b3)} 白 {sorted(w3)}  状态 {status()}")
    if b3 or w3:
        fails.append(f"悔棋后棋盘未回到落子前（黑 {sorted(b3)} 白 {sorted(w3)}）")
    if status() != "轮到你":
        fails.append(f"悔棋后不是轮到玩家（{status()}）")

    # 关键：电脑不应自动补一手
    time.sleep(1.6)
    b4, w4 = counts()
    print(f"[1] 悔棋后等待 1.6s：黑 {len(b4)} 颗 白 {len(w4)} 颗  状态 {status()}")
    if b4 or w4:
        fails.append("悔棋后电脑自动补了一手（bug 复现）")

    # ---- 用例 2：电脑思考中（350ms 内）悔棋，应取消这次落子 ----
    ui.tap(*cell_center(box, 7, 7), delay=0.05)
    ui.tap(ux, uy, delay=0.1)
    time.sleep(1.6)
    img2 = shot("gomoku_3_undo_while_thinking.png")
    b5 = stone_cells(img2, box, BLACK, 26)
    w5 = stone_cells(img2, box, WHITE, 6)
    print(f"[2] 思考期悔棋后（等 1.6s）：黑 {sorted(b5)} 白 {sorted(w5)}  状态 {status()}")
    if b5 or w5:
        fails.append("思考期悔棋未能取消电脑落子")

    # ---- 用例 3：对局中（棋盘上有子）按返回键应弹确认框 ----
    ui.tap(*cell_center(box, 7, 7), delay=0.4)
    ui.key("BACK", delay=0.8)
    labels = ui.texts()
    has_dialog = any("退出这一局" in t for t in labels)
    print(f"[3] 返回键确认框：{'出现 ✓' if has_dialog else '未出现'}")
    if not has_dialog:
        fails.append("对局中返回未弹确认框")
    else:
        ui.tap_text("继续下", delay=0.5)
        kept = stone_cells(shot("gomoku_4_back_continue.png"), box, BLACK, 26)
        print(f"    继续下后保留棋局：{sorted(kept)}")
        if not kept:
            fails.append("继续下后棋局丢失")

    print("✅ 五子棋悔棋语义与返回确认 全部通过" if not fails else f"❌ 未通过：{fails}")
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
