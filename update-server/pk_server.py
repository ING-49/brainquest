"""
脑力大冒险 · 联机对战服务器（好友码房间制，WebSocket）

协议（JSON）：
  客户端 → 服务器：
    {"t":"create","name":"昵称"}                    → 创建房间
    {"t":"join","code":"123456","name":"昵称"}       → 加入房间
    {"t":"start","questions":[...]}                 （房主）开始并下发题目
    {"t":"answer","idx":0,"correct":true,"timeMs":1234}
    {"t":"finish","correct":3,"timeMs":9876}
    {"t":"chat","text":"..."}                       （预留）
  服务器 → 客户端：
    {"t":"created","code":"123456"}
    {"t":"joined","code":"123456","peer":"对方昵称"}
    {"t":"peer_joined","peer":"对方昵称"}
    {"t":"start","questions":[...]}                 （转发给加入方）
    {"t":"question","idx":0,"q":{...}}              （房主逐题下发时转发）
    {"t":"peer_answer","idx":0,"correct":true}
    {"t":"peer_finish","correct":3,"timeMs":9876}
    {"t":"result","outcome":"win|lose|draw","my":{"correct":3,"timeMs":9876},"peer":{"correct":2,"timeMs":8000}}
    {"t":"peer_left"}
    {"t":"error","msg":"..."}

用法：python pk_server.py [端口，默认 8765]
"""
import asyncio
import json
import random
import sys

import websockets

rooms = {}  # code -> {"host": ws, "guest": ws, "names": {ws: name}, "finish": {ws: (correct, timeMs)}}


def send(ws, obj):
    asyncio.ensure_future(ws.send(json.dumps(obj, ensure_ascii=False)))


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
    for ws, my, other, label in (
        (host, (hc, ht), (gc, gt), None),
        (guest, (gc, gt), (hc, ht), None),
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
    code = None
    try:
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            t = msg.get("t")
            if t == "create":
                code = f"{random.randint(0, 999999):06d}"
                while code in rooms:
                    code = f"{random.randint(0, 999999):06d}"
                rooms[code] = {"host": ws, "guest": None,
                               "names": {ws: msg.get("name", "玩家")},
                               "versions": {ws: msg.get("version", "?")}, "finish": {}}
                send(ws, {"t": "created", "code": code})
                print(f"[room] {code} created by {msg.get('name')}")

            elif t == "join":
                c = str(msg.get("code", ""))
                room = rooms.get(c)
                if not room or room["guest"] is not None:
                    send(ws, {"t": "error", "msg": "房间不存在或已满"})
                    continue
                code = c
                room["guest"] = ws
                room["names"][ws] = msg.get("name", "玩家")
                room["versions"] = room.get("versions", {})
                room["versions"][ws] = msg.get("version", "?")
                send(ws, {"t": "joined", "code": code, "peer": room["names"][room["host"]],
                          "peer_version": room["versions"].get(room["host"], "?")})
                send(room["host"], {"t": "peer_joined", "peer": room["names"][ws],
                                    "version": room["versions"].get(ws, "?")})
                print(f"[room] {code} joined by {msg.get('name')}")

            elif code and t in ("start", "question", "answer"):
                peer = peer_of(code, ws)
                if peer:
                    out = dict(msg)
                    if t == "answer":
                        out["t"] = "peer_answer"
                    send(peer, out)

            elif code and t == "finish":
                room = rooms.get(code)
                if room:
                    room["finish"][ws] = (msg.get("correct", 0), msg.get("timeMs", 0))
                    peer = peer_of(code, ws)
                    if peer:
                        send(peer, {"t": "peer_finish", "correct": msg.get("correct", 0),
                                    "timeMs": msg.get("timeMs", 0)})
                    try_result(code)

            elif t == "ping":
                send(ws, {"t": "pong"})
    except websockets.ConnectionClosed:
        pass
    finally:
        if code and code in rooms:
            room = rooms[code]
            peer = peer_of(code, ws)
            if peer:
                send(peer, {"t": "peer_left"})
            room["names"].pop(ws, None)
            if room["host"] == ws:
                rooms.pop(code, None)
            elif room["guest"] == ws:
                room["guest"] = None
            print(f"[room] {code} member left")


async def main(port):
    async with websockets.serve(handler, "0.0.0.0", port, ping_interval=20):
        print(f"PK 服务器已启动: ws://0.0.0.0:{port}")
        print("模拟器连接: ws://10.0.2.2:%d | 手机(同Wi-Fi): ws://<电脑IP>:%d" % (port, port))
        await asyncio.Future()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    asyncio.run(main(port))
