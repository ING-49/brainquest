# PLAYBOOK — 「脑力大冒险」完整工程线路与作业手册

> 本文档记录本项目从零到可发版的**完整已验证线路**、全部可用工具、以及踩坑详表。
> 速查版见根目录 `AGENTS.md`。

## 一、项目概况

- **应用**：脑力大冒险（com.brainquest.game）——益智学习手游合集，单机
- **技术栈**：Kotlin 2.0.20 + Jetpack Compose (BOM 2024.09) + Material 3 + DataStore + OkHttp；minSdk 26 / target 34
- **仓库**：https://github.com/ING-49/brainquest（public，Conventional Commits）
- **当前版本**：v1.1.1 (versionCode 4)
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
| `启动更新服务器.bat` | 双击 | 本地/局域网静态文件服务 |

## 四、内容与功能速记

- **题库 schema**（`assets/questions/*.json` 与热更包同构）：
  `{"subject","version","questions":[{"id","subject","difficulty"(1-5),"type","question","options"[4],"answer"(下标),"explanation","tags"}]}`
- **加题**：直接改 assets 里的 JSON（随 APK 发）或在 `update-server/packs/src/` 新建 JSON（version+1 → release.py --packs-only → publish_github，走热更）
- **配对数据**：`assets/pairs/merge_*.json`（2048 词对/算式对，8 级）、`memory_sets.json`（翻牌知识集）
- **科目注册**：`data/question/Subjects.kt`（名称/emoji/描述）+ `data/Achievements.kt` subjectKey + 科目卡颜色（ui/LevelsScreen.kt subjectColor）
- **存档**：DataStore 单键 JSON（PlayerState），换版本自动兼容（新字段有默认值）
- **考研模式**：`PlayerState.hardMode` → 设置开关 → `QuestionBank.pickDaily(count, hardMode)`（开=大学五科难度4，关=基础五科难度2）

## 五、发版检查单

- [ ] `app/build.gradle.kts` versionCode +1、versionName 升级
- [ ] `gradlew assembleDebug` 成功
- [ ] APK 复制到 `update-server/apks/BrainQuest-v<版本>.apk`
- [ ] `python release.py`（末尾必须出现 `[verify] ✓`，自校验不过禁止发布）
- [ ] `python tools/publish_github.py`（末尾出现 `✓ GitHub 托管生效`）
- [ ] 浏览器验证 `releases/latest/download/manifest.json` 指向新版本
- [ ] 真机/模拟器实际走一次「检查更新 → 增量更新」
- [ ] git commit + push

## 五之二、出题规则热更（gen_rules.json）
`assets/config/gen_rules.json` 可由热更包同名文件覆盖（`filesDir/content/packs/gen_rules.json`），改手感无需发版：
- `fillChance`：填空题出现概率（%）；`fillMaxDifficulty`：填空题最高难度
- `baseDamage / perLevelDamage / comboDamage`：战斗伤害公式参数；`fillTimeBonus`：填空题加时（秒）
发布方式：把 gen_rules.json（version+1）放进内容包 → `release.py --packs-only` → publish → App 内更新内容包即生效

## 五之〇、版本策略
- **单机**：离线完全可玩，不做强制更新
- **联机对战**：强制双方同版本（握手携带 versionName，内嵌服务器/房主端校验，不一致拒绝加入并提示双方更新）
- **补丁链**：apks/ 内历史 APK 保留（勿删，补丁链依赖），debug 签名旧版隔离在 _hold/；每次发版自动重建「全部历史版本 → 最新」补丁链
- 目标：尽量让所有用户都在最新版，减少多版本维护；联机是版本收敛的主要动力

## 五之一、更新体系增强（v1.4.1 起）
- **多版本补丁链**：`release.py` 为 `apks/` 中每个签名兼容的历史版本生成 →最新补丁（幂等：已验证的补丁重跑直接复用），manifest.patches 全列；App 按自身 versionCode 自动匹配
- **国内多源回退**：`UpdateManager.fetchManifestMulti` 按序尝试 [GitHub直连, gh-proxy镜像, ghfast镜像]（用户自定义地址时独占），成功源记入 activeBase 供后续补丁/APK/内容包下载使用
- 镜像地址格式：`https://gh-proxy.com/` + GitHub 完整链接（ghfast.top 同）

## 五之二、联机对战（阶段一已实现）

架构（v1.4.0 起双形态）：
- **App 内嵌服务器**（`net/EmbeddedPkServer.kt`，Java-WebSocket 库）：房主手机即服务器，支持热点离线对战
- **独立服务器**（`update-server/pk_server.py`）：跑在电脑/公网 VPS 上
协议统一：create/join → start(题目由房主本地选题整包下发) → answer(逐题转发对手) → finish(双方) → 判定(答对数、平局比用时) → result。加入方 PkClient 对两种服务器完全透明。
局域网发现：房主 UDP 信标广播（每秒，端口 8766，含房间码/昵称/TCP 端口），加入方监听列出房间；AP 隔离网络广播被挡 → 手动输 IP 兜底。
验证状态（模拟器实测）：内嵌服务器完整对战一局（创建→加入→10题同答→判定→结算 3:8 正确呈现）✓；UDP 信标被主机侧监听接收 ✓。
待真机两台实测：同 Wi-Fi 搜索发现、热点模式（模拟器 NAT 不转发广播，无法模拟真机行为）。

## 五之四、联机对战服务器（远程部署）

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

### 版本策略
- 单机离线免更新；**联机强制双方同版本**（握手携带 versionName，不一致拒绝加入并提示）
- 补丁链助老版本（v1.1.2+）小补丁升级；apks/ 历史 APK 保留勿删（补丁链依赖），debug 签名旧版隔离 _hold/

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
| 11 | ToneGenerator 常量编译错 | TONE_PROP_KEY/PIP 不存在 | 用 TONE_PROP_ACK 等 |
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
## 七、后续可做事项

- [ ] 打 tag（v1.1.1）实战验证一次 Actions 发版流水线（workflow 已就绪）
- [ ] release 正式签名（生成 keystore + build.gradle.kts signingConfig；目前 debug 签名）
- [ ] 题库批量扩充（各大学科目 45~75 题 → 目标 150+，以热更包发布顺便演示热更）
- [ ] 真机实测一轮（安装 APK → Wi-Fi/公网更新）
- [ ] 考研模式扩展到闯关难度、错题本复习强化、云存档
- [ ] README 补充截图与 Release 链接

## 八、记录约定

- 环境变更（路径/版本/账号）→ 同步更新本文件与 `AGENTS.md`
- 新踩的坑 → 补进 §六 详表（现象/根因/解法三段式）
- 新工具 → 补进 §三 工具清单
