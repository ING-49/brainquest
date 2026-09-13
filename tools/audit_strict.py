"""严格模式审计：统计正确选项为「唯一最长」的题目分布与明细"""
import glob
import json

stats = {"总题": 0, "严格最长": 0, "m>=2": 0, "m>=3": 0}
detail3 = []
for path in sorted(glob.glob("app/src/main/assets/questions/*.json")) + sorted(
    glob.glob("update-server/packs/src/*.json")
):
    d = json.load(open(path, encoding="utf-8"))
    fname = path.replace("\\", "/").split("/")[-1]
    for q in d.get("questions", []):
        if q.get("type", "single") != "single" or len(q["options"]) != 4:
            continue
        stats["总题"] += 1
        lens = [len(o) for o in q["options"]]
        ans = q["answer"]
        others = [l for i, l in enumerate(lens) if i != ans]
        if lens[ans] > max(others):
            stats["严格最长"] += 1
            margin = lens[ans] - max(others)
            if margin >= 2:
                stats["m>=2"] += 1
            if margin >= 3:
                stats["m>=3"] += 1
                detail3.append((fname, q["id"], lens, q["question"][:24], q["options"][ans][:36]))

print(stats)
print("--- 长出≥3字的明细 ---")
for f, qid, lens, question, ans_text in detail3:
    print(f"{f} {qid} {lens} | {question} | {ans_text}")
