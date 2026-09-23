"""应用修复表到题库 JSON：按 qid 定位题目，替换 options/answer。"""
import glob
import json
import sys

import fixes_advmath
import fixes_batch2

TABLES = {**fixes_advmath.FIXES, **fixes_batch2.FIXES}

applied = 0
missing = []
for path in sorted(glob.glob("app/src/main/assets/questions/*.json")) + sorted(
    glob.glob("update-server/packs/src/*.json")
):
    d = json.load(open(path, encoding="utf-8"))
    changed = False
    for q in d.get("questions", []):
        if q["id"] in TABLES:
            fix = TABLES[q["id"]]
            q["options"] = fix["options"]
            q["answer"] = fix["answer"]
            applied += 1
            changed = True
    if changed:
        json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

print(f"已应用 {applied} 题修复（修复表共 {len(TABLES)} 条）")
if missing:
    print("未定位到:", missing)
