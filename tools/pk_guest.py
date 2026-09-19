"""模拟对手：加入指定房间，自动答完 10 题（用于端到端验证联机对战）。

用法：python tools/pk_guest.py <房间码> [答对数]
"""
import asyncio
import json
import sys
import time

import websockets

CODE = sys.argv[1] if len(sys.argv) > 1 else ""
TARGET_CORRECT = int(sys.argv[2]) if len(sys.argv) > 2 else 10
URL = __import__("os").environ.get("PK_URL", "ws://localhost:8765")


async def main():
    async with websockets.connect(URL) as ws:
        await ws.send(json.dumps({"t": "join", "code": CODE, "name": "机器人对手"}))
        questions = []
        my_done = False
        t0 = time.time()
        result_seen = None

        async def answer_flow():
            """只发送，不 recv（避免与主循环抢占消息）"""
            while not questions:
                await asyncio.sleep(0.2)
            print(f"[guest] 收到 {len(questions)} 题，开始作答")
            for i, q in enumerate(questions):
                await asyncio.sleep(1.0)
                correct = i < TARGET_CORRECT
                await ws.send(json.dumps({
                    "t": "answer", "idx": i, "correct": correct,
                    "timeMs": 1200 + i * 100,
                }))
                print(f"[guest] 第{i+1}题作答 correct={correct}")
            await asyncio.sleep(0.5)
            await ws.send(json.dumps({"t": "finish", "correct": TARGET_CORRECT,
                                      "timeMs": int((time.time() - t0) * 1000)}))
            print("[guest] 已提交成绩")

        task = asyncio.create_task(answer_flow())
        try:
            while True:
                msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=150))
                t = msg.get("t")
                if t == "joined":
                    print(f"[guest] 已加入房间 {msg['code']}，对手: {msg['peer']}")
                elif t == "start":
                    questions = msg["questions"]  # 触发 answer_flow
                elif t == "peer_finish":
                    print("[guest] 对手已完成")
                elif t == "result":
                    result_seen = msg
                    print(f"[guest] 结果: {msg['outcome']}  我 {msg['my']['correct']} 题 / "
                          f"对手 {msg['peer']['correct']} 题")
                    break
                elif t == "peer_left":
                    print("[guest] 对手离开，结束")
                    break
        except asyncio.TimeoutError:
            print("[guest] 等待消息超时")
        task.cancel()


asyncio.run(main())
