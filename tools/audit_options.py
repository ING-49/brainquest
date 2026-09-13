"""审计题库：找出「正确选项显著长于其他选项」的破绽题。
用法：python tools/audit_options.py [阈值，默认4]
输出标记列表（供批量改写），并统计各文件分布。
"""
import glob
import json
import sys

TH = int(sys.argv[1]) if len(sys.argv) > 1 else 4  # 正确项比次长项多出的字符数

flagged = []
total = 0
for path in sorted(glob.glob("app/src/main/assets/questions/*.json")) + sorted(
    glob.glob("update-server/packs/src/*.json")
):
    d = json.load(open(path, encoding="utf-8"))
    for q in d.get("questions", []):
        total += 1
        opts = q["options"]
        if q.get("type", "single") != "single" or len(opts) != 4:
            continue
        lens = [len(o) for o in opts]
        ans = q["answer"]
        others_max = max(l for i, l in enumerate(lens) if i != ans)
        if lens[ans] - others_max >= TH:
            flagged.append((path.split("/")[-1].split("\\")[-1], q["id"], lens, q["question"][:30], opts[ans]))

by_file = {}
for f, qid, lens, *_ in flagged:
    by_file[f] = by_file.get(f, 0) + 1
print(f"总题数 {total}，破绽题（正确项比次长项长 ≥{TH} 字）: {len(flagged)}")
for f, n in sorted(by_file.items(), key=lambda x: -x[1]):
    print(f"  {f}: {n}")
print("--- 明细 ---")
for f, qid, lens, question, ans_text in flagged:
    print(f"{f} {qid} 长度{lens} | {question} | 正确项: {ans_text[:40]}")
