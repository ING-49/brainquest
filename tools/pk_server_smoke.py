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
    # 清空等待队列（c 版本独苗、f 无人可配），避免干扰后续分科目配对测试
    await c.close()
    await f.close()
    await asyncio.sleep(0.3)

    # 7. ELO：快速匹配对局返回对称积分（胜者加分 = 败者减分）
    rw_rating = rw.get("rating") if rw else None
    rl_rating = rl.get("rating") if rl else None
    check("快速匹配结果带计分信息",
          bool(rw_rating and rw_rating.get("ranked")) and bool(rl_rating and rl_rating.get("ranked")))
    check("ELO 对称（胜者 delta = -败者 delta）",
          bool(rw_rating and rl_rating and rw_rating.get("delta") == -rl_rating.get("delta")))
    check("ELO 胜者加分", bool(rw_rating and rw_rating.get("delta", 0) >= 8))

    # 8. 好友房间不计分
    g = await websockets.connect(URL)
    g2 = await websockets.connect(URL)
    await g.send(json.dumps({"t": "create", "name": "G", "version": V}))
    rc = await recv_until(g, "created", timeout=5)
    await g2.send(json.dumps({"t": "join", "code": rc["code"], "name": "H", "version": V}))
    await recv_until(g2, "joined", timeout=5)
    qs2 = [{"id": f"roomq{i}", "subject": "数学口算", "difficulty": 1, "type": "choice",
            "question": f"{i}+1=?", "options": ["0", "1", "2", "3"], "answer": 1,
            "explanation": "smoke", "tags": []} for i in range(10)]
    await g.send(json.dumps({"t": "start", "questions": qs2}))
    await recv_until(g2, "start", timeout=5)
    await g.send(json.dumps({"t": "finish", "correct": 5, "timeMs": 500}))
    await g2.send(json.dumps({"t": "finish", "correct": 3, "timeMs": 600}))
    rg = await recv_until(g, "result", timeout=5)
    check("好友房间不计分（ranked=false）",
          bool(rg and rg.get("rating") and rg["rating"].get("ranked") is False))

    # 9. 排行榜：包含刚才计分玩家（混合桶）
    await b.send(json.dumps({"t": "leaderboard", "name": "B"}))
    lb = await recv_until(b, "leaderboard", timeout=5)
    check("排行榜返回 Top 列表与我的排名",
          bool(lb and isinstance(lb.get("top"), list) and len(lb["top"]) >= 1
               and lb.get("me") and lb["me"].get("rank", 0) >= 1))

    # 9b. 排行榜按科目分桶：混合桶有 B，数学口算桶独立为空
    await b.send(json.dumps({"t": "leaderboard", "name": "B", "subject": "数学口算"}))
    lb_sub = await recv_until(b, "leaderboard", timeout=5)
    check("排行榜按科目分桶（数学口算桶不含混合桶玩家）",
          bool(lb_sub and lb_sub.get("subject") == "数学口算" and lb_sub.get("me") is None))

    # 9c. 带科目的快速匹配计分进对应科目桶
    i = await websockets.connect(URL)
    j = await websockets.connect(URL)
    await i.send(json.dumps({"t": "quick_match", "name": "smokeI", "version": V, "subject": "数学口算"}))
    await j.send(json.dumps({"t": "quick_match", "name": "smokeJ", "version": V}))
    ri = await recv_until(i, "created", timeout=5)
    await recv_until(j, "joined", timeout=5)
    await i.send(json.dumps({"t": "finish", "correct": 6, "timeMs": 400}))
    await j.send(json.dumps({"t": "finish", "correct": 2, "timeMs": 500}))
    rsub = await recv_until(i, "result", timeout=5)
    check("数学口算局计分（ranked 且带积分）",
          bool(rsub and rsub.get("rating", {}).get("ranked")))
    await i.send(json.dumps({"t": "leaderboard", "name": "smokeI", "subject": "数学口算"}))
    lbi = await recv_until(i, "leaderboard", timeout=5)
    await i.send(json.dumps({"t": "leaderboard", "name": "smokeI"}))
    lbm = await recv_until(i, "leaderboard", timeout=5)
    check("科目桶独立（数学口算榜有我、混合榜无我）",
          bool(lbi and lbi.get("me") and lbi["me"].get("rank", 0) >= 1
               and lbm and lbm.get("me") is None))

    # 10. 云存档（v1.6.7 起必须为加密信封）：上传 → 下载一致
    envelope = json.dumps({"fmt": "BQENC1", "salt": "c2FsdHNhbHQ=", "iters": 60000,
                           "iv": "aXZpdml2aXZp", "ct": "Y2lwaGVydGV4dA=="}, ensure_ascii=False)
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "data": envelope}))
    ok_ = await recv_until(b, "save_ok", timeout=5)
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1"}))
    sd = await recv_until(b, "save_data", timeout=5)
    check("云存档信封上传+下载一致", bool(ok_ and sd and sd.get("data") == envelope))

    # 10b. 旧版明文存档不再接受上传（防回退明文存储；旧文件下载兼容在 App 端做）
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "data": '{"nickname":"明文"}'}))
    plain_err = await recv_until(b, "error", timeout=5)
    check("明文存档上传被拒绝", bool(plain_err and "加密" in plain_err.get("msg", "")))

    # 10c. save_del：删除后不可再下载（用户数据删除通道）
    await b.send(json.dumps({"t": "save_del", "code": "SMOKE1"}))
    del_ok = await recv_until(b, "save_del_ok", timeout=5)
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1"}))
    gone = await recv_until(b, "error", timeout=5)
    check("云存档删除后不可下载", bool(del_ok and gone and "不存在" in gone.get("msg", "")))

    # 10d. save_get 限流：新连接连续下载超过阈值后被拒（防暴力试码）
    rl_ws = await websockets.connect(URL)
    limited = False
    for _ in range(8):
        await rl_ws.send(json.dumps({"t": "save_get", "code": "NOSUCH99"}))
        r_ = await recv_until(rl_ws, "error", timeout=3)
        if r_ and "频繁" in r_.get("msg", ""):
            limited = True
            break
    check("save_get 超频被限流", limited)
    await rl_ws.close()

    for ws in (b, d, e, g, g2, i, j):
        await ws.close()
    ok, fail = sum(results), len(results) - sum(results)
    print(f"\n结果: {ok} 通过, {fail} 失败 @ {URL}")
    sys.exit(1 if fail else 0)


asyncio.run(main())
