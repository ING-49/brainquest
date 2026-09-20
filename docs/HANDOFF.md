# HANDOFF — 脑力大冒险 · 项目交接速览

> 一屏内恢复上下文用。详细手册见 `docs/PLAYBOOK.md`，环境与坑见 `AGENTS.md`。

## 现状
- **版本**：v1.6.6（versionCode 31）· 包名 `com.brainquest.game` · Android 单机 APK + 应用内增量更新
- **用户量**：公网对战服务器上已有真实玩家数据（积分记录 + 云存档），改动勿清库
- **技术栈**：Kotlin 2.0 + Jetpack Compose(M3) + DataStore + OkHttp/WebSocket + kotlinx.serialization

## 已上线功能
| 模块 | 说明 |
|---|---|
| 速算英雄 | 答题战斗闯关（10 科题库 420+ 题 + 真题卷，考研模式高难题） |
| 每日挑战 / 错题本 | 艾宾浩斯复习（1/2/4/7/15 天）、错题本到期队列 |
| 小游戏 | **华容道**（6 关经典布局，滑动/点选移动+滑行动画，最少步数记录）· **五子棋**（本地 AI 三档，悔棋回到我上一手前）· **贪吃蛇**（2D，整屏滑动，300ms 起步缓加速）|
| 联机对战 | 远程快速匹配（ELO 排行榜，**仅快速匹配计分**，按科目分桶）/ 远程好友房间 / 局域网热点（内嵌服务器+UDP 发现） |
| 云存档 | 存档码上传/下载（换设备输码恢复）；口令加密只做了规划（见 PLAYBOOK） |
| 更新体系 | 多版本 BQDELTA1 差分补丁链（21 条）+ 国内多源回退 + GitHub Releases |

## 最近几轮（便于追溯）
- **v1.6.3**：联机页两级返回、答题选项统一绿框/红框+解析、ELO 排行榜（仅快速匹配计分）、云存档上传/下载
- **v1.6.4**：连续匹配第二局提前完成修复（房主路径未重置战斗状态）、排行榜按科目分桶（默认混合）、云存档补存档码输入框
- **v1.6.5**：移除 知识2048/记忆翻牌，新增 华容道/五子棋/贪吃蛇（纯游戏化，靠金币经验接入成长线）
- **v1.6.6**：三个小游戏体验强化——去掉屏幕方向键（改纯滑动+点选）、华容道移动改滑行动画与拖动跟手、棋盘/棋子重做高对比配色、五子棋悔棋语义修复（原来撤错手）、音效扩到 12 种音序（滑动/落子/吃豆/撞击/开局等）、贪吃蛇减速到 300ms/格、设置里的「震动反馈」开关真正生效（原来无一处调用）

## 待办
- [ ] 打 tag 实战验证一次 GitHub Actions 发版流水线（目前发版走 `tools/publish_github.py`）
- [ ] **真机两台实测**（热点局域网互搜 / 公网对战 / 应用内更新全流程）——需用户参与
- [ ] 云存档身份校验（口令加密 + save_get 限流 + 存档码升 10 位）——已规划未实现
- [ ] 内容持续扩充（题库/真题卷，走热更包不用发版）

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
python tools/pk_server_smoke.py ws://8.148.192.129:8765   # 冒烟 16 项
python tools/pk_guest.py --quick 6 1.6.5                  # 机器人对手（PK_URL 指定服务器）
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
- 发版前必须 `clean`（zipflinger 死空间会让 APK 虚高）
- 模拟器 AVD：`BrainQuest`，`ANDROID_AVD_HOME=E:/Tools/Android-Studio/avd-home`
