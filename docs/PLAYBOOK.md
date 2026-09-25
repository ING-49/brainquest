# PLAYBOOK — 「脑力大冒险」完整工程线路与作业手册

> 本文档记录本项目从零到可发版的**完整已验证线路**、全部可用工具、以及踩坑详表。
> 速查版见根目录 `AGENTS.md`。

## 一、项目概况

- **应用**：脑力大冒险（com.brainquest.game）——益智学习手游合集，单机离线可玩 + 公网联机对战
- **技术栈**：Kotlin 2.0.20 + Jetpack Compose (BOM 2024.09) + Material 3 + DataStore + OkHttp；minSdk 26 / target 34
- **仓库**：https://github.com/ING-49/brainquest（public，Conventional Commits）
- **当前版本**：v1.6.9 (versionCode 34)
- **更新体系**：自研 BQDELTA1 增量差分 + GitHub Releases 托管 + GitHub Actions 自动发版

## 二、已验证的完整线路

```
【环境】JDK17 + SDK + 模拟器 AVD（一次性，见 AGENTS.md 环境表）
   ↓
【开发】Kotlin/Compose 编码 → gradlew assembleDebug
   ↓                                  （失败先查 PLAYBOOK §六 踩坑表）
【验证】adb install -r → am start → adb exec-out screencap 截图
        → tools/autoplay.py 自动答题回归 → tools/update_demo.py 更新回归
   ↓
【发版】① build.gradle.kts versionCode+1 / versionName 升级
        ② gradlew assembleDebug → cp APK 到 update-server/apks/BrainQuest-v<ver>.apk
        ③ cd update-server && python release.py
           （打内容包 zip + 生成 BQDELTA1 差分 + manifest.json + 合成自校验）
        ④ python tools/publish_github.py
           （创建 GitHub Release + 上传资产 + latest 地址自校验）
   ↓
【分发】App 内「设置 → 检查更新」→ 增量补丁（几百 KB）→ 合成 → 签名校验 → 系统安装
        全量兜底：manifest 无匹配补丁时自动走全量下载
```

### 关键地址
| 用途 | 地址 |
|---|---|
| 更新清单（永远最新） | `https://github.com/ING-49/brainquest/releases/latest/download/manifest.json` |
| 仓库 | https://github.com/ING-49/brainquest |
| 模拟器访问 GitHub 困难时 | `python tools/dev_relay.py` 后填 `http://10.0.2.2:8000` |
| 本地静态服务器（真机同 Wi-Fi） | 双击 `启动更新服务器.bat` |

### 增量更新链路（数据流）
```
release.py: old.apk + new.apk → BQDELTA1 补丁（滚动哈希块匹配 → COPY/LIT 操作流）
          → Python 端用同算法重放一遍，SHA-256 必须等于 new.apk，否则拒绝发布
App:  下载补丁 → SHA-256 校验 → 读已安装 base.apk → 重放操作流合成新 APK
      → SHA-256 校验 == manifest.fullApkSha256 → 签名比对 → PackageInstaller
```

## 三、工具清单与用法

| 工具 | 用法 | 场景 |
|---|---|---|
| `tools/autoplay.py` | 进入战斗页后运行；自动读题口算、点正确选项、处理胜负对话框 | 战斗玩法回归测试、快速解锁关卡 |
| `tools/update_demo.py` | 驱动完整更新演示（检查更新→热更→增量→安装），每步截图到 .shots/ | 更新系统回归 |
| `tools/dev_relay.py` | `python tools/dev_relay.py 8000` | 模拟器无法直连 GitHub 时做桥 |
| `tools/publish_github.py` | `GH_EXE=<gh路径> python tools/publish_github.py` | 发布 update-server 产物到 Releases |
| `update-server/release.py` | `python release.py`；`--packs-only` 只发内容包 | 每次发版第一步之后 |
| `update-server/delta.py` | 被 release.py 调用；`verify()` 独立可测 | 差分编码 |
| `tools/klotski_verify.py` | `python tools/klotski_verify.py` | 华容道 6 关可解性 BFS 校验（含最少步数） |
| `tools/snake_autoeat.py` | `python tools/snake_autoeat.py` | 贪吃蛇自动追豆（像素识别 + 暂停分步，转向用棋盘内滑动） |
| `tools/snake_speed_check.py` | 同上 | 贪吃蛇速度/无方向键/速度档/返回确认取证 |
| `tools/klotski_anim_check.py` | 同上 | 华容道拖动跟手/过阈值滑行/精确落格/步数取证 |
| `tools/gomoku_undo_check.py` | 同上 | 五子棋悔棋语义 + 思考期取消 + 返回确认取证 |
| `tools/ui.py` | 被上面几个脚本 import | uiautomator 文本定位/点击/滑动/截图（`tap_text`/`swipe`/`shot`） |
| `tools/pk_guest.py` | `python tools/pk_guest.py --quick [答对数] [版本]` | 联机机器人对手（PK_URL 指定服务器） |
| `tools/pk_server_smoke.py` | `python tools/pk_server_smoke.py ws://8.148.192.129:8765` | PK 服务器冒烟（22 项断言） |
| `tools/deploy_pk_server.py` | `PK_SSH_PASS='<密码>' python tools/deploy_pk_server.py` | 部署/更新公网 pk_server（systemd 常驻） |
| `启动对战服务器.bat` | 双击 | 局域网 PC 端 PK 服务器 |
| `tools/_oneshot/*.py`（20 个） | ⛔ **不要运行** | 历史一次性改写脚本（`fix_*`/`add_*`/`v1xx_*`/`upgrade_*` 等），改动已并入源码；重跑会二次改写源码或题库 JSON。见 `tools/_oneshot/README.md` |

## 四、内容与功能速记

- **题库 schema**（`assets/questions/*.json` 与热更包同构）：
  `{"subject","version","questions":[{"id","subject","difficulty"(1-5),"type","question","options"[4],"answer"(下标),"explanation","tags"}]}`
- **加题**：直接改 assets 里的 JSON（随 APK 发）或在 `update-server/packs/src/` 新建 JSON（version+1 → release.py --packs-only → publish_github，走热更）
- **小游戏**（v1.6.5 起）：速算英雄（答题战斗）、`game/klotski/`（华容道，关卡 Kotlin 内置，最优步数由 `tools/klotski_verify.py` BFS 校验）、`game/gomoku/`（五子棋，本地启发式 AI）、`game/snake/`（贪吃蛇 2D）；三者纯逻辑类 + `version` 计数器，无资产依赖
- **小游戏清单（v1.6.5 / 体验强化 v1.6.6）**：速算英雄（答题战斗闯关）· 华容道（6 关，滑动/点选移动 + 滑行动画，记录最少步数）· 五子棋（本地 AI 三档，记录总胜场/最佳连胜）· 贪吃蛇（最高分，300ms 起步缓加速）· 联机对战
- 成就联动：智取华容 / 棋逢对手 / 连战连捷 / 蛇行三十；奖励统一走 `vm.reportBest(Low) + addCoins + addXp`

### 小游戏交互规范（v1.6.6 定稿，改小游戏先读这条）
- **手势优先，不做屏幕方向键**：棋盘类用滑动（华容道滑棋子、贪吃蛇整屏滑动）；大块不好滑时支持"先点一下选中，再在棋盘空白处滑动"；自动化用 `adb shell input swipe`（`input motionevent` 合成的 UP 可能不被 Compose 收尾，仅适合中途截图取证）
- **移动必须有过程动画**：位置变化统一交给一个动画值（`animateDpAsState`，落格 150ms、拖动跟手 70ms），不要"瞬移"。拖动偏移用普通 state 直接赋值，位置 = 格位 + 偏移，**不要再开动画协程**（多协程抢同一动画值会互相取消）
- **棋盘与棋子必须拉开对比**：浅底深子或深底亮子，棋子加描边，选中态用高对比描边（如琥珀 3dp），**不要用半透明白蒙层**
- **音效与事件一一对应**（`util/Sfx.kt`，ToneGenerator 音序，无音频资源）：

| 事件 | SfxType |
|---|---|
| 选中棋子 / 棋子滑动 / 我落子 / 电脑落子 | `SELECT` / `MOVE` / `PLACE` / `PLACE_AI` |
| 吃豆 / 撞墙咬自己 | `EAT` / `CRASH` |
| 开局 / 胜利 / 失败 | `START` / `WIN` / `LOSE` |
| 答题对错 / 点击 | `CORRECT` / `WRONG` / `CLICK`（沿用原单音） |

  同类型 60ms 内节流；`Sfx.play(context, soundOn, hapticsOn, type)` 同时给触感（滑动/选中无、落子/吃豆轻、撞击/胜负强）
- **悔棋语义 = 回到我上一手之前**（对方应的那手一并撤销），撤完必轮到自己；AI"思考中"悔棋 = 取消那次落子
- **对局中返回要确认**：先弹「退出这一局？」；华容道是两级返回（对局 → 选关 → 离开）
- **已下线**：知识2048 / 记忆翻牌（v1.6.5 移除，`assets/pairs/` 一并删除；老存档里的 `g2048_*`/`memory_*` 记录保留但不再展示，无需迁移）
- **出题规则热更（v1.6.8 修复）**：`GenRulesConfig` 递归扫 `content/packs/` 任意子目录的 gen_rules.json（UpdateManager 解包到 `<包id>/` 子目录，旧版只查平铺路径导致热更从未生效）；`PapersScreen` 同法支持 papers.json 热更
- **科目注册**：`data/question/Question.kt` 里的 `object Subjects`（10 个科目的名称与 `all` 列表）+ `data/Achievements.kt` 的 `subjectKey()`（科目 → 存档 key 前缀）+ 科目卡颜色（`ui/LevelsScreen.kt` 的 `subjectColor`）
- **存档**：DataStore 单键 JSON（PlayerState），换版本自动兼容（新字段有默认值）
- **考研模式**：`PlayerState.hardMode` → 设置开关。每日挑战走 `pickDaily`（开=大学五科 60% 真题+40% 难度4，关=基础五科难度2）；闯关/战斗 v1.6.0 起同样联动（`BattleState(subject, level, hardMode=…)`，非数学/逻辑科目走 `pickKaoyanBattleExcluding` 真题+高难并去重），战斗页标题带 🎓 徽标
- **错题本艾宾浩斯复习**（v1.6.0）：`WrongEntry.stage/nextReviewAt` + `PlayerState.reviewIntervalMs`（答错当天→1→2→4→7→15 天）；复习答对 stage+1，答错退回 stage0 且 60s 后重练；`AppViewModel.dueReviewQuestions()` 汇总到期题，错题本页顶部「今日待复习」卡片一键进入复习

## 五、发版检查单

- [ ] `app/build.gradle.kts` versionCode +1、versionName 升级
- [ ] `gradlew assembleDebug` 成功
- [ ] APK 复制到 `update-server/apks/BrainQuest-v<版本>.apk`
- [ ] `python release.py`（末尾必须出现 `[verify] ✓`，自校验不过禁止发布）
- [ ] `python tools/publish_github.py`（末尾出现 `✓ GitHub 托管生效`）
- [ ] 浏览器验证 `releases/latest/download/manifest.json` 指向新版本
- [ ] 真机/模拟器实际走一次「检查更新 → 增量更新」
- [ ] git commit + push

## 五之一、出题规则热更（gen_rules.json）
`assets/config/gen_rules.json` 可由热更包同名文件覆盖（`filesDir/content/packs/gen_rules.json`），改手感无需发版：
- `fillChance`：填空题出现概率（%）；`fillMaxDifficulty`：填空题最高难度
- `baseDamage / perLevelDamage / comboDamage`：战斗伤害公式参数；`fillTimeBonus`：填空题加时（秒）
发布方式：把 gen_rules.json（version+1）放进内容包 → `release.py --packs-only` → publish → App 内更新内容包即生效（v1.6.8 起路径修复后才真正生效）

## 五之二、版本策略
- **单机**：离线完全可玩，不做强制更新
- **联机对战**：强制双方同版本（握手携带 versionName，内嵌服务器/房主端校验，不一致拒绝加入并提示双方更新）
- **补丁链**：apks/ 内历史 APK 保留（勿删，补丁链依赖），debug 签名旧版隔离在 _hold/；每次发版自动重建「全部历史版本 → 最新」补丁链
- 目标：尽量让所有用户都在最新版，减少多版本维护；联机是版本收敛的主要动力

## 五之三、更新体系增强（v1.4.1 起）
- **多版本补丁链**：`release.py` 为 `apks/` 中每个签名兼容的历史版本生成 →最新补丁（幂等：已验证的补丁重跑直接复用），manifest.patches 全列；App 按自身 versionCode 自动匹配
- **国内多源回退**：`UpdateManager.fetchManifestMulti` 按序尝试 [GitHub直连, gh-proxy镜像, ghfast镜像]（用户自定义地址时独占），成功源记入 activeBase 供后续补丁/APK/内容包下载使用
- 镜像地址格式：`https://gh-proxy.com/` + GitHub 完整链接（ghfast.top 同）

## 五之四、联机对战（阶段一已实现）

架构（v1.4.0 起双形态）：
- **App 内嵌服务器**（`net/EmbeddedPkServer.kt`，Java-WebSocket 库）：房主手机即服务器，支持热点离线对战
- **独立服务器**（`update-server/pk_server.py`）：跑在电脑/公网 VPS 上
协议统一：create/join → start(题目由房主本地选题整包下发) → answer(逐题转发对手) → finish(双方) → 判定(答对数、平局比用时) → result。加入方 PkClient 对两种服务器完全透明。
局域网发现：房主 UDP 信标广播（每秒，端口 8766，含房间码/昵称/TCP 端口），加入方监听列出房间；AP 隔离网络广播被挡 → 手动输 IP 兜底。
验证状态（模拟器实测）：内嵌服务器完整对战一局（创建→加入→10题同答→判定→结算 3:8 正确呈现）✓；UDP 信标被主机侧监听接收 ✓。
待真机两台实测：同 Wi-Fi 搜索发现、热点模式（模拟器 NAT 不转发广播，无法模拟真机行为）。

## 五之五、联机对战服务器（远程部署）

三种形态：
1. **App 内嵌服务器**（v1.4.0 起）：房主手机即服务器，热点模式完全离线可玩（`net/EmbeddedPkServer.kt`）
2. **局域网 PC 服务器**：`启动对战服务器.bat`（pk_server.py 跑电脑，同 Wi-Fi 手机对战）
3. **公网服务器**（随时随地对战）：同一份 pk_server.py 部署到云上

### 阿里云部署实录（已上线：ws://8.148.192.129:8765）
- 选型：轻量应用服务器 · 国内地域（延迟最低）· 镜像 Ubuntu 24.04 LTS · 最低套餐 2核2G · **纯 IP+非标端口（8765）无需 ICP 备案** · 新用户活动价常低至 ¥38~99/年
- 防火墙规则：应用类型=自定义 / 协议=自定义 TCP / 端口范围=8765/8765 / 来源=0.0.0.0/0
- 一键部署：`python tools/deploy_pk_server.py`（SSH：装 python3+websockets → 上传 pk_server.py → systemd 常驻 pk-server.service，开机自启+崩溃拉起）
- 运维：`systemctl status/restart pk-server`；服务无状态，换机迁移=改 App 联机页地址
- App 联机页填：`ws://8.148.192.129:8765`

### 远程模式（v1.6.9 起服务器中立：服务器出题+判分，见下）
- **在线人数**：服务器实时广播 `{"t":"online","players":N,"waiting":K,"rooms":M}`（连接/断开/入队/出队时触发）；App 联机页远程页签维持空闲长连接显示「🟢 在线 N 人」
- **快速匹配**：`quick_match` 入队，服务器只配对**同版本**玩家；甲方收 `created`+`peer_joined`、乙方收 `joined` —— 客户端状态机零改动，复用现有确认→倒计时→对战→结算全流程
- 大厅双页签：🌐 远程（默认，在线人数/快速匹配/科目选择/好友房间）+ 🏠 局域网（创建/搜索/手动直连，原样保留）
- 服务器地址**不在界面显示**（默认公网唯一地址）；长按「在线人数」行弹出隐藏编辑对话框（调试/自建用），修改后 1.5s 防抖自动重连；旧默认（10.0.2.2）打开时自动迁移为公网地址
- PK 战斗体验（v1.6.2 定稿）：答完题卡内显示对错与正确答案，停留 0.75s **自动进下一题**（无"下一题"按钮，最后一题自动交卷）；顶部双方进度「🧑 我 x/10」｜「对手 x/10」实时更新；我方完成页上下布局显示自己成绩+对手进度；双方完成出结算页
- 冒烟测试：`python tools/pk_server_smoke.py ws://8.148.192.129:8765`（在线查询/版本隔离/配对/服务器出题/服务器判分/整局/取消/ELO/不计分/排行榜/云存档加密信封/身份归属/明文拒绝/删除/限流，22 项断言）
- 机器人对手：`python tools/pk_guest.py --quick [答对数] [版本]` 或 `python tools/pk_guest.py <房间码>`（版本默认读 manifest；PK_URL 指定服务器）

### ELO 与排行榜（v1.6.3 起）
- **仅快速匹配计分**：配对房间 `ranked=true`；好友房间（create/join）与局域网/热点对战不计分（结算页标注「不计分」）
- ELO：初始 1000、K=32；服务器持久化 `/opt/pk/ratings.json`（重启不丢）
- 结算消息 `rating:{ranked,my,delta,peer}`；App 结算页显示「🏅 积分 xxx（±n）」
- 排行榜：`{"t":"leaderboard","name":昵称}` → Top10 + 我的排名；联机页远程页签常驻「🏆 排行榜」入口

### 云存档（v1.6.3 起；v1.6.7 加密；v1.6.9 身份归属）
- 服务器：`save_put`/`save_get`/`save_del`，存 `/opt/pk/saves/<码>.json`；存档码 10 位（BQ+8 位大写字母数字，去除易混字符），旧 8 位码兼容（服务器接受 ≤24 位）
- App：设置页「☁️ 云存档」卡片——存档码 + 口令两个输入框；上传**必须设口令（≥4 位）**，留空码自动生成；换设备输同一存档码与口令「下载存档」并确认覆盖本地
- **加密（v1.6.7）**：`util/SaveCrypto.kt` 口令 PBKDF2WithHmacSHA256（盐 16B、6 万次迭代）→ AES-256-GCM，密文封装 `{"fmt":"BQENC1","salt","iters","iv","ct"}` 信封后上传；**服务器只见密文**，口令不落盘不上传，是唯一凭证（丢了无法恢复云端存档）
- **兼容**：无 `fmt` 字段的旧明文存档仍可下载（App 端检测后免口令直接导入，导入会把 PlayerState 整体覆盖——**含 pkServerUrl 等设置**）；新上传一律信封，服务器 `save_put` **拒绝明文**
- **服务器加固**：`save_get` 每连接 60s 限 6 次（防暴力试码，超限回 `error:"too many"`）；单档上限 256KB；`save_del` 删除云端存档（隐私政策的数据删除通道）
- **身份归属（v1.6.9）**：App 首启生成身份码 `QX`+10 位（`PlayerState.identity`，永久固定不可改）；服务器 `save_owners.json` 绑定 存档码↔身份码——put/get/del 均校验归属，猜中存档码也拿不到/覆盖不了；换设备恢复输「原存档码+原身份码+口令」即完成归属转移；无归属记录的旧存档首次操作自动认领
- ⚠️ 部署注意：**旧版 App（≤1.6.6）对新服务器上传会被拒**（提示更新 App），下载不受影响；新版 App 对旧服务器完全正常（旧服务器原样存信封）
- 独立短连接（`PkClient` idle 模式），操作完成即关闭

### 云存档身份校验（v1.6.7 已实现，原规划见 git 历史）
实现要点：口令 PBKDF2→AES-GCM 信封加密 + `save_get` 限流 + 存档码 8→10 位 + `save_del`（见上节）。
未做的备选：只读分享码（低优先级）；账号体系（成本高，等真实用户量再评估）。

### 排行榜按科目（v1.6.4 起）
- 评分按科目分桶（`ratings[科目][昵称]`）；旧扁平数据自动迁移进「混合」桶
- 快捷匹配携带所选科目，配对房间记录**房主科目**（出题与计分同源）；好友房间/局域网不计分
- App 排行榜对话框：科目 chips（🎲 混合默认 + 10 科目），打开默认混合榜，点科目切换查询

### 选项答题反馈（v1.6.3 定稿，全 App 统一）
- 共享组件 `ui/QuizOptionList.kt`：正确项**绿框 ✔**；选错时错选项**红框 ✘**；解析随最后一个选项下方展示
- 适用：闯关战斗（支持道具排除项）、每日挑战/错题复习（QuizRunner）、联机对战

### 联机页返回行为（v1.6.3）
- 返回键两级：非大厅阶段（等待/匹配/对战/结算）先取消会话回联机大厅；大厅再返回才离开页面（含系统返回键 BackHandler）

## 六、踩坑详表（现象 → 根因 → 解法）

| # | 现象 | 根因 | 解法 |
|---|---|---|---|
| 1 | Kotlin 编译崩 `IllegalArgumentException: 25.0.2` | Studio 自带 JBR 是 Java 25，Kotlin 2.0 编译器不认识 | 用独立 JDK 17（E:\Tools\jdk-17.0.20.1+1），JAVA_HOME setx |
| 2 | 依赖解析失败 `Remote host terminated the handshake` | dl.google.com 被墙 | settings.gradle.kts 配阿里云镜像（已配） |
| 3 | Gradle 提示版本/Java 不兼容 | Gradle 8.4 不支持 JDK 21+ | wrapper 升 8.7 |
| 4 | 编译错 `Unclosed comment` | Kotlin 块注释内写 `/*.json` 触发嵌套注释 | 注释里避免 `/*` 字样 |
| 5 | `onSuccess { it }` 字段全 Unresolved | runCatching 块最后一句是赋值（Unit） | 块内最后显式返回值 |
| 6 | `download(...)` 报 lambda 类型错 | 尾随 lambda 绑定到最后一个参数 | 函数参数用命名传参 |
| 7 | 游戏界面整体冻结、答案点了没反应 | 游戏状态类是普通 Kotlin 类，字段变更不触发重组 | 状态类字段改用 mutableStateOf 系；UI 读取 version 计数 |
| 8 | 关卡解锁状态滚动后错乱 | LazyColumn item 内顺序累加变量 | remember 在列表外预计算 |
| 9 | 更新报 CLEARTEXT not permitted | Android 9+ 禁明文 HTTP | manifest 加 usesCleartextTraffic="true" |
| 10 | 更新报 NetworkOnMainThreadException | OkHttp 跑在组合协程（主线程） | scope.launch(Dispatchers.IO) |
| 11 | ToneGenerator 常量编译错 | TONE_PROP_KEY/PIP 不存在 | 用 TONE_PROP_ACK/BEEP/BEEP2/NACK 等；要不同音高用 `TONE_DTMF_0..9`（音序 = 多个 Note 配 postDelayed，见 v1.6.6 的 Sfx.kt） |
| 12 | 增量补丁报 Stream is not in the BZip2 format / Stream closed | python-bsdiff4 的 bzip2 流串联解析 + Windows 32 位 long 溢出缺陷 | 弃用 bsdiff4，自研 BQDELTA1（COPY/LIT 操作流） |
| 13 | `adb shell uiautomator dump /sdcard/ui.xml` 输出落在本机 | Git Bash 把 /sdcard 转成本地路径 | 写 `//sdcard//ui.xml` 或用 `adb exec-out` |
| 14 | 拉取的 APK 哈希不对 | `adb shell cat` 二进制被 CRLF 损坏 | `adb exec-out run-as <pkg> cat <path>` |
| 15 | 安装确认弹窗弹不出来 | 上一次"App installed."界面未关，新意图被路由到旧安装器任务 | 先点 Done 关掉旧界面；logcat 可见 onActivityRestartAttempt |
| 16 | App 内 GitHub 检查更新超时（模拟器） | 模拟器流量不走宿主机代理，直连不了 GitHub | 跑 tools/dev_relay.py，App 填 10.0.2.2:8000 |
| 17 | UI 自动化点击频繁落空 | 应用冷启动慢/页面未就绪即点击 | 循环等待目标文本出现后再点；导航避免用返回键连按（会退出应用） |
| 18 | 更新装完停在安装器界面 | Android 后台启动限制（BAL）：安装器在前台时 Receiver 无法直接拉起 Activity | Receiver 先尝试拉起，失败则发"更新完成"通知（点通知回游戏）；另需 POST_NOTIFICATIONS 权限 |
| 19 | `adb shell input text` 报 NullPointerException | 该命令不支持中文字符 | 中文输入用真机键盘；自动化测试用英文/数字输入 |
| 20 | 跨签名的补丁补丁无法安装 | 合成 APK 与已装 APK 签名不一致时系统拒绝 | release.py 已加签名比对守卫：签名迁移版本自动跳过差分只发全量 |
| 21 | 发版 APK 体积虚高（16.9MB 应为 3.8MB） | zipflinger 增量打包在旧包基础上改写，被删条目留死空间 | 发版前 `gradlew clean assembleDebug`（或删 APK 重打） |
| 22 | 混淆后联机/更新失效风险 | R8 裁剪 serializer/WebSocket 类 | proguard-rules.pro 加 kotlinx.serialization + Java-WebSocket + Question 模型 keep 规则（已配） |
| 23 | 联机页输地址时 App 崩溃 | 地址输到一半（如 `ws://10.0.2.2:28`）防抖重连触发，OkHttp `Request.Builder().url()` 对残缺 URL 抛 IllegalArgumentException，协程内未捕获 | `PkClient.connect()` 加 `validPkUrl()` 前置校验 + try/catch 兜底，无效地址发 Error 事件不发连接 |
| 24 | 部署新 pk_server 后新功能没生效 | systemd `enable --now` 对**已运行**的服务不会重启，线上跑的还是旧代码 | 部署脚本改 `enable + restart`；手动更新用 `systemctl restart pk-server` |
| 25 | 小游戏最佳成绩记反了 | `vm.reportBest(key, score)` 语义是「越大越好」，而步数/用时类指标越小越好（早期记忆翻牌就写成了记最大步数） | 反向指标统一用 `AppViewModel.reportBestLow(key, score)`（华容道最少步数即用它） |
| 26 | 华容道棋子拖完停在半格，重开也回不去 | 手势 `pointerInput(b.id, version)` 的 key 含 `version`：走子 `version++` → 手势协程被重启，`onDragEnd/onDragCancel` 永不执行，拖动偏移永久残留 | 手势 key 只绑棋子 id；拖动偏移用普通 state，位置 = 格位 + 偏移统一交给 `animateDpAsState` 一个动画值（不再另开动画协程） |
| 27 | 五子棋悔棋撤错手（只撤电脑那颗 / 或连上轮自己那颗一起撤） | `undo()` 奇偶判断写反：玩家手是奇数手、AI 手是偶数手 | 偶数手（AI 刚应过）撤 2 手、奇数手撤 1 手 → 永远回到"玩家落子之前"，撤完必轮到玩家 |
| 29 | 后来人误跑 `tools/fix_*.py` / `tools/v1xx_*.py`，源码或题库被二次改写 | 这些是历史一次性脚本（读源码→断言行→字符串替换→覆盖写回），改动早已并入源码，但当时和现役工具混放在 `tools/` 顶层，没有任何警示 | 统一移入 `tools/_oneshot/` 并加 README 警告；想追改动看 git 历史而不是重跑脚本 |
| 30 | 新域名 http/ws 请求线上全部失败（模拟器/本地却正常） | v1.6.7 起 `usesCleartextTraffic` 已换成 `network_security_config.xml` 白名单（仅 8.148.192.129 与 10.0.2.2），白名单外域名明文流量被**静默拦截** | 新增 http/ws 域名同步改 `res/xml/network_security_config.xml`；或上 TLS |
| 31 | 恢复云存档后联机/更新地址「莫名」变回默认 | 导入存档 = 整份 PlayerState 覆盖（含 pkServerUrl/updateServerUrl/cloudCode），旧档里的设置会一起回来 | 属既定语义（整档迁移）。受影响时到联机页长按在线人数行改回服务器地址 |
| 28 | 改小游戏配色后像素识别脚本全失效 | 脚本按 RGB 阈值识别棋盘/棋子；且暂停时棋盘上有 40% 黑蒙层（观察色 = 原色 × 0.6） | 脚本改自适应：棋盘取屏幕最高频色的包围盒；棋子色同时匹配原色与 ×0.6 变体（见 tools/snake_autoeat.py） |

## 七、后续可做事项

- [x] release 正式签名（keystore.properties + signingConfig；正式签名历史 APK 均在 apks/ 供补丁链使用）
- [x] 题库批量扩充（8 个题库 JSON 共 420 题 + 真题卷 papers.json + 干扰项质量审计；数学口算/逻辑推理由 MathGenerator 程序化生成，合计 10 科目）
- [x] 考研模式扩展到闯关、错题本艾宾浩斯复习（v1.6.0 完成）
- [x] README 补充截图与 Release 链接
- [ ] 打 tag 实战验证一次 Actions 发版流水线（workflow 已就绪；目前发版走 tools/publish_github.py 本地发布）
- [x] v1.6.7/1.6.8 服务器代码已上线（2026-09-25，SSH 免密部署，线上冒烟 19 项全过；systemd 用 `python3 -u` 使对局日志落 journalctl）
- [ ] 真机两台实测（热点局域网互搜 / 公网 8.148.192.129 对战 / 应用内更新全流程）
- [x] 联机随机匹配（v1.6.1）、ELO 排行榜 + 云存档（v1.6.3，仅快速匹配计分）
- [x] 小游戏体验强化（v1.6.6：手势化/动效/配色/音效/悔棋语义/减速）

## 八、记录约定

- 环境变更（路径/版本/账号）→ 同步更新本文件与 `AGENTS.md`
- 新踩的坑 → 补进 §六 详表（现象/根因/解法三段式）
- 新工具 → 补进 §三 工具清单
