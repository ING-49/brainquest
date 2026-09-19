# AGENTS.md — 本工作区开发环境与作业规范

> 「脑力大冒险」Android 项目的环境速查与避坑清单。供 AI 助手与新成员快速上手。
> 完整版（流水线叙述、踩坑详表）见 `docs/PLAYBOOK.md`。

## 环境（已固化，直接用）

| 项 | 值 |
|---|---|
| JDK 17 | `E:\Tools\jdk-17.0.20.1+1`（JAVA_HOME 已 setx）⚠️ 不要用 Studio 自带 JBR（Java 25，Kotlin 2.0 编译器崩溃报 `IllegalArgumentException: <版本号>`） |
| Android SDK | `E:\Tools\Android-Studio\Android\SDK`（ANDROID_HOME 已 setx；platform 34/37、build-tools 34.0.0/36.0.0、系统镜像 android-34 google_apis x86_64） |
| 模拟器 AVD | `BrainQuest`（Pixel 6, API 34）。headless 启动：`emulator -avd BrainQuest -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot`（需设 ANDROID_HOME/ANDROID_AVD_HOME 环境变量） |
| gh CLI | `E:\Tools\gh-cli\bin\gh.exe`（已登录 GitHub 账号 ING-49，token 在系统 keyring） |
| Gradle | wrapper 8.7；依赖走阿里云镜像（settings.gradle.kts，dl.google.com 被墙）；GRADLE_USER_HOME=`E:\Tools\Android-Studio\Gradle-home` |
| Python | 3.8 + numpy（差分编码依赖）；bsdiff4 已弃用（Windows 版有缺陷） |

## 关键命令

```bash
# 构建（JDK17/ANDROID_HOME 已 setx 到用户级）
gradlew assembleDebug
# 安装并启动
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.brainquest.game/.MainActivity
# 截图验证
adb exec-out screencap -p > screen.png

# 发版三步（详见 docs/PLAYBOOK.md）
# 1) app/build.gradle.kts 版本号+1 → gradlew assembleDebug
#    → cp app/build/outputs/apk/debug/app-debug.apk update-server/apks/BrainQuest-v<版本>.apk
# 2) cd update-server && python release.py        # 差分+manifest+自校验
# 3) GH_EXE=E:/Tools/gh-cli/bin/gh.exe python tools/publish_github.py
```

- GitHub 仓库：https://github.com/ING-49/brainquest （public）
- App 更新地址（默认，写在 PlayerState）：`https://github.com/ING-49/brainquest/releases/latest/download`
- 模拟器直连不了 GitHub 时：`python tools/dev_relay.py`（App 填 http://10.0.2.2:8000）
- 应用包名：`com.brainquest.game`；`adb shell appops set com.brainquest.game REQUEST_INSTALL_PACKAGES allow` 可预授权应用内自装 APK

## 工具清单

| 工具 | 用途 |
|---|---|
| `tools/autoplay.py` | uiautomator 自动答题（战斗关卡回归测试） |
| `tools/update_demo.py` | 更新流程 UI 自动化（检查更新→热更→增量安装） |
| `tools/dev_relay.py` | 开发中继：模拟器 → 本机 → GitHub Releases |
| `tools/publish_github.py` | 发布 update-server 产物到 GitHub Releases（latest 地址自校验） |
| `tools/pk_guest.py` | 联机机器人对手：`--quick` 快速匹配（可当房主）/ 房间码模式；PK_URL 环境变量 |
| `tools/pk_server_smoke.py` | PK 服务器冒烟测试（在线人数/版本隔离/配对/整局/取消，7 项断言） |
| `update-server/release.py` | 一键：打内容包 + BQDELTA1 差分 + manifest + 合成自校验 |
| `update-server/delta.py` | 自研差分编码器（滚动哈希块匹配，numpy 加速） |
| `启动更新服务器.bat` | 本地静态服务器（自动显示局域网 IP，供真机同 Wi-Fi 更新） |

## 已踩过的坑（先读这条再做，能省大量时间）

1. **Compose 游戏状态类**：普通 Kotlin 类字段变更**不触发重组** → UI 整体冻结。游戏状态必须用 `mutableStateOf/mutableIntStateOf/mutableStateListOf`，或提供 version 计数器供 UI 读取；`LaunchedEffect` 的 key 必须是快照状态
2. **LazyColumn item 内不要做顺序累加计算**（如"前一关通过才解锁"）——滚动重组乱序重算会错乱；用 `remember` 在列表外预计算
3. **尾随 lambda 绑定到参数表最后一个参数**（即使中间有带默认值的参数）：`f(a, b) { }` 会把 lambda 传给最后的 b → 用命名参数
4. **Kotlin 块注释里写 `/*.json` 会被当嵌套注释开始** → "Syntax error: Unclosed comment"
5. **`runCatching` 块最后一句若是赋值**（Unit）→ `.onSuccess { it }` 的 it 是 Unit，后续字段全 Unresolved
6. **Android 9+ 禁明文 HTTP**：manifest 已加 `usesCleartextTraffic="true"`；否则报 CLEARTEXT not permitted
7. **网络 IO 必须在 IO 线程**：`rememberCoroutineScope` 跑在主线程，直接 OkHttp 会 NetworkOnMainThreadException → `scope.launch(Dispatchers.IO)`
8. **adb 二进制传输**：`adb shell cat` 在 Windows 会 CRLF 损坏二进制 → 用 `adb exec-out`；Git Bash 下 adb 的 `/sdcard` 参数会被转成本地路径 → 写 `//sdcard//`
9. **遗留的系统安装器 "App installed." 界面不关**：新 ACTION_VIEW 安装意图会被路由到旧安装器任务（logcat 可见 onActivityRestartAttempt: InstallSuccess），确认弹窗弹不出来 → 先点 Done 清掉旧界面
10. **ToneGenerator** 没有 TONE_PROP_KEY/TONE_PROP_PIP，可用 TONE_PROP_ACK/BEEP/BEEP2/NACK
11. **python-bsdiff4 Windows 版**补丁应用端有 32 位 long 溢出缺陷（哨兵 seek 被截断）→ 本项目自研 BQDELTA1（delta.py），不要换回 bsdiff4
12. **更新后自动回游戏**：Android 后台启动限制（BAL）会拦截 Receiver 拉起 Activity → 先尝试拉起、失败发通知兜底；POST_NOTIFICATIONS 需运行时授权
13. **`adb shell input text` 不支持中文**（会 NPE）；自动化测试用英文数字输入
14. **CI（GitHub Actions ubuntu runner）**：Android SDK 预装（build-tools 34.0.0 可用）；需 `pip install numpy`；aapt2 无 .exe 后缀（release.py 已做跨平台）
