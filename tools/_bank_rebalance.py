"""⚠️ 一次性脚本（one-time，勿重跑）：题库正确答案分布均衡化。

背景：题库源数据正确项严重集中在 B（55-62%）和 A（20-30%），D 几乎没有；
正常玩法已被 QuestionBank.shuffleOptions 运行时洗牌抵消，但错题复习保留存储原序，
直接暴露该偏差。本脚本对每个题库文件按种子随机把正确项均匀分布到 A/B/C/D
（重排 options 并同步改 answer 下标），只动选项顺序，不改题干/解析/答案内容。

用法：python tools/_bank_rebalance.py   （幂等性：重跑会再次洗牌，分布仍均匀但顺序会变）
"""
import json
import random
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "questions"
SEED = 20260930


def dist(qs):
    d = {0: 0, 1: 0, 2: 0, 3: 0}
    for q in qs:
        d[q["answer"]] = d.get(q["answer"], 0) + 1
    return d


def main():
    rng = random.Random(SEED)
    if not ROOT.exists():
        print("题库目录不存在:", ROOT)
        sys.exit(1)
    for f in sorted(ROOT.glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        qs = data["questions"]
        before = dist(qs)
        # 每题随机选一个新位置，但整体强制均匀：把 0..n-1 位置按题目数均分后洗牌分配
        targets = [i % 4 for i in range(len(qs))]
        rng.shuffle(targets)
        for q, t in zip(qs, targets):
            cur = q["answer"]
            if cur == t:
                continue
            opts = q["options"]
            correct = opts[cur]
            # 正确项换到 t 位置，原 t 位置的选项挪到 cur 位置（整体置换）
            opts[cur], opts[t] = opts[t], correct
            q["answer"] = t
        f.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        after = dist(qs)
        name = urllib.parse.quote(f.name)
        print(f"{name}: {before} -> {after}")


if __name__ == "__main__":
    main()
