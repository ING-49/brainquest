"""
脑力大冒险 · 联机对战服务器（好友码房间制 + 快速匹配，WebSocket）

协议（JSON）：
  客户端 → 服务器：
    {"t":"create","name":"昵称"}                    → 创建房间
    {"t":"join","code":"123456","name":"昵称"}       → 加入房间
    {"t":"quick_match","name":"昵称"}               → 进入快速匹配队列（同版本才配对）
    {"t":"cancel_match"}                            → 退出匹配队列
    {"t":"online"}                                  → 查询在线人数
    {"t":"start","questions":[...]}                 （房主）开始并下发题目
    {"t":"answer","idx":0,"correct":true,"timeMs":1234}
    {"t":"finish","correct":3,"timeMs":9876}
    {"t":"chat","text":"..."}                       （预留）
  服务器 → 客户端：
    {"t":"created","code":"123456"}
    {"t":"joined","code":"123456","peer":"对方昵称"}
    {"t":"peer_joined","peer":"对方昵称"}
    {"t":"online","players":N,"waiting":K,"rooms":M}   （广播：在线/等待匹配/房间数）
    {"t":"start","questions":[...]}                 （转发给加入方）
    {"t":"question","idx":0,"q":{...}}              （房主逐题下发时转发）
    {"t":"peer_answer","idx":0,"correct":true}
    {"t":"peer_finish","correct":3,"timeMs":9876}
    {"t":"result","outcome":"win|lose|draw","my":{"correct":3,"timeMs":9876},"peer":{"correct":2,"timeMs":8000}}
    {"t":"peer_left"}
    {"t":"error","msg":"..."}

快速匹配配对成功后：甲方（先入队）收到 created+peer_joined（走房主路径），
乙方收到 joined（走加入方路径），后续与好友房间完全一致。

用法：python pk_server.py [端口，默认 8765]
"""
import asyncio
import json
import random
import sys

import websockets

rooms = {}   # code -> {"host": ws, "guest": ws, "names": {ws: name}, "versions": {ws: version}, "finish": {ws: (correct, timeMs)}}
online = set()   # 当前所有连接
queue = []   # 快速匹配等待队列：[{"ws","name","version"}, ...]


def send(ws, obj):
    asyncio.ensure_future(ws.send(json.dumps(obj, ensure_ascii=False)))


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


def enqueue(ws, name, version):
    if any(e["ws"] == ws for e in queue):
        return
    queue.append({"ws": ws, "name": name, "version": version})
    print(f"[match] {name} v{version} 入队（等待 {len(queue)}）")


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
                   "finish": {}}
    # 甲方走房主路径：created → peer_joined（收到后本地选题并发 start）
    send(a["ws"], {"t": "created", "code": code})
    send(a["ws"], {"t": "peer_joined", "peer": b["name"], "version": b["version"]})
    # 乙方走加入方路径
    send(b["ws"], {"t": "joined", "code": code, "peer": a["name"],
                   "peer_version": a["version"]})
    print(f"[match] {code} 配对成功: {a['name']} vs {b['name']}")


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
        send(ws, {"t": "result", "outcome": outcome,
                  "my": {"correct": my[0], "timeMs": my[1]},
                  "peer": {"correct": other[0], "timeMs": other[1]}})
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
                               "versions": {ws: msg.get("version", "?")}, "finish": {}}
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
                enqueue(ws, msg.get("name", "玩家"), msg.get("version", "?"))
                if not try_match():
                    broadcast_presence()

            elif t == "cancel_match":
                if dequeue(ws):
                    broadcast_presence()

            elif t == "online":
                send(ws, presence())

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
    async with websockets.serve(handler, "0.0.0.0", port, ping_interval=20):
        print(f"PK 服务器已启动: ws://0.0.0.0:{port}")
        print("模拟器连接: ws://10.0.2.2:%d | 手机(同Wi-Fi): ws://<电脑IP>:%d" % (port, port))
        await asyncio.Future()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    asyncio.run(main(port))
