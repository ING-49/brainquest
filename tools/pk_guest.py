"""模拟对手：加入指定房间或快速匹配，自动答完 10 题（用于端到端验证联机对战）。

用法：
  python tools/pk_guest.py <房间码> [答对数] [版本]      # 好友房间模式（当乙方）
  python tools/pk_guest.py --quick [答对数] [版本]       # 快速匹配（可当甲方房主）
  PK_QUICK=1 python tools/pk_guest.py ...               # 同 --quick
环境变量：PK_URL（默认 ws://localhost:8765）
"""
import asyncio
import json
import os
import sys
import time

import websockets

argv = sys.argv[1:]
if argv and argv[0] == "--quick":
    QUICK = True
    argv = argv[1:]
else:
    QUICK = os.environ.get("PK_QUICK") == "1"


def arg(i, default):
    return argv[i] if len(argv) > i else default


CODE = "" if QUICK else arg(0, "")
TARGET_CORRECT = int(arg(1 if not QUICK else 0, 10))


def default_version():
    """版本取 update-server/manifest.json 的 latestVersionName（与线上 App 一致才能配对），
    读不到回落最后一次已知发版号；命令行显式传入的版本优先。"""
    explicit = arg(2 if not QUICK else 1, "")
    if explicit:
        return explicit
    m = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "update-server", "manifest.json")
    try:
        with open(m, encoding="utf-8") as f:
            v = json.load(f).get("latestVersionName")
            if v:
                return v
    except (OSError, ValueError):
        pass
    return "1.6.8"


VERSION = default_version()
URL = os.environ.get("PK_URL", "ws://localhost:8765")
NAME = "机器人对手"


def synth_questions(n=10):
    """房主机器人用的合成题目（App 端可正常解码渲染）"""
    return [{
        "id": f"bot_q{i}", "subject": "数学口算", "difficulty": 2, "type": "choice",
        "question": f"{i} + {i} = ?",
        "options": [str(i * 2), str(i * 2 + 1), str(i * 2 + 2), str(max(0, i * 2 - 1))],
        "answer": 0, "explanation": f"{i} × 2 = {i * 2}", "tags": [],
    } for i in range(1, n + 1)]


async def main():
    async with websockets.connect(URL) as ws:
        hello = ({"t": "quick_match", "name": NAME, "version": VERSION} if QUICK else
                 {"t": "join", "code": CODE, "name": NAME, "version": VERSION})
        await ws.send(json.dumps(hello))
        print(f"[bot] 已连接 {URL}（{'快速匹配' if QUICK else '房间 ' + CODE}，v{VERSION}）")

        state = {"questions": [], "started": False}
        t0 = time.time()

        async def answer_flow():
            # v1.6.9 服务器中立对战：等待服务器抽题下发，按题库答案发 choice（前 TARGET_CORRECT 题答对）
            while not state["started"]:
                await asyncio.sleep(0.2)
            qs = state["questions"]
            print(f"[bot] 收到服务器 {len(qs)} 题，开始作答")
            for i, q in enumerate(qs):
                await asyncio.sleep(1.0)
                correct = i < TARGET_CORRECT
                opts = q.get("options") or [""]
                choice = q["answer"] if correct else (q["answer"] + 1) % len(opts)
                await ws.send(json.dumps({
                    "t": "answer", "idx": i, "choice": choice,
                    "timeMs": 1200 + i * 100,
                }))
                print(f"[bot] 第{i+1}题 choice={choice}（目标{'答对' if correct else '答错'}）")
            await asyncio.sleep(0.5)
            await ws.send(json.dumps({"t": "finish", "timeMs": int((time.time() - t0) * 1000)}))
            print("[bot] 已交卷（判分以服务器为准）")

        task = asyncio.create_task(answer_flow())
        try:
            while True:
                msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=150))
                t = msg.get("t")
                if t == "created":
                    print(f"[bot] 配对成功 {msg['code']}，我是甲方")
                    await ws.send(json.dumps({"t": "ready"}))
                elif t == "peer_joined":
                    print(f"[bot] 对手加入: {msg.get('peer')}")
                elif t == "joined":
                    print(f"[bot] 已配对/加入 {msg['code']}，对手: {msg.get('peer')}，发送准备")
                    await ws.send(json.dumps({"t": "ready"}))
                elif t == "start":
                    state["questions"] = msg["questions"]
                    state["started"] = True
                elif t == "peer_finish":
                    print("[bot] 对手已完成")
                elif t == "result":
                    print(f"[bot] 结果: {msg['outcome']}  我 {msg['my']['correct']} 题 / "
                          f"对手 {msg['peer']['correct']} 题")
                    break
                elif t == "error":
                    print(f"[bot] 错误: {msg.get('msg')}")
                    break
                elif t == "peer_left":
                    print("[bot] 对手离开，结束")
                    break
        except asyncio.TimeoutError:
            print("[bot] 等待消息超时")
        task.cancel()


asyncio.run(main())
