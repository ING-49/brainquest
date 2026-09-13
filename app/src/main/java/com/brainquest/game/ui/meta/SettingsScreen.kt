package com.brainquest.game.ui.meta

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                    "有新版本时在这里更新：只下载很小的补丁，进度不丢失",
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
                                val fetched = updater.fetchManifest(player.updateServerUrl)
                                manifest = fetched
                                fetched
                            }.onSuccess { m ->
                                val packsPending = m.contentPacks.count { it.version > (player.contentVersions[it.id] ?: 0) }
                                val appOld = BuildConfig.VERSION_CODE < m.latestVersionCode
                                status = buildString {
                                    append("服务器版本 v${m.latestVersionName}(${m.latestVersionCode})")
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
                                        player.updateServerUrl,
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
                            busy = true
                            progress = 0f
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    if (patch != null) {
                                        status = "下载增量补丁（${patch.size / 1024}KB）…"
                                        val patchFile = updater.download(
                                            url = UpdateManager.joinUrl(player.updateServerUrl, patch.file),
                                            expectedSha256 = patch.sha256,
                                            onProgress = { p -> progress = p.fraction; status = "下载补丁 ${p.mbText}" },
                                        )
                                        val newApk = updater.applyIncrementalPatch(patchFile, m.fullApkSha256) { s -> status = s }
                                        check(updater.verifyApkSignature(newApk)) { "签名不一致，已中止安装" }
                                        tryInstall(context, newApk, { status = it }, { pendingApk = it })
                                    } else {
                                        status = "下载完整 APK…"
                                        val apk = updater.download(
                                            url = UpdateManager.joinUrl(player.updateServerUrl, m.fullApk),
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
