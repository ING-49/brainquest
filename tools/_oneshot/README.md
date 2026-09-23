# _oneshot —— 历史一次性脚本（勿重跑 ⚠️）

本目录里全是**已经在历史版本上执行过、改动已并入源码与题库**的一次性改写脚本。
它们不是工具，是"当时的施工记录"。

## 为什么放在这里
这些脚本的模式是「读源文件 → `assert 旧代码片段 in src` → 字符串替换 → 覆盖写回」，例如：

```python
open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleScreen.kt', 'w', encoding='utf-8').write(src)
```

并且假设**工作目录是仓库根目录**。在已经改过的当前代码上重跑：

- 轻则 `assert` 失败后中断（无害但会让人误以为是 bug）；
- 重则**重复插入代码 / 二次改写题库 JSON**，污染 `app/src/main/assets/questions/*.json`、
  `update-server/packs/src/*.json` 或 Kotlin 源码，需要 `git checkout` 才能还原。

## 想找回某个改动是怎么做的
用 git 历史而不是重跑脚本，例如：

```bash
git log --oneline -- tools/_oneshot/v160_ebbinghaus.py   # 找到对应提交
git show <commit> --stat                                  # 看那次改了什么
git log -p -- app/src/main/java/com/brainquest/game/data/PlayerState.kt  # 看文件演化
```

## 清单（20 个）
| 脚本 | 当时的用途 |
|---|---|
| `apply_fixes.py` + `fixes_advmath.py` / `fixes_batch2.py` / `fixes_batch5.py` / `fixes_batch6.py` | 按 qid 修复表批量改题库选项与答案（后四个是数据表，被 `apply_fixes.py` import，必须同目录） |
| `fix_options_balance.py` | 干扰项长度自动平衡（对全量题库 JSON 覆盖写） |
| `add_dedup.py` | 题库去重 + 相关状态字段 |
| `add_fill_type.py` / `add_fill_ui.py` | 新增填空题类型与填空作答 UI |
| `fix_mathgen.py` | 数学生成器修正 |
| `v150_part1.py` | v1.5.0 第一批改动（PlayerState / AppViewModel / SettingsScreen） |
| `v151_matched_fix.py` | v1.5.1 匹配修复 |
| `v160_ebbinghaus.py` | v1.6.0 艾宾浩斯复习 + 考研闯关 |
| `pk_flow_v2.py` / `pk_three_modes.py` / `pk_matched_fix.py` | 联机页流程/三形态/匹配改写（PK 页面与 PkClient） |
| `fix_pk_screen.py` | 联机页修正 |
| `fix_profile.py` / `upgrade_profile.py` / `userify_settings.py` | 个人页与设置页文案/结构改写 |

## 现役工具在哪
回归与验证脚本仍在 `tools/` 顶层（`autoplay.py`、`update_demo.py`、`ui.py`、
`klotski_verify.py`、`snake_autoeat.py`、`snake_speed_check.py`、`klotski_anim_check.py`、
`gomoku_undo_check.py`、`pk_guest.py`、`pk_server_smoke.py`、`deploy_pk_server.py`、
`dev_relay.py`、`publish_github.py`、`audit_options.py`、`audit_strict.py`、`pk_play.py`）。
索引见 `docs/PLAYBOOK.md` §三 与 `AGENTS.md` 工具清单。
