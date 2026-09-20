"""校验华容道关卡：合法性（不重叠/在界内）+ 可解性（BFS 求最少步数）。

用法：python tools/klotski_verify.py
"""
from collections import deque

COLS, ROWS = 4, 5
CAO, EXIT = 0, (3, 1)  # 曹操编号 0；出口左上角坐标


def B(name, row, col, w, h):
    return (name, row, col, w, h)


def make_blocks(defs):
    return [{"name": n, "r": r, "c": c, "w": w, "h": h} for (n, r, c, w, h) in defs]


def valid(blocks):
    for b in blocks:
        if b["r"] < 0 or b["c"] < 0 or b["r"] + b["h"] > ROWS or b["c"] + b["w"] > COLS:
            return False, f"越界: {b}"
    for i in range(len(blocks)):
        for j in range(i + 1, len(blocks)):
            a, o = blocks[i], blocks[j]
            if (a["r"] < o["r"] + o["h"] and o["r"] < a["r"] + a["h"] and
                    a["c"] < o["c"] + o["w"] and o["c"] < a["c"] + a["w"]):
                return False, f"重叠: {a['name']} 与 {o['name']}"
    return True, "ok"


def free(blocks, ignore, r, c, w, h):
    if r < 0 or c < 0 or r + h > ROWS or c + w > COLS:
        return False
    for k, o in enumerate(blocks):
        if k == ignore:
            continue
        if r < o["r"] + o["h"] and o["r"] < r + h and c < o["c"] + o["w"] and o["c"] < c + w:
            return False
    return True


def key(blocks):
    """归一化状态编码：同形状棋子视为可互换（否则带标签状态空间爆炸）
    曹操单独一类，其余按 (w,h) 分组后组内坐标排序。"""
    cao = None
    groups = {}
    for b in blocks:
        if b["name"] == "曹操":
            cao = (b["r"], b["c"])
        else:
            groups.setdefault((b["w"], b["h"]), []).append((b["r"], b["c"]))
    return (cao, tuple(sorted((wh, tuple(sorted(pos))) for wh, pos in groups.items())))


def solve(blocks):
    """BFS 最短步数；不可解返回 None"""
    start = make_blocks([(b["name"], b["r"], b["c"], b["w"], b["h"]) for b in blocks])
    if start[CAO]["r"] == EXIT[0] and start[CAO]["c"] == EXIT[1]:
        return 0
    seen = {key(start): 0}
    q = deque([(start, 0)])
    while q:
        cur, d = q.popleft()
        for i in range(len(cur)):
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                b = cur[i]
                if not free(cur, i, b["r"] + dr, b["c"] + dc, b["w"], b["h"]):
                    continue
                nxt = [dict(x) for x in cur]
                nxt[i]["r"] += dr
                nxt[i]["c"] += dc
                k = key(nxt)
                if k in seen:
                    continue
                nd = d + 1
                if nxt[CAO]["r"] == EXIT[0] and nxt[CAO]["c"] == EXIT[1]:
                    return nd
                seen[k] = nd
                q.append((nxt, nd))
    return None


LEVELS = {
    "横刀立马": [B("曹操", 0, 1, 2, 2), B("张飞", 0, 0, 1, 2), B("赵云", 0, 3, 1, 2),
             B("马超", 2, 0, 1, 2), B("黄忠", 2, 3, 1, 2), B("关羽", 2, 1, 2, 1),
             B("卒", 3, 1, 1, 1), B("卒", 3, 2, 1, 1), B("卒", 4, 0, 1, 1), B("卒", 4, 3, 1, 1)],
    "指挥若定": [B("曹操", 0, 1, 2, 2), B("张飞", 0, 0, 1, 2), B("赵云", 0, 3, 1, 2),
             B("马超", 3, 0, 1, 2), B("黄忠", 3, 3, 1, 2), B("关羽", 2, 1, 2, 1),
             B("卒", 2, 0, 1, 1), B("卒", 2, 3, 1, 1), B("卒", 3, 1, 1, 1), B("卒", 3, 2, 1, 1)],
    "将拥曹营": [B("曹操", 1, 1, 2, 2), B("张飞", 0, 1, 1, 1), B("赵云", 0, 2, 1, 1),
             B("马超", 0, 0, 1, 2), B("黄忠", 0, 3, 1, 2), B("关羽", 3, 1, 2, 1),
             B("卒", 3, 0, 1, 1), B("卒", 3, 3, 1, 1), B("卒", 4, 1, 1, 1), B("卒", 4, 2, 1, 1)],
    "齐头并进": [B("曹操", 0, 1, 2, 2), B("张飞", 0, 0, 1, 2), B("赵云", 0, 3, 1, 2),
             B("关羽", 2, 0, 2, 1), B("马超", 2, 2, 1, 2), B("黄忠", 3, 3, 1, 1),
             B("卒", 3, 0, 1, 1), B("卒", 3, 1, 1, 1), B("卒", 4, 0, 1, 1), B("卒", 4, 3, 1, 1)],
    "兵分三路": [B("曹操", 0, 1, 2, 2), B("张飞", 0, 0, 1, 2), B("赵云", 0, 3, 1, 2),
             B("马超", 2, 0, 1, 2), B("黄忠", 2, 3, 1, 2), B("关羽", 3, 1, 2, 1),
             B("卒", 4, 0, 1, 1), B("卒", 4, 1, 1, 1), B("卒", 4, 2, 1, 1), B("卒", 4, 3, 1, 1)],
    "层层设防": [B("曹操", 0, 1, 2, 2), B("张飞", 0, 0, 1, 2), B("赵云", 0, 3, 1, 2),
             B("关羽", 2, 1, 2, 1), B("马超", 3, 0, 1, 2), B("黄忠", 3, 3, 1, 2),
             B("卒", 2, 0, 1, 1), B("卒", 2, 3, 1, 1), B("卒", 3, 1, 1, 1), B("卒", 3, 2, 1, 1)],
}

if __name__ == "__main__":
    fails = []
    for name, defs in LEVELS.items():
        blocks = make_blocks(defs)
        ok, why = valid(blocks)
        if not ok:
            print(f"[FAIL] {name}: {why}")
            fails.append(name)
            continue
        n = solve(blocks)
        if n is None:
            print(f"[FAIL] {name}: 不可解（BFS 穷尽）")
            fails.append(name)
        else:
            print(f"[PASS] {name}: 最少 {n} 步")
    print("\n结果:", "全部通过" if not fails else f"需修正 {fails}")
