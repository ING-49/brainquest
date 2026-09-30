"""旧版按昵称计分数据 → 身份码积分的迁移验证（v1.6.18）。

流程：调用方先用临时数据目录起一个临时端口的服务器（见下方用法）→
本脚本用 name_claim（身份码 + 旧昵称）认领 → 查排行榜断言旧积分被继承、
未认领旧昵称的新身份从 1000 分起步。

用法：
  mkdir /tmp/pk_mig && echo '{"线性代数":{"老玩家":{"r":1234,"w":5,"l":2,"g":7}}}' > /tmp/pk_mig/ratings.json
  PK_DATA_DIR=/tmp/pk_mig python update-server/pk_server.py 8766 &
  python tools/pk_server_migration_check.py ws://127.0.0.1:8766
"""
import asyncio
import json
import sys

import websockets

URL = sys.argv[1] if len(sys.argv) > 1 else "ws://127.0.0.1:8766"


async def recv_until(ws, want, timeout=5):
    """收消息直到出现指定 t（跳过 online 等广播），超时返回 None"""
    try:
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
            if msg.get("t") == want:
                return msg
    except asyncio.TimeoutError:
        return None


async def main():
    ok = True

    def check(name, cond):
        nonlocal ok
        if not cond:
            ok = False
        print(("  [PASS] " if cond else "  [FAIL] ") + name)

    # 1) 新身份认领旧昵称 → 继承 1234 分
    a = await websockets.connect(URL)
    await a.send(json.dumps({"t": "name_claim", "name": "老玩家", "identity": "MIG_OLD1"}))
    r = await recv_until(a, "name_claim")
    check("认领旧昵称成功", bool(r and r.get("ok") is True))
    await a.send(json.dumps({"t": "leaderboard", "name": "老玩家", "subject": "线性代数", "identity": "MIG_OLD1"}))
    lb = await recv_until(a, "leaderboard")
    check("旧积分被继承（1234 分 / 7 局）",
          bool(lb.get("me") and lb["me"].get("rating") == 1234 and lb["me"].get("games") == 7))
    await a.close()

    # 2) 另一个身份用其他昵称 → 无旧记录，从 1000 分起步
    b = await websockets.connect(URL)
    await b.send(json.dumps({"t": "name_claim", "name": "其他名字", "identity": "MIG_OLD2"}))
    await recv_until(b, "name_claim")
    await b.send(json.dumps({"t": "leaderboard", "name": "其他名字", "subject": "线性代数", "identity": "MIG_OLD2"}))
    lb2 = await recv_until(b, "leaderboard")
    check("未认领旧昵称的新身份不继承旧分（未打过局即不上榜）",
          bool(lb2 is not None and (lb2.get("me") is None or lb2["me"].get("rating") == 1000)))
    await b.close()

    print("\n结果:", "迁移逻辑全部通过" if ok else "存在失败项")
    sys.exit(0 if ok else 1)


asyncio.run(main())
