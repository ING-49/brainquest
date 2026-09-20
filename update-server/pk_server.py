"""
脑力大冒险 · 联机对战服务器（好友码房间制 + 快速匹配 + ELO + 云存档，WebSocket）

协议（JSON）：
  客户端 → 服务器：
    {"t":"create","name":"昵称"}                    → 创建房间（不计分）
    {"t":"join","code":"123456","name":"昵称"}       → 加入房间（不计分）
    {"t":"quick_match","name":"昵称","subject":"混合"} → 进入快速匹配队列（同版本才配对，计分；科目=房主选定）
    {"t":"cancel_match"}                            → 退出匹配队列
    {"t":"online"}                                  → 查询在线人数
    {"t":"leaderboard","name":"昵称","subject":"混合"} → 查询某科目排行榜（Top10 + 我的排名）
    {"t":"save_put","code":"XXXX","data":"<json>"}  → 上传云存档
    {"t":"save_get","code":"XXXX"}                  → 下载云存档
    {"t":"start","questions":[...]}                 （房主）开始并下发题目
    {"t":"answer","idx":0,"correct":true,"timeMs":1234}
    {"t":"finish","correct":3,"timeMs":9876}
    {"t":"chat","text":"..."}                       （预留）
  服务器 → 客户端：
    {"t":"created","code":"123456"}
    {"t":"joined","code":"123456","peer":"对方昵称"}
    {"t":"peer_joined","peer":"对方昵称"}
    {"t":"online","players":N,"waiting":K,"rooms":M}   （广播：在线/等待匹配/房间数）
    {"t":"leaderboard","top":[{name,rating,wins,losses,games}],"me":{...rank}|null}
    {"t":"save_ok","size":N} / {"t":"save_data","data":"...","ts":...}
    {"t":"start","questions":[...]}                 （转发给加入方）
    {"t":"question","idx":0,"q":{...}}              （房主逐题下发时转发）
    {"t":"peer_answer","idx":0,"correct":true}
    {"t":"peer_finish","correct":3,"timeMs":9876}
    {"t":"result","outcome":"win|lose|draw","my":{...},"peer":{...},
                   "rating":{"ranked":true,"my":1032,"delta":+18,"peer":976}}   计分局附积分
    {"t":"peer_left"}
    {"t":"error","msg":"..."}

快速匹配配对成功后：甲方（先入队）收到 created+peer_joined（走房主路径），
乙方收到 joined（走加入方路径）——配对房间标记 ranked=true（仅快速匹配计分）。

用法：python pk_server.py [端口，默认 8765]
数据目录：环境变量 PK_DATA_DIR，默认 /opt/pk（不存在则用脚本所在目录）
"""
import asyncio
import json
import os
import random
import re
import sys

import websockets

DATA_DIR = os.environ.get("PK_DATA_DIR") or ("/opt/pk" if os.path.isdir("/opt/pk") else os.path.dirname(os.path.abspath(__file__)))
RATINGS_FILE = os.path.join(DATA_DIR, "ratings.json")
SAVES_DIR = os.path.join(DATA_DIR, "saves")

K_FACTOR = 32          # ELO K 值
DEFAULT_RATING = 1000  # 初始积分
DEFAULT_SUBJECT = "混合"  # 排行榜默认科目（房主未选科目时）

rooms = {}   # code -> {"host": ws, "guest": ws, "names": {ws: name}, "versions": {ws: version}, "finish": {ws: (correct, timeMs)}, "ranked": bool, "subject": str}
online = set()   # 当前所有连接
queue = []   # 快速匹配等待队列：[{"ws","name","version","subject"}, ...]
ratings = {}  # 科目 -> {name -> {"r": 1000, "w": 0, "l": 0, "g": 0}}（排行榜按科目分桶）


# ---------- 积分持久化（快速匹配 ELO，按科目分桶，仅计分局更新） ----------

def load_ratings():
    global ratings
    try:
        with open(RATINGS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        # 兼容 v1.6.3 的旧扁平结构（name -> 积分）→ 迁移进「混合」桶
        if data and all(isinstance(v, dict) and "r" in v for v in data.values()):
            data = {DEFAULT_SUBJECT: data}
        ratings = data
        n = sum(len(v) for v in ratings.values())
        print(f"[elo] 已载入 {len(ratings)} 个科目桶 / {n} 名玩家积分")
    except Exception:
        ratings = {}


def save_ratings():
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(RATINGS_FILE, "w", encoding="utf-8") as f:
            json.dump(ratings, f, ensure_ascii=False)
    except Exception as e:
        print(f"[elo] 积分保存失败: {e}")


def rating_of(subject, name):
    return ratings.setdefault(subject, {}).setdefault(
        name, {"r": DEFAULT_RATING, "w": 0, "l": 0, "g": 0})


def apply_elo(subject, host_name, guest_name, host_score):
    """host_score: 1 胜 / 0.5 平 / 0 负；返回 (host_delta, host_rating, guest_rating)"""
    h, g = rating_of(subject, host_name), rating_of(subject, guest_name)
    exp_h = 1 / (1 + 10 ** ((g["r"] - h["r"]) / 400))
    delta = round(K_FACTOR * (host_score - exp_h))
    h["r"] += delta
    g["r"] -= delta
    h["g"] += 1
    g["g"] += 1
    if host_score == 1:
        h["w"] += 1; g["l"] += 1
    elif host_score == 0:
        h["l"] += 1; g["w"] += 1
    save_ratings()
    return delta, h["r"], g["r"]


def rank_of(subject, name):
    order = sorted(ratings.get(subject, {}).items(), key=lambda kv: -kv[1]["r"])
    for i, (n, _) in enumerate(order):
        if n == name:
            return i + 1
    return 0


# ---------- 基础工具 ----------

def send(ws, obj):
    async def _do():
        try:
            await ws.send(json.dumps(obj, ensure_ascii=False))
        except Exception:
            pass
    asyncio.ensure_future(_do())


def presence():
    return {"t": "online", "players": len(online),
            "waiting": len(queue), "rooms": len(rooms)}


def broadcast_presence():
    snap = presence()
    for ws in list(online):
        send(ws, snap)


def new_code():
    c = f"{random.randint(0, 999999):06d}"
    while c in rooms:
        c = f"{random.randint(0, 999999):06d}"
    return c


def enqueue(ws, name, version, subject):
    if any(e["ws"] == ws for e in queue):
        return
    queue.append({"ws": ws, "name": name, "version": version, "subject": subject})
    print(f"[match] {name} v{version} 科目[{subject}] 入队（等待 {len(queue)}）")


def dequeue(ws):
    before = len(queue)
    queue[:] = [e for e in queue if e["ws"] != ws]
    return len(queue) != before


def try_match():
    """队列里找版本号相同的两人配对建房；成功返回 True"""
    for i in range(len(queue)):
        for j in range(i + 1, len(queue)):
            a, b = queue[i], queue[j]
            if a["version"] == b["version"]:
                queue.pop(j)
                queue.pop(i)
                pair(a, b)
                return True
    return False


def pair(a, b):
    code = new_code()
    rooms[code] = {"host": a["ws"], "guest": b["ws"],
                   "names": {a["ws"]: a["name"], b["ws"]: b["name"]},
                   "versions": {a["ws"]: a["version"], b["ws"]: b["version"]},
                   "finish": {}, "ranked": True,          # 快速匹配 = 计分局
                   "subject": a.get("subject") or DEFAULT_SUBJECT}  # 房主选定的科目 = 计分桶
    # 甲方走房主路径：created → peer_joined（收到后本地选题并发 start）
    send(a["ws"], {"t": "created", "code": code})
    send(a["ws"], {"t": "peer_joined", "peer": b["name"], "version": b["version"]})
    # 乙方走加入方路径
    send(b["ws"], {"t": "joined", "code": code, "peer": a["name"],
                   "peer_version": a["version"]})
    print(f"[match] {code} 配对成功: {a['name']} vs {b['name']}（计分 · 科目[{rooms[code]['subject']}]）")


def peer_of(code, ws):
    room = rooms.get(code)
    if not room:
        return None
    return room["guest"] if room["host"] == ws else room["host"]


def try_result(code):
    room = rooms.get(code)
    if not room or len(room["finish"]) < 2:
        return
    host, guest = room["host"], room["guest"]
    (hc, ht), (gc, gt) = room["finish"][host], room["finish"][guest]
    # 判定（先算双方 outcome，再按需结算 ELO）
    if hc != gc:
        host_score = 1.0 if hc > gc else 0.0
    elif ht != gt:
        host_score = 1.0 if ht < gt else 0.0
    else:
        host_score = 0.5
    rating = None
    if room.get("ranked"):
        h_name = room["names"].get(host, "玩家")
        g_name = room["names"].get(guest, "玩家")
        subject = room.get("subject") or DEFAULT_SUBJECT
        delta, h_r, g_r = apply_elo(subject, h_name, g_name, host_score)
        rating = {
            host: {"ranked": True, "my": h_r, "delta": delta, "peer": g_r},
            guest: {"ranked": True, "my": g_r, "delta": -delta, "peer": h_r},
        }
        print(f"[elo] [{subject}] {h_name}({h_r}) vs {g_name}({g_r}) delta={delta:+d}")
    for ws, my, other in (
        (host, (hc, ht), (gc, gt)),
        (guest, (gc, gt), (hc, ht)),
    ):
        if my[0] != other[0]:
            outcome = "win" if my[0] > other[0] else "lose"
        elif my[1] != other[1]:
            outcome = "win" if my[1] < other[1] else "lose"
        else:
            outcome = "draw"
        msg = {"t": "result", "outcome": outcome,
               "my": {"correct": my[0], "timeMs": my[1]},
               "peer": {"correct": other[0], "timeMs": other[1]}}
        if rating:
            msg["rating"] = rating[ws]
        else:
            msg["rating"] = {"ranked": False}
        send(ws, msg)
    rooms.pop(code, None)  # 一局结束，房间关闭


async def handler(ws):
    online.add(ws)
    broadcast_presence()
    try:
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            t = msg.get("t")
            if t == "create":
                code = new_code()
                rooms[code] = {"host": ws, "guest": None,
                               "names": {ws: msg.get("name", "玩家")},
                               "versions": {ws: msg.get("version", "?")}, "finish": {},
                               "ranked": False}   # 好友房间不计分
                send(ws, {"t": "created", "code": code})
                broadcast_presence()
                print(f"[room] {code} created by {msg.get('name')}")

            elif t == "join":
                c = str(msg.get("code", ""))
                room = rooms.get(c)
                if not room or room["guest"] is not None:
                    send(ws, {"t": "error", "msg": "房间不存在或已满"})
                    continue
                room["guest"] = ws
                room["names"][ws] = msg.get("name", "玩家")
                room["versions"] = room.get("versions", {})
                room["versions"][ws] = msg.get("version", "?")
                send(ws, {"t": "joined", "code": c, "peer": room["names"][room["host"]],
                          "peer_version": room["versions"].get(room["host"], "?")})
                send(room["host"], {"t": "peer_joined", "peer": room["names"][ws],
                                    "version": room["versions"].get(ws, "?")})
                broadcast_presence()
                print(f"[room] {c} joined by {msg.get('name')}")

            elif t == "quick_match":
                enqueue(ws, msg.get("name", "玩家"), msg.get("version", "?"),
                        str(msg.get("subject") or DEFAULT_SUBJECT))
                if not try_match():
                    broadcast_presence()

            elif t == "cancel_match":
                if dequeue(ws):
                    broadcast_presence()

            elif t == "online":
                send(ws, presence())

            elif t == "leaderboard":
                name = msg.get("name", "")
                subject = str(msg.get("subject") or DEFAULT_SUBJECT)
                table = ratings.get(subject, {})
                top = sorted(table.items(), key=lambda kv: -kv[1]["r"])[:10]
                me = None
                if name in table:
                    v = table[name]
                    me = {"name": name, "rating": v["r"], "wins": v["w"],
                          "losses": v["l"], "games": v["g"], "rank": rank_of(subject, name)}
                send(ws, {"t": "leaderboard", "subject": subject,
                          "top": [{"name": n, "rating": v["r"], "wins": v["w"],
                                   "losses": v["l"], "games": v["g"]} for n, v in top],
                          "me": me})

            elif t == "save_put":
                code = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("code", "")))[:24]
                data = str(msg.get("data", ""))
                if not code or not data:
                    send(ws, {"t": "error", "msg": "云存档参数不完整"})
                else:
                    os.makedirs(SAVES_DIR, exist_ok=True)
                    with open(os.path.join(SAVES_DIR, code + ".json"), "w", encoding="utf-8") as f:
                        f.write(data)
                    send(ws, {"t": "save_ok", "size": len(data.encode("utf-8"))})
                    print(f"[save] {code} 上传 {len(data)} 字符")

            elif t == "save_get":
                code = re.sub(r"[^A-Za-z0-9_-]", "", str(msg.get("code", "")))[:24]
                path = os.path.join(SAVES_DIR, code + ".json")
                if code and os.path.isfile(path):
                    with open(path, "r", encoding="utf-8") as f:
                        data = f.read()
                    send(ws, {"t": "save_data", "data": data, "ts": int(os.path.getmtime(path) * 1000)})
                    print(f"[save] {code} 下载 {len(data)} 字符")
                else:
                    send(ws, {"t": "error", "msg": "云存档不存在，请先在上传过存档的设备上上传"})

            elif t in ("start", "question", "answer", "ready", "finish"):
                # 在自己所在房间内转发（快速匹配与 create/join 统一按成员关系查）
                target = None
                for c, room in rooms.items():
                    if ws in (room["host"], room["guest"]):
                        target = c
                        break
                if not target:
                    continue
                if t == "finish":
                    room = rooms[target]
                    room["finish"][ws] = (msg.get("correct", 0), msg.get("timeMs", 0))
                    peer = peer_of(target, ws)
                    if peer:
                        send(peer, {"t": "peer_finish", "correct": msg.get("correct", 0),
                                    "timeMs": msg.get("timeMs", 0)})
                    try_result(target)
                else:
                    peer = peer_of(target, ws)
                    if peer:
                        out = dict(msg)
                        if t == "answer":
                            out["t"] = "peer_answer"
                        send(peer, out)

            elif t == "ping":
                send(ws, {"t": "pong"})
    except websockets.ConnectionClosed:
        pass
    finally:
        online.discard(ws)
        dequeue(ws)
        # 统一按成员关系清理房间（覆盖快速匹配/create/join 三种来源）
        for c in [c for c, room in rooms.items() if ws in (room["host"], room["guest"])]:
            room = rooms[c]
            peer = room["guest"] if room["host"] == ws else room["host"]
            if peer:
                send(peer, {"t": "peer_left"})
            room["names"].pop(ws, None)
            if room["host"] == ws:
                rooms.pop(c, None)
            elif room["guest"] == ws:
                room["guest"] = None
            print(f"[room] {c} member left")
        broadcast_presence()


async def main(port):
    load_ratings()
    async with websockets.serve(handler, "0.0.0.0", port, ping_interval=20):
        print(f"PK 服务器已启动: ws://0.0.0.0:{port}（数据目录 {DATA_DIR}）")
        print("模拟器连接: ws://10.0.2.2:%d | 手机(同Wi-Fi): ws://<电脑IP>:%d" % (port, port))
        await asyncio.Future()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    asyncio.run(main(port))
