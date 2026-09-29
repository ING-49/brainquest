"""华容道「将拥曹营」BFS 求解回放：驱动 UI 走最少步解，实测曹操滑出门洞的出场动画。

用法：python tools/_klotski_solve_replay.py
"""
import os
import sys
import time
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

SHOTS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools", "_shots_v1614")
COLS, ROWS, EXIT = 4, 5, (3, 1)

START = [
    ("曹操", 1, 1, 2, 2), ("张飞", 0, 1, 1, 1), ("赵云", 0, 2, 1, 1),
    ("马超", 0, 0, 1, 2), ("黄忠", 0, 3, 1, 2), ("关羽", 3, 1, 2, 1),
    ("卒", 3, 0, 1, 1), ("卒", 3, 3, 1, 1), ("卒", 4, 1, 1, 1), ("卒", 4, 2, 1, 1),
]


def state_of(defs):
    return [list(b) for b in defs]   # [name, r, c, w, h]


def free(blocks, ignore, r, c, w, h):
    if r < 0 or c < 0 or r + h > ROWS or c + w > COLS:
        return False
    for k, o in enumerate(blocks):
        if k == ignore:
            continue
        # o = [name, r, c, w, h]：行方向比 h(o[4])，列方向比 w(o[3])
        if r < o[1] + o[4] and o[1] < r + h and c < o[2] + o[3] and o[2] < c + w:
            return False
    return True


def solve_path():
    """BFS + 父指针回溯，返回 [(block_index, dr, dc), ...] 最短解"""
    start = state_of(START)
    if start[0][1] == EXIT[0] and start[0][2] == EXIT[1]:
        return []
    seen = {(tuple(map(tuple, start))): None}
    q = deque([start])
    while q:
        cur = q.popleft()
        for i in range(len(cur)):
            n, r, c, w, h = cur[i]
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if not free(cur, i, r + dr, c + dc, w, h):
                    continue
                nxt = [list(x) for x in cur]
                nxt[i][1] += dr
                nxt[i][2] += dc
                key = tuple(map(tuple, nxt))
                if key in seen:
                    continue
                seen[key] = (tuple(map(tuple, cur)), i, dr, dc)
                if nxt[0][1] == EXIT[0] and nxt[0][2] == EXIT[1]:
                    path = []
                    k = (key, i, dr, dc)
                    # 回溯：seen[key] 记录 (prevKey, i, dr, dc)
                    prev, mi, mdr, mdc = seen[key]
                    path.append((mi, mdr, mdc))
                    while seen[prev] is not None:
                        prev2, pi, pdr, pdc = seen[prev]
                        path.append((pi, pdr, pdc))
                        prev = prev2
                    return list(reversed(path))
                q.append(nxt)
    return None


def main():
    path = solve_path()
    if path is None:
        print("❌ BFS 无解？")
        return 1
    print(f"[BFS] 将拥曹营 最少 {len(path)} 步")

    ui.start()
    if not ui.tap_text("大厅", exact=True, wait=5):
        print("❌ 未找到大厅"); return 1
    for _ in range(3):
        ui.swipe(540, 1600, 540, 700, ms=250, delay=0.4)
    if not ui.tap_text("华容道", exact=False, wait=5):
        print("❌ 未找到华容道"); return 1
    if not ui.tap_text("将拥曹营", exact=False, wait=4):
        print("❌ 未找到将拥曹营"); return 1
    time.sleep(1.0)

    # 棋盘几何（1080x2400）：左上角 (48, 576)，单格 246x245
    bx, by, cw, ch = 48, 576, 246, 245
    blocks = state_of(START)
    for n, (i, dr, dc) in enumerate(path):
        name, r, c, w, h = blocks[i]
        cx = bx + cw * (c + w / 2)
        cy = by + ch * (r + h / 2)
        ui.tap(int(cx), int(cy), delay=0.25)                      # 选中
        ui.swipe(int(cx), int(cy), int(cx + dc * cw), int(cy + dr * ch), ms=220, delay=0.22)
        blocks[i][1] += dr
        blocks[i][2] += dc
        if (n + 1) % 8 == 0:
            print(f"    ...{n + 1}/{len(path)} 步")

    # 最后一步后：曹操滑出门洞动画（~650ms），抓中间帧
    time.sleep(0.9)
    ui.shot(os.path.join(SHOTS, "12_exit_anim_mid.png"))
    time.sleep(1.6)
    ui.shot(os.path.join(SHOTS, "13_win_dialog.png"))
    print("texts@final:", [x for x in ui.texts() if x][:10])
    return 0


if __name__ == "__main__":
    sys.exit(main())
