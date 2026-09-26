package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.brainquest.game.net.PkEvent
import com.brainquest.game.net.PkClient
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.Routes
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 云存档独立页面（从设置页多进一层：口令等属隐私操作）。
 * 口令不落盘不上传：上传/下载时临时弹窗输入，用完即弃。
 */
@Composable
fun CloudSaveScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var cloudStatus by remember { mutableStateOf("") }
    var cloudAction by remember { mutableStateOf("") }         // put / get / del，非空=操作中防重复点击
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restorePassword by remember { mutableStateOf("") }     // 恢复确认弹窗里的口令（加密存档用）
    var pendingDelete by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    // 弹窗输入：上传口令 / 下载（原设备存档码、身份码、口令）
    var uploadPassword by remember { mutableStateOf("") }
    var dlCodeInput by remember { mutableStateOf("") }
    var dlOwnerInput by remember { mutableStateOf("") }
    var dlPassword by remember { mutableStateOf("") }

    fun effectiveCode(): String =
        (if (dlCodeInput.isNotBlank()) dlCodeInput else player.cloudCode).trim().uppercase()

    fun effectiveOwner(): String =
        (if (dlOwnerInput.isNotBlank()) dlOwnerInput else vm.identity).trim().uppercase()

    // 云存档：独立短连（连接成功后按 cloudAction 发对应请求）
    val cloudEvents = remember { MutableSharedFlow<PkEvent>(extraBufferCapacity = 8) }
    val cloudClient = remember { PkClient { cloudEvents.tryEmit(it) } }
    DisposableEffect(Unit) { onDispose { cloudClient.close() } }
    LaunchedEffect(Unit) {
        cloudEvents.collect { e ->
            when (e) {
                is PkEvent.Connected -> when (cloudAction) {
                    "put" -> {
                        if (effectiveCode().isBlank()) {
                            // 首次上传刚生成的存档码可能尚未写回 PlayerState：兜底提示，不发空码请求
                            cloudAction = ""
                            cloudStatus = "❌ 存档码尚未就绪，请重试上传"
                            cloudClient.close()
                        } else {
                            cloudStatus = "正在加密并上传…"
                            cloudClient.sendCloudPut(effectiveCode(), vm.identity, vm.encryptSaveJson(uploadPassword.trim()))
                        }
                    }
                    "get" -> {
                        cloudStatus = "正在下载…"
                        cloudClient.sendCloudGet(effectiveCode(), effectiveOwner(), vm.identity)
                    }
                    "del" -> cloudClient.sendCloudDel(effectiveCode(), vm.identity)
                }
                is PkEvent.SaveOk -> {
                    cloudAction = ""
                    cloudStatus = "✅ 已上传（${e.size} 字节，密文存储，归属 ${vm.identity}）"
                    cloudClient.close()
                }
                is PkEvent.SaveDeleted -> {
                    cloudAction = ""
                    cloudStatus = "🗑 云端存档已删除"
                    cloudClient.close()
                }
                is PkEvent.SaveData -> {
                    cloudAction = ""
                    pendingRestore = e.data
                    restorePassword = dlPassword   // 下载时输过的口令直接带上，加密存档可直接覆盖恢复
                    cloudStatus = "已取到云端存档，确认后覆盖本地（归属将转移到本机身份码）"
                    cloudClient.close()
                }
                is PkEvent.Error -> {
                    // 操作已完成后是我们主动关连接，关闭事件不算错误
                    if (cloudAction.isNotEmpty()) {
                        cloudAction = ""
                        cloudStatus = "❌ ${e.msg}"
                        cloudClient.close()
                    }
                }
                is PkEvent.Disconnected -> {
                    if (cloudAction.isNotEmpty()) {
                        cloudAction = ""
                        cloudStatus = "❌ 连接失败，请检查网络后重试"
                    }
                }
                else -> {}
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            "☁️ 云存档",
            onBack = { nav.popBackStack() },
            subtitle = "进度上云 · 跨设备恢复",
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "存档码与身份码自动生成、不可修改；存档用口令加密后才上传，且与身份码绑定。换新设备：点「下载恢复」输入原设备的存档码 + 身份码 + 口令即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("存档码：", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        player.cloudCode.ifBlank { "首次上传时自动生成" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("身份码：", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        vm.identity,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("identity", vm.identity))
                        android.widget.Toast.makeText(context, "🪪 身份码已复制", android.widget.Toast.LENGTH_SHORT).show()
                        cloudStatus = "🪪 身份码已复制到剪贴板（归属校验用，不可修改）"
                    }) { Text("📋 复制") }
                }
                Text(
                    "🔒 口令只在上传/下载时临时输入：不保存在本机也不上传，丢了无法找回存档，请牢记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 弹窗每次打开都清空口令/恢复输入，防上次输入残留（口令累加类事故）
                    Button(onClick = { uploadPassword = ""; showUploadDialog = true }, enabled = cloudAction.isEmpty()) { Text("⬆️ 上传存档") }
                    OutlinedButton(
                        onClick = { dlCodeInput = ""; dlOwnerInput = ""; dlPassword = ""; showDownloadDialog = true },
                        enabled = cloudAction.isEmpty(),
                    ) { Text("⬇️ 下载恢复") }
                }
                TextButton(
                    onClick = { pendingDelete = true },
                    enabled = cloudAction.isEmpty() && player.cloudCode.isNotBlank(),
                ) { Text("🗑 删除云端存档") }
                if (cloudStatus.isNotBlank()) {
                    Text(cloudStatus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Text(
            "提示：导入云端存档会整份覆盖本机进度（含设置）；本机身份码保持不变，云端归属自动转到本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (showUploadDialog) {
            AlertDialog(
                onDismissRequest = { showUploadDialog = false },
                title = { Text("⬆️ 上传存档") },
                text = {
                    Column {
                        Text("将把本机进度加密上传到云端存档码 ${player.cloudCode.ifBlank { "（上传时自动生成）" }}。")
                        OutlinedTextField(
                            value = uploadPassword,
                            onValueChange = { uploadPassword = it.take(24) },
                            label = { Text("设置存档口令（≥4 位）") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            ),
                        )
                        Text(
                            "口令是唯一凭证，丢失将无法恢复云端存档。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (uploadPassword.trim().length < 4) {
                            cloudStatus = "请先设置至少 4 位存档口令"
                            return@Button
                        }
                        if (player.cloudCode.isBlank()) {
                            vm.setSettings(cloudCode = vm.generateCloudCode())
                        }
                        showUploadDialog = false
                        cloudAction = "put"
                        cloudStatus = "连接服务器…"
                        cloudClient.connect(player.pkServerUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)
                    }) { Text("加密并上传") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showUploadDialog = false }) { Text("取消") }
                },
            )
        }

        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("⬇️ 下载恢复") },
                text = {
                    Column {
                        Text("从云端取回存档并覆盖本机进度。本机玩就留空前两格。")
                        OutlinedTextField(
                            value = dlCodeInput,
                            onValueChange = { dlCodeInput = it.uppercase().take(12) },
                            label = { Text("原设备的存档码（换新设备才填）") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = dlOwnerInput,
                            onValueChange = { dlOwnerInput = it.uppercase().take(14) },
                            label = { Text("原设备的身份码（换新设备才填）") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = dlPassword,
                            onValueChange = { dlPassword = it.take(24) },
                            label = { Text("存档口令（加密存档必填）") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            ),
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (effectiveCode().isBlank()) {
                            cloudStatus = "还没有存档码：先在本机上传一次，或填入原设备的存档码"
                            return@Button
                        }
                        showDownloadDialog = false
                        cloudAction = "get"
                        cloudStatus = "连接服务器…"
                        cloudClient.connect(player.pkServerUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)
                    }) { Text("下载") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDownloadDialog = false }) { Text("取消") }
                },
            )
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
                                value = restorePassword,
                                onValueChange = { restorePassword = it.take(24) },
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
                        val ok = vm.importCloudSave(data, restorePassword.trim())
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
                text = { Text("将删除服务器上存档码 ${player.cloudCode} 对应的云端存档，本机进度不受影响。此操作不可恢复。") },
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
    }
}
