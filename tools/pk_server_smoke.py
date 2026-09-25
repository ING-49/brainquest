"""PK 服务器冒烟测试：覆盖在线人数 / 快速匹配 / 版本隔离 / 取消匹配 / 完整一局。

用法：python tools/pk_server_smoke.py [ws://地址]
默认 ws://127.0.0.1:8765；部署后可跑 python tools/pk_server_smoke.py ws://8.148.192.129:8765
"""
import asyncio
import json
import sys

import websockets

URL = sys.argv[1] if len(sys.argv) > 1 else "ws://127.0.0.1:8765"


def _default_version():
    """配对用版本号：与 pk_guest.py 同源，优先取 update-server/manifest.json 的 latestVersionName，
    显式传第二参可覆盖（python pk_server_smoke.py ws://host 1.6.8）。"""
    if len(sys.argv) > 2:
        return sys.argv[2]
    import json as _json
    import os as _os
    m = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "update-server", "manifest.json")
    try:
        with open(m, encoding="utf-8") as f:
            v = _json.load(f).get("latestVersionName")
            if v:
                return v
    except (OSError, ValueError):
        pass
    return "1.6.8"


V = _default_version()


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

    # 2. 版本不一致不配对 + 排队版本提示
    b = await websockets.connect(URL)
    c = await websockets.connect(URL)
    await b.send(json.dumps({"t": "quick_match", "name": "B", "version": V, "identity": "SMOKE_B"}))
    await c.send(json.dumps({"t": "quick_match", "name": "C", "version": "9.9.9", "identity": "SMOKE_C"}))
    hint = await recv_until(c, "error", timeout=3)
    check("排队版本不同有明确提示", bool(hint and "版本" in hint.get("msg", "")))
    r = await recv_until(c, "created", timeout=1)
    check("版本不一致不配对", r is None)

    # 3. 同版本自动配对：先入队 B 为甲方
    d = await websockets.connect(URL)
    await d.send(json.dumps({"t": "quick_match", "name": "D", "version": V, "identity": "SMOKE_D"}))
    rb = await recv_until(b, "created", timeout=5)
    rd = await recv_until(d, "joined", timeout=5)
    check("同版本自动配对（甲 created / 乙 joined）", rb is not None and rd is not None)

    # 3b. 单局约束：对局未结束，同身份不能再开新局（v1.6.10）
    await b.send(json.dumps({"t": "quick_match", "name": "B", "version": V, "identity": "SMOKE_B"}))
    busy_err = await recv_until(b, "error", timeout=3)
    check("对局未结束不可加新局", bool(busy_err and "未结束的对局" in busy_err.get("msg", "")))

    # 4. ready 互转 + 服务器自动出题（v1.6.9 服务器中立对战）
    await b.send(json.dumps({"t": "ready"}))
    await d.send(json.dumps({"t": "ready"}))
    rbd = await recv_until(b, "peer_ready", timeout=5)
    rdb = await recv_until(d, "peer_ready", timeout=5)
    check("ready 互转", rbd is not None and rdb is not None)
    sb = await recv_until(b, "start", timeout=5)
    sd = await recv_until(d, "start", timeout=5)
    check("服务器自动出题（start 同发双方各 10 题）",
          bool(sb and sd and len(sb.get("questions", [])) == 10 and len(sd.get("questions", [])) == 10))
    QS_B = sb.get("questions", []) if sb else []
    QS_D = sd.get("questions", []) if sd else []

    # 5. 完整一局：choice 答题（服务器按题库判分）→ finish → 判定
    for i in range(10):
        qb, qd = QS_B[i], QS_D[i]
        cb = qb["answer"] if i < 7 else (qb["answer"] + 1) % len(qb["options"])
        cd_ = qd["answer"] if i < 5 else (qd["answer"] + 1) % len(qd["options"])
        await b.send(json.dumps({"t": "answer", "idx": i, "choice": cb, "timeMs": 100}))
        await d.send(json.dumps({"t": "answer", "idx": i, "choice": cd_, "timeMs": 120}))
    await b.send(json.dumps({"t": "finish", "timeMs": 1000}))
    await d.send(json.dumps({"t": "finish", "timeMs": 1100}))
    rw = await recv_until(b, "result", timeout=5)
    rl = await recv_until(d, "result", timeout=5)
    check("判定胜负（服务器判分 7:5 甲胜乙败）",
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
    await g.send(json.dumps({"t": "ready"}))
    await g2.send(json.dumps({"t": "ready"}))
    sg = await recv_until(g, "start", timeout=5)
    sg2 = await recv_until(g2, "start", timeout=5)
    for i in range(10):
        qg, qg2 = sg["questions"][i], sg2["questions"][i]
        cg = qg["answer"] if i < 5 else (qg["answer"] + 1) % len(qg["options"])
        cg2 = qg2["answer"] if i < 3 else (qg2["answer"] + 1) % len(qg2["options"])
        await g.send(json.dumps({"t": "answer", "idx": i, "choice": cg, "timeMs": 100}))
        await g2.send(json.dumps({"t": "answer", "idx": i, "choice": cg2, "timeMs": 100}))
    await g.send(json.dumps({"t": "finish", "timeMs": 500}))
    await g2.send(json.dumps({"t": "finish", "timeMs": 600}))
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
    # 注意：两人必须声明同一科目——配对双方谁先入队谁当房主（房主科目=计分桶），
    # 跨连接的发送顺序有竞态，同科目可消除对入队顺序的依赖
    i = await websockets.connect(URL)
    j = await websockets.connect(URL)
    await i.send(json.dumps({"t": "quick_match", "name": "smokeI", "version": V, "subject": "数学口算"}))
    await j.send(json.dumps({"t": "quick_match", "name": "smokeJ", "version": V, "subject": "数学口算"}))
    ri = await recv_until(i, "created", timeout=5)
    await recv_until(j, "joined", timeout=5)
    await i.send(json.dumps({"t": "ready"}))
    await j.send(json.dumps({"t": "ready"}))
    si = await recv_until(i, "start", timeout=5)
    sj = await recv_until(j, "start", timeout=5)
    for i2 in range(10):
        qi, qj = si["questions"][i2], sj["questions"][i2]
        ci = qi["answer"] if i2 < 6 else (qi["answer"] + 1) % len(qi["options"])
        cj = qj["answer"] if i2 < 2 else (qj["answer"] + 1) % len(qj["options"])
        await i.send(json.dumps({"t": "answer", "idx": i2, "choice": ci, "timeMs": 80}))
        await j.send(json.dumps({"t": "answer", "idx": i2, "choice": cj, "timeMs": 90}))
    await i.send(json.dumps({"t": "finish", "timeMs": 400}))
    await j.send(json.dumps({"t": "finish", "timeMs": 500}))
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

    # 9d. 断线韧性（v1.6.10）：peer_lost → resume 恢复进度 → 正常完赛；缺席立即结算
    m = await websockets.connect(URL)
    n = await websockets.connect(URL)
    await m.send(json.dumps({"t": "quick_match", "name": "smokeM", "version": V, "identity": "SMOKE_M"}))
    await n.send(json.dumps({"t": "quick_match", "name": "smokeN", "version": V, "identity": "SMOKE_N"}))
    rm_ = await recv_until(m, "created", timeout=5)
    await recv_until(n, "joined", timeout=5)
    await m.send(json.dumps({"t": "ready"}))
    await n.send(json.dumps({"t": "ready"}))
    sm_ = await recv_until(m, "start", timeout=5)
    sn_ = await recv_until(n, "start", timeout=5)
    check("断线场景服务器正常出题", bool(sm_ and sn_ and len(sm_.get("questions", [])) == 10))
    # m 答前 3 题后断线
    for i2 in range(3):
        qm_ = sm_["questions"][i2]
        await m.send(json.dumps({"t": "answer", "idx": i2, "choice": qm_["answer"], "timeMs": 100}))
    await m.close()
    pl = await recv_until(n, "peer_lost", timeout=5)
    check("对局中断线对手收到 peer_lost（房间保留）", pl is not None)
    # n 继续答题（不受对手掉线影响）
    for i2 in range(3):
        qn_ = sn_["questions"][i2]
        await n.send(json.dumps({"t": "answer", "idx": i2, "choice": qn_["answer"], "timeMs": 100}))
    # m 重连 resume：进度 3 / 自己 3 分 / 对手 3 分
    m2 = await websockets.connect(URL)
    await m2.send(json.dumps({"t": "resume", "name": "smokeM", "version": V, "identity": "SMOKE_M"}))
    rs_ = await recv_until(m2, "resume", timeout=5)
    check("重连恢复进度（idx/双方得分正确）",
          bool(rs_ and rs_.get("idx") == 3 and rs_.get("my") == 3 and rs_.get("peer") == 3
               and len(rs_.get("questions", [])) == 10))
    pb = await recv_until(n, "peer_back", timeout=5)
    check("对手收到 peer_back", pb is not None)
    # 双方答完剩余题并交卷 → 正常结算
    for i2 in range(3, 10):
        qm_ = sm_["questions"][i2]
        qn_ = sn_["questions"][i2]
        await m2.send(json.dumps({"t": "answer", "idx": i2, "choice": qm_["answer"], "timeMs": 100}))
        await n.send(json.dumps({"t": "answer", "idx": i2, "choice": qn_["answer"], "timeMs": 100}))
    await m2.send(json.dumps({"t": "finish", "timeMs": 3000}))
    await n.send(json.dumps({"t": "finish", "timeMs": 3100}))
    rm2 = await recv_until(m2, "result", timeout=5)
    rn2 = await recv_until(n, "result", timeout=5)
    check("重连后完赛正常结算（10:10 平分，用时短者胜）",
          bool(rm2 and rn2 and rm2["outcome"] in ("win", "draw") and rm2["my"]["correct"] == 10
               and rn2["my"]["correct"] == 10))
    await m2.close()
    await n.close()

    # 9e. 对局中掉线不回归 → 在场玩家交卷立即结算（全对立胜，标注对手掉线）
    m = await websockets.connect(URL)
    n = await websockets.connect(URL)
    await m.send(json.dumps({"t": "quick_match", "name": "smokeM", "version": V, "identity": "SMOKE_M"}))
    await n.send(json.dumps({"t": "quick_match", "name": "smokeN", "version": V, "identity": "SMOKE_N"}))
    await recv_until(m, "created", timeout=5)
    await recv_until(n, "joined", timeout=5)
    await m.send(json.dumps({"t": "ready"}))
    await n.send(json.dumps({"t": "ready"}))
    sm_ = await recv_until(m, "start", timeout=5)
    sn_ = await recv_until(n, "start", timeout=5)
    await m.close()   # m 开局即断线
    pl = await recv_until(n, "peer_lost", timeout=5)
    # n 全部答对后交卷 → 立即结算（不等待）
    for i2 in range(10):
        qn_ = sn_["questions"][i2]
        await n.send(json.dumps({"t": "answer", "idx": i2, "choice": qn_["answer"], "timeMs": 100}))
    await n.send(json.dumps({"t": "finish", "timeMs": 4000}))
    rn_ = await recv_until(n, "result", timeout=5)
    check("对手缺席交卷立即结算（全对立胜 + 标注掉线）",
          bool(rn_ and rn_["outcome"] == "win" and rn_.get("reason") == "对手掉线"
               and rn_["my"]["correct"] == 10))
    await n.close()
    # 迟到的重连：对局已结束
    m3 = await websockets.connect(URL)
    await m3.send(json.dumps({"t": "resume", "name": "smokeM", "version": V, "identity": "SMOKE_M"}))
    late = await recv_until(m3, "error", timeout=5)
    check("迟到重连得到明确反馈", bool(late and "没有可恢复" in late.get("msg", "")))
    await m3.close()

    # 10. 云存档（v1.6.7 起必须为加密信封）：上传 → 下载一致
    envelope = json.dumps({"fmt": "BQENC1", "salt": "c2FsdHNhbHQ=", "iters": 60000,
                           "iv": "aXZpdml2aXZp", "ct": "Y2lwaGVydGV4dA=="}, ensure_ascii=False)
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "owner": "SMOKEOWN1", "data": envelope}))
    ok_ = await recv_until(b, "save_ok", timeout=5)
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1", "owner": "SMOKEOWN1"}))
    sd = await recv_until(b, "save_data", timeout=5)
    check("云存档信封上传+下载一致", bool(ok_ and sd and sd.get("data") == envelope))

    # 10b. 旧版明文存档不再接受上传（防回退明文存储；旧文件下载兼容在 App 端做）
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "owner": "SMOKEOWN1", "data": '{"nickname":"明文"}'}))
    plain_err = await recv_until(b, "error", timeout=5)
    check("明文存档上传被拒绝", bool(plain_err and "加密" in plain_err.get("msg", "")))

    # 10b-2. 他人身份下载/覆盖被拒（身份归属校验，v1.6.9）
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1", "owner": "SMOKEOWN2"}))
    own_err = await recv_until(b, "error", timeout=5)
    check("他人身份下载被拒", bool(own_err and "身份不符" in own_err.get("msg", "")))
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "owner": "SMOKEOWN2", "data": envelope}))
    own_err2 = await recv_until(b, "error", timeout=5)
    check("他人身份覆盖被拒", bool(own_err2 and "身份" in own_err2.get("msg", "")))

    # 10b-3. 归属转移（换设备恢复场景）：A 身份授权 → B 可接管
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1", "owner": "SMOKEOWN1", "new_owner": "SMOKEOWN2"}))
    sd2 = await recv_until(b, "save_data", timeout=5)
    await b.send(json.dumps({"t": "save_put", "code": "SMOKE1", "owner": "SMOKEOWN2", "data": envelope}))
    ok2 = await recv_until(b, "save_ok", timeout=5)
    check("归属转移后新身份可接管", bool(sd2 and ok2))

    # 10c. save_del：删除后不可再下载（用户数据删除通道；需归属身份）
    await b.send(json.dumps({"t": "save_del", "code": "SMOKE1", "owner": "SMOKEOWN2"}))
    del_ok = await recv_until(b, "save_del_ok", timeout=5)
    await b.send(json.dumps({"t": "save_get", "code": "SMOKE1", "owner": "SMOKEOWN1"}))
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
