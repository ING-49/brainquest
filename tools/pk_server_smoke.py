"""PK 服务器冒烟测试：覆盖在线人数 / 快速匹配 / 版本隔离 / 取消匹配 / 完整一局。

用法：python tools/pk_server_smoke.py [ws://地址]
默认 ws://127.0.0.1:8765；部署后可跑 python tools/pk_server_smoke.py ws://8.148.192.129:8765
"""
import asyncio
import json
import sys

import websockets

URL = sys.argv[1] if len(sys.argv) > 1 else "ws://127.0.0.1:8765"
V = "1.6.1"


async def recv_until(ws, want, timeout=5):
    """收消息直到出现指定 t，返回该消息；超时返回 None（其余消息跳过）"""
    try:
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
            if msg.get("t") == want:
                return msg
    except asyncio.TimeoutError:
        return None


async def main():
    results = []

    def check(name, cond):
        results.append(cond)
        print(("  [PASS] " if cond else "  [FAIL] ") + name)

    # 1. 在线人数查询
    a = await websockets.connect(URL)
    await a.send(json.dumps({"t": "online"}))
    r = await recv_until(a, "online")
    check("online 查询返回统计", r is not None and r.get("players", 0) >= 1)
    await a.close()

    # 2. 版本不一致不配对
    b = await websockets.connect(URL)
    c = await websockets.connect(URL)
    await b.send(json.dumps({"t": "quick_match", "name": "B", "version": V}))
    await c.send(json.dumps({"t": "quick_match", "name": "C", "version": "9.9.9"}))
    r = await recv_until(c, "created", timeout=2)
    check("版本不一致不配对", r is None)

    # 3. 同版本自动配对：先入队 B 为甲方
    d = await websockets.connect(URL)
    await d.send(json.dumps({"t": "quick_match", "name": "D", "version": V}))
    rb = await recv_until(b, "created", timeout=5)
    rd = await recv_until(d, "joined", timeout=5)
    check("同版本自动配对（甲 created / 乙 joined）", rb is not None and rd is not None)

    # 4. ready 互转
    await b.send(json.dumps({"t": "ready"}))
    await d.send(json.dumps({"t": "ready"}))
    rbd = await recv_until(b, "ready", timeout=5)
    check("ready 互转", rbd is not None)

    # 5. 完整一局：start 下发 → 逐题 answer → finish → 判定
    qs = [{"id": f"smoke{i}", "subject": "数学口算", "difficulty": 1, "type": "choice",
           "question": f"{i}+{i}=?", "options": ["0", "1", "2", "3"], "answer": 0,
           "explanation": "smoke", "tags": []} for i in range(10)]
    await b.send(json.dumps({"t": "start", "questions": qs}))
    rs = await recv_until(d, "start", timeout=5)
    check("题目整包下发", rs is not None and len(rs.get("questions", [])) == 10)
    for i in range(10):
        await b.send(json.dumps({"t": "answer", "idx": i, "correct": i < 7, "timeMs": 100}))
        await d.send(json.dumps({"t": "answer", "idx": i, "correct": i < 5, "timeMs": 120}))
    await b.send(json.dumps({"t": "finish", "correct": 7, "timeMs": 1000}))
    await d.send(json.dumps({"t": "finish", "correct": 5, "timeMs": 1100}))
    rw = await recv_until(b, "result", timeout=5)
    rl = await recv_until(d, "result", timeout=5)
    check("判定胜负（7:5 甲胜乙败）",
          rw is not None and rw["outcome"] == "win" and rl is not None and rl["outcome"] == "lose")

    # 6. 取消匹配：E 取消后不被后来者配对
    e = await websockets.connect(URL)
    await e.send(json.dumps({"t": "quick_match", "name": "E", "version": V}))
    await asyncio.sleep(0.3)
    await e.send(json.dumps({"t": "cancel_match"}))
    f = await websockets.connect(URL)
    await f.send(json.dumps({"t": "quick_match", "name": "F", "version": V}))
    re_ = await recv_until(e, "created", timeout=2)
    check("取消匹配后不再被配对", re_ is None)

    for ws in (b, c, d, e, f):
        await ws.close()
    ok, fail = sum(results), len(results) - sum(results)
    print(f"\n结果: {ok} 通过, {fail} 失败 @ {URL}")
    sys.exit(1 if fail else 0)


asyncio.run(main())
