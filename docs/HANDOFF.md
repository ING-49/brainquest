# HANDOFF — 脑力大冒险 · 项目交接速览

> ## ⏸ 项目暂停中（2026-09-23）
> 完整交接包在 **[`docs/移交/`](移交/)**，按需要查：
> **[00 交接总览](移交/00-交接总览.md)**（先读：现状 / 三条红线 / 恢复开发 5 步）·
> [01 资产与凭据](移交/01-资产与凭据.md)（不在 git 的东西要单独备份、口令从哪取）·
> [02 恢复开发](移交/02-恢复开发.md)（环境含 Python 漂移 / 构建 / 发版 / 部署）·
> [03 待办与路线图](移交/03-待办与路线图.md)（P0 风险 / 已规划未实现 / 不要做清单）·
> [04 上架准备清单](移交/04-上架准备清单.md)（隐私合规 / 素材 / 签名与双通道更新策略）
>
> 本篇是一屏速览。详细工程手册见 [`PLAYBOOK.md`](PLAYBOOK.md)，环境与坑见 [`../AGENTS.md`](../AGENTS.md)。

## 现状
- **版本**：v1.6.10（versionCode 35）· 包名 `com.brainquest.game` · Android 单机 APK + 应用内增量更新
- **用户量**：公网对战服务器上已有真实玩家数据（积分记录 + 云存档），改动勿清库
- **技术栈**：Kotlin 2.0 + Jetpack Compose(M3) + DataStore + OkHttp/WebSocket + kotlinx.serialization

## 已上线功能
| 模块 | 说明 |
|---|---|
| 速算英雄 | 答题战斗闯关（10 科题库 420+ 题 + 真题卷，考研模式高难题；热更包扩至 596 题） |
| 每日挑战 / 错题本 | 艾宾浩斯复习（1/2/4/7/15 天）、错题本到期队列 |
| 小游戏 | **华容道**（6 关经典布局，滑动/点选移动+滑行动画，最少步数记录）· **五子棋**（本地 AI 三档，悔棋回到我上一手前）· **贪吃蛇**（2D，整屏滑动，300ms 起步缓加速）|
| 联机对战 | **远程 = 服务器中立**（v1.6.9：服务器出题+按题库判分+结算 ELO，双方 ready 后自动下发；题库在 /opt/pk/questions，口算/逻辑服务器程序生成）/ 局域网热点（内嵌服务器+UDP 发现，保留房主出题模式） |
| 云存档 | **三重防护（v1.6.9 完整）**：存档码（自动生成只读）+ 身份码（QX+10 位，归属校验，换机输原身份码转移）+ 口令（AES-GCM 加密，服务器只存密文）；`save_del` 删除通道；`save_get` 限流 |
| 隐私合规 | 应用内隐私政策（设置页入口）、明文流量收敛为域名白名单（`network_security_config.xml`）、商店副名「学海星槎」 |
| 更新体系 | 多版本 BQDELTA1 差分补丁链（v1.6.7 发布后共 24 条，覆盖 v1.1.1+）+ 国内多源回退 + GitHub Releases |

## 最近几轮（便于追溯）
- **v1.6.3**：联机页两级返回、答题选项统一绿框/红框+解析、ELO 排行榜（仅快速匹配计分）、云存档上传/下载
- **v1.6.4**：连续匹配第二局提前完成修复（房主路径未重置战斗状态）、排行榜按科目分桶（默认混合）、云存档补存档码输入框
- **v1.6.5**：移除 知识2048/记忆翻牌，新增 华容道/五子棋/贪吃蛇（纯游戏化，靠金币经验接入成长线）
- **v1.6.6**：三个小游戏体验强化——去掉屏幕方向键（改纯滑动+点选）、华容道移动改滑行动画与拖动跟手、棋盘/棋子重做高对比配色、五子棋悔棋语义修复（原来撤错手）、音效扩到 12 种音序（滑动/落子/吃豆/撞击/开局等）、贪吃蛇减速到 300ms/格、设置里的「震动反馈」开关真正生效（原来无一处调用）
- **v1.6.10**：对局断线韧性——断线房间继续、重连 resume 恢复进度续战、在场玩家交卷立即结算（全对立胜、掉线方优势不保留）、单局约束（同身份未完不可开新局，拒绝后自动回原局）、排队版本提示；冒烟扩到 31 项
- **v1.6.9**：远程对战服务器中立化——服务器抽题下发+按题库判分+服务器累计结算（房主发题/自报对错废除，防作弊）；云存档身份码归属绑定（存档码只读自动生成，换设备需原身份码转移）；机器人/冒烟适配（线上 22 项断言）
- **v1.6.8**：修复 gen_rules 热更路径失效（递归扫包，引入以来从未生效）、真题卷 papers.json 走热更、机器人/冒烟脚本版本号改读 manifest、pk_server 日志 python3 -u、老脚本（autoplay/update_demo/pk_play）dump 迁移 ui.py 且 autoplay 补填空题支持
- **v1.6.7**：云存档身份校验（口令 PBKDF2→AES-GCM 信封加密、`save_get` 限流、存档码 8→10 位、`save_del` 删除通道）+ 大学五科热更题包 165 题（`*_p3`）+ 上架技术准备（应用内隐私政策、明文流量白名单、主副名「脑力大冒险 / 学海星槎」、商店图标导出、README 截图补齐）。公网服务器已于 2026-09-25 同步上线；SSH 免密（本机 id_rsa → 服务器 authorized_keys），详见移交 01/03。

## 待办
**已全部收进 [`docs/移交/03-待办与路线图.md`](移交/03-待办与路线图.md)**（分 P0 风险 / P1 已规划未实现 / P2 体验，每条带"从哪开始改 + 怎么验收"）。最要紧的三件：
1. 备份签名 keystore 与服务器玩家数据（`01-资产与凭据.md`）
2. ~~部署 v1.6.7 服务器代码~~ ✅ 已上线（2026-09-25，线上冒烟 19 项全过、公网云存档加密实测通过）
3. 真机两台实测（热点局域网互搜 / 公网对战 / 应用内更新全流程）

## 常用命令
```bash
# 构建 / 安装 / 截图
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
# 发版三步（详见 AGENTS.md / PLAYBOOK §五）
# 1) build.gradle.kts 版本号+1 → ./gradlew clean assembleDebug → cp 到 update-server/apks/
# 2) cd update-server && python release.py      # 差分+manifest+自校验
# 3) GH_EXE=E:/Tools/gh-cli/bin/gh.exe python tools/publish_github.py
# 联机服务器（阿里云 8.148.192.129:8765）
PK_SSH_PASS='<密码>' python tools/deploy_pk_server.py     # 部署（改完 pk_server.py 后）
python tools/pk_server_smoke.py ws://8.148.192.129:8765   # 冒烟 31 项
python tools/pk_guest.py --quick 6 1.6.7                  # 机器人对手（版本必须与 App 一致，否则配不上；PK_URL 指定服务器）
python tools/klotski_verify.py                            # 华容道关卡可解性 BFS 校验
python tools/snake_autoeat.py                             # 贪吃蛇自动追豆（像素识别）
python tools/snake_speed_check.py                         # 贪吃蛇速度/无方向键/返回确认取证
python tools/klotski_anim_check.py                        # 华容道跟手/滑行/落格取证
python tools/gomoku_undo_check.py                         # 五子棋悔棋语义取证
```

## 关键约定（改代码前必读）
- 游戏逻辑用**纯 Kotlin 类 + `var version by mutableIntStateOf(0)`**，界面 `key(version)` 或直接读 version 触发重组
- ⚠️ `pointerInput` 的 key **不要绑 version**：走子会 version++ → 手势协程被重启，`onDragEnd/onDragCancel` 不执行，拖动偏移会永久残留（v1.6.6 踩过）
- 音效统一走 `util/Sfx.kt`：`Sfx.play(context, soundOn, hapticsOn, SfxType.X)`；小游戏音色见 PLAYBOOK §四「小游戏交互规范」
- 记分：`vm.reportBest(key, score)` 是**越大越好**；反向指标（最少步数等）用 `vm.reportBestLow(key, score)`
- 新增页面：`ui/AppRoot.kt` 加路由常量 + import + composable；`ui/HomeScreen.kt` 加入口卡片与最佳成绩标签
- 联机非大厅阶段返回要先回大厅（`BackHandler`），别直接 `popBackStack`
- 云存档上传必须走 `vm.encryptSaveJson(口令)`（SaveCrypto 信封）；服务器**拒绝明文** `save_put`，口令不落盘不上传；`save_get` 每连接 60s 限 6 次
- ⚠️ 明文流量已收敛为白名单（`res/xml/network_security_config.xml`：仅 `8.148.192.129` 与 `10.0.2.2`）——新加 http/ws 域名要同步改这里，否则线上被静默拦截
- 发版前必须 `clean`（zipflinger 死空间会让 APK 虚高）
- ⛔ `tools/_oneshot/` 里的 20 个脚本是**历史一次性脚本**（改动已并入源码），**勿重跑**，见 `tools/_oneshot/README.md`
- 模拟器 AVD：`BrainQuest`，`ANDROID_AVD_HOME=E:/Tools/Android-Studio/avd-home`
