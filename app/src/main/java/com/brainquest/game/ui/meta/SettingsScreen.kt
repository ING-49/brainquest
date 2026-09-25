package com.brainquest.game.ui.meta

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.BuildConfig
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.update.ApkInstaller
import com.brainquest.game.update.UpdateManager
import com.brainquest.game.update.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val updater = remember { UpdateManager(context) }

    var url by remember(player.updateServerUrl) { mutableStateOf(player.updateServerUrl) }
    var manifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var status by remember { mutableStateOf("未检查。启动本地演示服务器后点击「检查更新」。") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf<java.io.File?>(null) }
    var showDevUrl by remember { mutableStateOf(false) }
    var cloudStatus by remember { mutableStateOf("") }
    var cloudAction by remember { mutableStateOf("") }        // put / get，"正在操作中"防重复点击
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var cloudCodeInput by remember(player.cloudCode) { mutableStateOf(player.cloudCode) }
    var cloudPassword by remember { mutableStateOf("") }   // 口令只在本机内存，不落盘不上服务器
    var showPrivacy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }

    // 云存档：独立短连（连接成功后按 cloudAction 发对应请求）
    val cloudEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<com.brainquest.game.net.PkEvent>(extraBufferCapacity = 8) }
    val cloudClient = remember { com.brainquest.game.net.PkClient { cloudEvents.tryEmit(it) } }
    DisposableEffect(Unit) { onDispose { cloudClient.close() } }
    LaunchedEffect(Unit) {
        cloudEvents.collect { e ->
            when (e) {
                is com.brainquest.game.net.PkEvent.Connected -> when (cloudAction) {
                    "put" -> {
                        cloudStatus = "正在加密并上传…"
                        cloudClient.sendCloudPut(cloudCodeInput.trim().uppercase(), vm.encryptSaveJson(cloudPassword.trim()))
                    }
                    "get" -> {
                        cloudStatus = "正在下载…"
                        cloudClient.sendCloudGet(cloudCodeInput.trim().uppercase())
                    }
                    "del" -> cloudClient.sendCloudDel(cloudCodeInput.trim().uppercase())
                }
                is com.brainquest.game.net.PkEvent.SaveOk -> {
                    cloudAction = ""
                    cloudStatus = "✅ 已上传（${e.size} 字节，密文存储）"
                    cloudClient.close()
                }
                is com.brainquest.game.net.PkEvent.SaveDeleted -> {
                    cloudAction = ""
                    cloudStatus = "🗑 云端存档已删除"
                    cloudClient.close()
                }
                is com.brainquest.game.net.PkEvent.SaveData -> {
                    cloudAction = ""
                    pendingRestore = e.data
                    cloudStatus = "已取到云端存档，确认后覆盖本地"
                    cloudClient.close()
                }
                is com.brainquest.game.net.PkEvent.Error -> {
                    // 操作已完成后是我们主动关连接，关闭事件不算错误
                    if (cloudAction.isNotEmpty()) {
                        cloudAction = ""
                        cloudStatus = "❌ ${e.msg}"
                        cloudClient.close()
                    }
                }
                is com.brainquest.game.net.PkEvent.Disconnected -> {
                    if (cloudAction.isNotEmpty()) {
                        cloudAction = ""
                        cloudStatus = "❌ 连接失败，请检查网络后重试"
                    }
                }
                else -> {}
            }
        }
    }

    fun startCloudPut() {
        if (cloudPassword.trim().length < 4) {
            cloudStatus = "请先设置至少 4 位存档口令（口令是唯一凭证，丢失将无法恢复云端存档）"
            return
        }
        var code = cloudCodeInput.trim().uppercase()
        if (code.isBlank()) {
            code = vm.generateCloudCode()
            cloudCodeInput = code
        }
        vm.setSettings(cloudCode = code)
        cloudAction = "put"
        cloudStatus = "连接服务器…"
        cloudClient.connect(player.pkServerUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)
    }

    fun startCloudGet() {
        val code = cloudCodeInput.trim().uppercase()
        if (code.isBlank()) {
            cloudStatus = "请输入原设备的存档码"
            return
        }
        vm.setSettings(cloudCode = code)
        cloudAction = "get"
        cloudStatus = "连接服务器…"
        cloudClient.connect(player.pkServerUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)
    }
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showDevUrl = true },
                    ),
            ) {
                PageHeader(
                    "⚙️ 设置与更新",
                    onBack = { nav.popBackStack() },
                    subtitle = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
        }

        if (showDevUrl) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                singleLine = true,
                label = { Text("开发者：更新服务器地址") },
            )
            OutlinedButton(onClick = { vm.setSettings(url = url); showDevUrl = false }, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("保存地址")
            }
        }

        // 通用设置
        SettingRow("🔊 音效", player.soundOn) { vm.setSettings(sound = it) }
        SettingRow("📳 震动反馈", player.hapticsOn) { vm.setSettings(haptics = it) }
        SettingRow(
            label = "🎓 大学考研模式",
            subtitle = "开：每日挑战出大学科目高难题（高数/线代/概率/高频/通信，考研向）\n关：每日挑战出基础入门题（数学口算/逻辑/英语/科学/编程）",
            checked = player.hardMode,
        ) { vm.setSettings(hard = it) }

        // 云存档
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("☁️ 云存档", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("跨设备恢复进度：上传存档时设置口令（≥4 位，存档用口令加密后才上传）→ 新设备输入同一存档码与口令 → 「下载存档」恢复。口令不保存在本机也不上传，丢了无法找回存档，请牢记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = cloudCodeInput,
                    onValueChange = { cloudCodeInput = it.uppercase().take(12) },
                    label = { Text("存档码（留空上传则自动生成）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cloudPassword,
                    onValueChange = { cloudPassword = it.take(24) },
                    label = { Text("存档口令（上传需 ≥4 位；下载旧存档可留空）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startCloudPut() }, enabled = cloudAction.isEmpty()) { Text("⬆️ 上传存档") }
                    OutlinedButton(onClick = { startCloudGet() }, enabled = cloudAction.isEmpty()) { Text("⬇️ 下载存档") }
                }
                androidx.compose.material3.TextButton(
                    onClick = { pendingDelete = true },
                    enabled = cloudAction.isEmpty() && cloudCodeInput.isNotBlank(),
                ) { Text("🗑 删除云端存档") }
                if (cloudStatus.isNotBlank()) {
                    Text(cloudStatus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        pendingRestore?.let { data ->
            val encrypted = com.brainquest.game.util.SaveCrypto.isEnvelope(data)
            AlertDialog(
                onDismissRequest = { pendingRestore = null; cloudStatus = "已取消" },
                title = { Text("恢复云存档？") },
                text = {
                    Column {
                        Text("将用云端存档覆盖本机当前进度，确定继续吗？")
                        if (encrypted) {
                            OutlinedTextField(
                                value = cloudPassword,
                                onValueChange = { cloudPassword = it.take(24) },
                                label = { Text("该存档已加密，请输入口令") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                                ),
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val ok = vm.importCloudSave(data, cloudPassword.trim())
                        cloudStatus = when {
                            ok -> "✅ 已恢复云端存档"
                            encrypted -> "❌ 口令错误或存档已损坏，未恢复"
                            else -> "❌ 存档解析失败"
                        }
                        pendingRestore = null
                    }) { Text("覆盖恢复") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingRestore = null; cloudStatus = "已取消" }) { Text("取消") }
                },
            )
        }

        if (pendingDelete) {
            AlertDialog(
                onDismissRequest = { pendingDelete = false },
                title = { Text("删除云端存档？") },
                text = { Text("将删除服务器上存档码 ${cloudCodeInput.trim().uppercase()} 对应的云端存档，本机进度不受影响。此操作不可恢复。") },
                confirmButton = {
                    Button(onClick = {
                        pendingDelete = false
                        cloudAction = "del"
                        cloudStatus = "连接服务器…"
                        cloudClient.connect(player.pkServerUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)
                    }) { Text("删除") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingDelete = false }) { Text("取消") }
                },
            )
        }

        if (showPrivacy) {
            val privacyText = remember {
                runCatching { context.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() } }
                    .getOrDefault("隐私政策文件缺失")
            }
            AlertDialog(
                onDismissRequest = { showPrivacy = false },
                title = { Text("隐私政策") },
                text = {
                    Text(
                        privacyText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    Button(onClick = { showPrivacy = false }) { Text("我知道了") }
                },
            )
        }

        // 隐私政策
        Text(
            "🔒 隐私政策",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPrivacy = true }
                .padding(top = 12.dp),
        )

        // 更新中心
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🔄 更新中心", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "当前题库：高等数学 ${vm.bank.countFor(com.brainquest.game.data.question.Subjects.ADV_MATH)} 题 · 英语 ${vm.bank.countFor(com.brainquest.game.data.question.Subjects.ENGLISH)} 题 · 通信原理 ${vm.bank.countFor(com.brainquest.game.data.question.Subjects.COMMUNICATION)} 题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    "有新版本时在这里更新：只下载很小的补丁，进度不丢失。单机离线也能玩；联机对战需双方都为最新版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                Button(
                    onClick = {
                        busy = true
                        progress = null
                        status = "正在检查更新…"
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val defaultBase = "https://github.com/ING-49/brainquest/releases/latest/download"
                                // 用户自定义了地址 → 只用自定义；否则 多源回退（直连→镜像）
                                val bases = if (player.updateServerUrl != defaultBase) listOf(player.updateServerUrl)
                                else {
                                    // 上次成功的源排最前（利于国内更新），其余兜底
                                    val all = listOf(
                                        defaultBase,
                                        "$defaultBase".let { "https://gh-proxy.com/$it" },
                                        "$defaultBase".let { "https://ghfast.top/$it" },
                                    )
                                    if (player.lastGoodSource in all)
                                        listOf(player.lastGoodSource) + all.filter { it != player.lastGoodSource }
                                    else all
                                }
                                val (fetched, base) = updater.fetchManifestMulti(bases)
                                vm.setSettings(lastGoodSource = base)
                                manifest = fetched
                                android.util.Log.i("UpdateDemo", "更新源: $base")
                                val srcLabel = when {
                                    base.contains("gh-proxy") -> "gh-proxy 镜像"
                                    base.contains("ghfast") -> "ghfast 镜像"
                                    base == defaultBase -> "GitHub 直连"
                                    else -> "自定义地址"
                                }
                                fetched
                            }.onSuccess { m ->
                                val srcLabel = when {
                                    updater.activeBase!!.contains("gh-proxy") -> "gh-proxy 镜像"
                                    updater.activeBase!!.contains("ghfast") -> "ghfast 镜像"
                                    updater.activeBase == "https://github.com/ING-49/brainquest/releases/latest/download" -> "GitHub 直连"
                                    else -> "自定义地址"
                                }
                                val packsPending = m.contentPacks.count { it.version > (player.contentVersions[it.id] ?: 0) }
                                val appOld = BuildConfig.VERSION_CODE < m.latestVersionCode
                                status = buildString {
                                    append("服务器版本 v${m.latestVersionName}(${m.latestVersionCode})")
                                    append(" · 更新源：$srcLabel")
                                    if (appOld) append(" · 📱有新版本！")
                                    if (packsPending > 0) append(" · 📦${packsPending}个内容包待更新")
                                    if (!appOld && packsPending == 0) append(" · 一切都是最新 ✅")
                                    if (m.notice.isNotBlank()) append("\n公告：${m.notice}")
                                }
                            }.onFailure {
                                android.util.Log.e("UpdateDemo", "检查更新失败", it)
                                status = "❌ 检查失败：${it.javaClass.simpleName}: ${it.message}\n请确认已启动 update-server（python -m http.server）"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text("检查更新") }

                // 内容包更新
                val currentManifest = manifest
                if (currentManifest != null && currentManifest.contentPacks.any { it.version > (player.contentVersions[it.id] ?: 0) }) {
                    Button(
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    updater.updateContentPacks(
                                        updater.currentBase(player.updateServerUrl),
                                        currentManifest,
                                        player.contentVersions,
                                    ) { s -> status = s }
                                }.onSuccess { updated ->
                                    if (updated.isEmpty()) {
                                        status = "内容包已是最新"
                                    } else {
                                        val versions = updated.associate { it.id to it.version }
                                        vm.setContentVersions(player.contentVersions + versions)
                                        vm.bank.reload()
                                        status = "✅ 已更新 ${updated.size} 个内容包并重载题库：${updated.joinToString { it.id }}"
                                    }
                                }.onFailure {
                                    status = "❌ 更新失败：${it.message}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.padding(top = 6.dp),
                    ) { Text("📦 更新全部内容包") }
                }

                // APK 更新（增量优先）
                if (currentManifest != null && BuildConfig.VERSION_CODE < currentManifest.latestVersionCode) {
                    val m = currentManifest
                    val patch = m.patches.firstOrNull {
                        it.from == BuildConfig.VERSION_CODE && it.to == m.latestVersionCode
                    }
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            busy = true
                            progress = 0f
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    if (patch != null) {
                                        status = "下载增量补丁（${patch.size / 1024}KB）…"
                                        val patchFile = updater.downloadWithFallback(
                                            fallbackBase = player.updateServerUrl,
                                            relative = patch.file,
                                            expectedSha256 = patch.sha256,
                                            onProgress = { p -> progress = p.fraction; status = "下载补丁 ${p.mbText}" },
                                        )
                                        val newApk = updater.applyIncrementalPatch(patchFile, m.fullApkSha256) { s -> status = s }
                                        check(updater.verifyApkSignature(newApk)) { "签名不一致，已中止安装" }
                                        tryInstall(context, newApk, { status = it }, { pendingApk = it })
                                    } else {
                                        status = "下载完整 APK…"
                                        val apk = updater.downloadWithFallback(
                                            fallbackBase = player.updateServerUrl,
                                            relative = m.fullApk,
                                            expectedSha256 = m.fullApkSha256,
                                            outputName = "brainquest_full.apk",
                                            onProgress = { p -> progress = p.fraction; status = "下载 APK ${p.mbText}" },
                                        )
                                        check(updater.verifyApkSignature(apk)) { "签名不一致，已中止安装" }
                                        tryInstall(context, apk, { status = it }, { pendingApk = it })
                                    }
                                }.onFailure {
                                    status = "❌ 更新失败：${it.message}"
                                    progress = null
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(if (patch != null) "⚡ 增量更新到 v${m.latestVersionName}（推荐）" else "⬇️ 全量更新到 v${m.latestVersionName}")
                    }
                }

                if (pendingApk != null) {
                    Button(
                        onClick = { ApkInstaller.installApk(context, pendingApk!!) },
                        modifier = Modifier.padding(top = 6.dp),
                    ) { Text("已授权，继续安装") }
                }

                progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Text(
            "💡 更新只下载很小的补丁，金币、错题本等进度全部保留，安装完成后会自动回到游戏。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun tryInstall(
    context: android.content.Context,
    apk: java.io.File,
    setStatus: (String) -> Unit,
    setPending: (java.io.File?) -> Unit,
) {
    if (ApkInstaller.canInstall(context)) {
        setStatus("✅ 新 APK 已就绪，调起安装…")
        ApkInstaller.installApk(context, apk)
    } else {
        setStatus("⚠️ 新 APK 已就绪，请先在系统设置中允许本应用安装未知应用，然后回来点击「继续安装」")
        ApkInstaller.requestInstallPermission(context)
        setPending(apk)
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
