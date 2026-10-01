# -*- coding: utf-8 -*-
"""地牢幸存者阶段2验收 bot v5：读游戏日志（真实坐标 + 房间图）做航点导航。

不靠截图猜：游戏自己上报 px/py/房间/敌人状态，bot 算最短路、逐门推进、
进未清房就站桩让自动攻击打，升级自动选卡。截图只用于留档。
"""
import sys, time, re, subprocess
sys.path.insert(0, r"E:\PROJECT_CCC\ZCode_project\APP-learn\tools")
import ui

ADB = r"E:/Tools/Android-Studio/Android/SDK/platform-tools/adb.exe"
S = r"E:\PROJECT_CCC\ZCode_project\APP-learn\tools\_shots_dungeon1"

ROOM_W, ROOM_H, GRID_X, GRID_Y = 1000, 640, 1400, 960

def log_tail():
    r = subprocess.run([ADB, "logcat", "-d", "-s", "DGBG"], capture_output=True)
    return r.stdout.decode("utf-8", "ignore")

def game_state():
    """返回 (px, py, room, locked, enemies, rooms_map)；rooms_map[floor][(gx,gy)] = type"""
    txt = log_tail()
    px = py = None; room = None; locked = False; enemies = 0
    for m in re.finditer(r"DGBG.*: px=(-?\d+) py=(-?\d+) room=(-?\d+),(-?\d+) locked=(\w+) enemies=(\d+)", txt):
        px, py = int(m.group(1)), int(m.group(2))
        room = (int(m.group(3)), int(m.group(4)))
        locked = m.group(5) == "true"
        enemies = int(m.group(6))
    rooms = {}
    for m in re.finditer(r"DGMAP floor=(\d+) start=(-?\d+),(-?\d+) rooms=(.*)", txt):
        fl = int(m.group(1))
        rooms[fl] = {"start": (int(m.group(2)), int(m.group(3)))}
        for part in m.group(4).split(";"):
            gx, gy, t = part.split(",")
            rooms[fl][(int(gx), int(gy))] = t
    return px, py, room, locked, enemies, rooms

def move(dx, dy, ms=480):
    subprocess.run([ADB, "shell", "input", "swipe", "460", "1200",
                    str(int(460 + dx)), str(int(1200 + dy)), str(ms)], capture_output=True)

def tap_upgrade():
    for name in ("攻击力", "攻速", "移速", "生命", "拾取", "元素"):
        if ui.tap_text(name, exact=False, wait=0.3):
            return True
    return False

def neighbors(g, rmap):
    out = []
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (g[0] + dx, g[1] + dy)
        if n in rmap: out.append(n)
    return out

def bfs_path(src, dst, rmap):
    from collections import deque
    q = deque([src]); prev = {src: None}
    while q:
        cur = q.popleft()
        if cur == dst: break
        for n in neighbors(cur, rmap):
            if n not in prev:
                prev[n] = cur; q.append(n)
    if dst not in prev: return None
    path = []
    while dst != src:
        path.append(dst); dst = prev[dst]
    return list(reversed(path))

def door_waypoint(a, b):
    return ((a[0] + b[0]) / 2 * GRID_X, (a[1] + b[1]) / 2 * GRID_Y)

def room_center(g):
    return (g[0] * GRID_X, g[1] * GRID_Y)

def steer(tx, ty, px, py, scale=300):
    dx, dy = tx - px, ty - py
    d = max((dx * dx + dy * dy) ** 0.5, 1e-3)
    move(dx / d * scale, dy / d * scale)

# ---------- 开始一局 ----------
ui.start()
ui.tap_text("大厅", exact=True, wait=6)
for _ in range(5):
    if ui.find("地牢幸存者", exact=False): break
    ui.swipe(540, 1700, 540, 900, ms=250, delay=0.5)
ui.tap_text("地牢幸存者", exact=False, wait=5)
time.sleep(1.0)
subprocess.run([ADB, "logcat", "-c"], capture_output=True)
ui.tap_text("进入地牢", exact=True, wait=4)
time.sleep(0.8)
ui.tap_text("🏹 游侠", exact=False, wait=4)
time.sleep(2.0)

t0 = time.time()
result = ""
target_room = None
last_shot = 0
visited_combat = set()
while time.time() - t0 < 900:
    t = [x for x in ui.texts() if x]
    if any("升级" in x for x in t):
        tap_upgrade(); time.sleep(0.5); continue
    if any("地牢通关" in x for x in t):
        result = "VICTORY"; ui.shot(S + r"\11_victory.png"); break
    if any("倒在了地牢" in x for x in t):
        result = "DEAD"; ui.shot(S + r"\11_dead.png"); break

    px, py, room, locked, enemies, rooms_map = game_state()
    if px is None or not rooms_map:
        time.sleep(0.5); continue
    floor = max(rooms_map.keys())
    rmap = rooms_map[floor]
    hudline = next((x for x in t if "房间" in x), "")

    if locked:
        # 战斗房锁门中：站桩清怪（含等延迟刷怪落地）
        time.sleep(0.8)
        continue

    # 自由通行：选目标——最近的未去过的战斗房（战斗/精英/Boss），都去过则随机走相邻房拾球
    pending = [g for g, ty in rmap.items()
               if ty in ("BATTLE", "ELITE", "BOSS") and g not in visited_combat and g != room]
    if not pending:
        pending = [g for g in rmap if g != room and g not in visited_combat]
    if not pending:
        visited_combat.clear()   # 全图走完一轮：重置，继续扫球
        continue
    pending.sort(key=lambda g: abs(g[0] - room[0]) + abs(g[1] - room[1]))
    target_room = pending[0]

    path = bfs_path(room, target_room, rmap)
    if not path:
        visited_combat.add(target_room); continue
    nxt = path[0]
    wp = door_waypoint(room, nxt)
    d = ((wp[0] - px) ** 2 + (wp[1] - py) ** 2) ** 0.5
    if d > 200:
        # 远离门：先对准门口航点
        steer(wp[0], wp[1], px, py)
        time.sleep(0.2)
    else:
        # 进入门区（200px > 单次位移 ~118px）：直接朝邻房中心穿门，避免在航点两侧振荡
        rc = room_center(nxt)
        steer(rc[0], rc[1], px, py)
        time.sleep(0.2)
        # 进房：若是战斗房且当前房已变成它，记为已访问（清完或清中都会推进）
        if room == nxt:
            visited_combat.add(nxt)

    if time.time() - last_shot > 10:
        ui.shot(S + r"\15_run.png")
        last_shot = time.time()

print("final:", result or hudline or "n/a")
ui.shot(S + r"\12_final.png")
