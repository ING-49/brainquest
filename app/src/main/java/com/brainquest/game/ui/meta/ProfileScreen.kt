package com.brainquest.game.ui.meta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.Routes
import com.brainquest.game.data.levelForXp
import com.brainquest.game.ui.AvatarBadge
import com.brainquest.game.ui.StatChip
import com.brainquest.game.ui.XpBar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import com.brainquest.game.ui.SectionCard
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        var showAvatarPicker by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val name = "avatar_${System.currentTimeMillis()}.jpg"
                val dst = java.io.File(ctx.filesDir, "avatars/$name")
                dst.parentFile?.mkdirs()
                runCatching {
                    val src2 = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
                    val scaled = android.graphics.Bitmap.createScaledBitmap(
                        src2, 256, (256f * src2.height / src2.width).toInt().coerceAtLeast(1), true,
                    )
                    dst.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                    vm.setAvatar("custom://$name")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(2.dp)   // 露出卡片圆角内的渐变描边
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { showAvatarPicker = true }) {
                        AvatarBadge(player.avatar, 64)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f).clickable { showRename = true }) {
                        Text(player.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Lv.${levelForXp(player.xp)} · 累计经验 ${player.xp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    com.brainquest.game.ui.CoinText(
                        player.coins,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { nav.navigate(com.brainquest.game.ui.Routes.SHOP) },
                    )
                }
                XpBar(player.xp, Modifier.padding(top = 10.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            StatChip("🎯", "答对", "${player.totalCorrect}", Modifier.weight(1f))
            StatChip("💥", "答错", "${player.totalWrong}", Modifier.weight(1f))
            StatChip("📈", "正确率", "${(player.accuracy * 100).toInt()}%", Modifier.weight(1f))
            StatChip("⭐", "星星", "${player.totalStars}", Modifier.weight(1f))
        }

        // 本周学习（最近 7 天答题柱状）
        val weekBars = remember(player.dailyResults) {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val wd = java.text.SimpleDateFormat("E", java.util.Locale.getDefault())
            val cal = java.util.Calendar.getInstance()
            (6 downTo 0).map { off ->
                cal.time = java.util.Date()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -off)
                val key = fmt.format(cal.time)
                val r = player.dailyResults[key]
                Triple(wd.format(cal.time), r?.correct ?: 0, r?.total ?: 0)
            }
        }
        val weekCorrect = weekBars.sumOf { it.second }
        val weekTotal = weekBars.sumOf { it.third }
        val maxBar = weekBars.maxOf { it.second }.coerceAtLeast(1)
        SectionCard("📅 本周学习") {
            Text(
                if (weekTotal > 0) "本周答对 $weekCorrect / $weekTotal" else "本周还没答题，去闯一闯吧",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weekBars.forEach { (label, correct, total) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height((8 + 52f * correct / maxBar).dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                ),
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        MenuCard("🏅 成就墙", "已解锁 ${player.achievements.size} 个成就") { nav.navigate(Routes.ACHIEVEMENTS) }
        MenuCard("📖 错题本", "${player.wrongBook.count { !it.mastered }} 道待复习") { nav.navigate(Routes.WRONGBOOK) }
        MenuCard("🛒 商店", "道具 · 主题 · 头像") { nav.navigate(Routes.SHOP) }
        MenuCard("⚙️ 设置与更新", "音效 · 震动 · 热更新 · APK升级") { nav.navigate(Routes.SETTINGS) }

        if (showAvatarPicker) {
            // Q 版头像需拥有才能选（kawaii_0 为体验款人人可用），其余可在商店购入
            val ownedKawaii = (listOf("kawaii_0") + player.ownedAvatars.filter { it.startsWith("kawaii_") }).distinct()
            val classic = listOf("🧑‍🎓", "🐻", "🐱", "🦊", "🐼", "🦁", "🐸", "🐵", "🦉", "🤖", "👻", "🧙")
            val ctx2 = androidx.compose.ui.platform.LocalContext.current
            val customFiles = remember {
                java.io.File(ctx2.filesDir, "avatars")
                    .listFiles { f -> f.extension == "jpg" }?.map { "custom://${it.name}" } ?: emptyList()
            }
            AlertDialog(
                onDismissRequest = { showAvatarPicker = false },
                title = { Text("选择头像") },
                text = {
                    Column {
                        Text("✨ Q 版头像", style = MaterialTheme.typography.labelLarge)
                        if (ownedKawaii.size < 8) {
                            Text(
                                "还有 ${8 - ownedKawaii.size} 款在商店等你 · 🛒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            items(ownedKawaii) { id ->
                                val sel = player.avatar == id
                                Box(
                                    Modifier
                                        .padding(4.dp)
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (sel) 3.dp else 1.dp,
                                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                        )
                                        .clickable { vm.setAvatar(id); showAvatarPicker = false },
                                ) {
                                    com.brainquest.game.ui.components.KawaiiAvatar(
                                        variant = id.removePrefix("kawaii_").toIntOrNull() ?: 0,
                                        size = 56.dp,
                                    )
                                }
                            }
                        }
                        if (customFiles.isNotEmpty()) {
                            Text("🖼 我的上传", style = MaterialTheme.typography.labelLarge)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            ) {
                                items(customFiles) { id ->
                                    val sel = player.avatar == id
                                    val ctxF = androidx.compose.ui.platform.LocalContext.current
                                    val bmp = remember(id) {
                                        runCatching {
                                            android.graphics.BitmapFactory.decodeFile(
                                                java.io.File(ctxF.filesDir, "avatars/" + id.removePrefix("custom://")).absolutePath,
                                            )
                                        }.getOrNull()
                                    }
                                    if (bmp != null) {
                                        Box(
                                            Modifier
                                                .padding(4.dp)
                                                .size(56.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .border(
                                                    width = if (sel) 3.dp else 1.dp,
                                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = androidx.compose.foundation.shape.CircleShape,
                                                )
                                                .clickable { vm.setAvatar(id); showAvatarPicker = false },
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "我的头像",
                                                modifier = Modifier.fillMaxWidth(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text("⭐ 经典表情", style = MaterialTheme.typography.labelLarge)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            items(classic) { emoji ->
                                Box(
                                    Modifier
                                        .padding(3.dp)
                                        .size(44.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { vm.setAvatar(emoji); showAvatarPicker = false },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        Button(
                            onClick = {
                                showAvatarPicker = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) { Text("🖼 从相册选择照片") }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showAvatarPicker = false }) { Text("关闭") }
                },
            )
        }

        if (showRename) {
            var newName by remember { mutableStateOf(player.nickname) }
            var checking by remember { mutableStateOf(false) }
            var takenMsg by remember { mutableStateOf<String?>(null) }
            var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
            val scope = rememberCoroutineScope()
            fun saveName(name: String) { vm.rename(name); showRename = false }
            AlertDialog(
                onDismissRequest = { if (!checking) showRename = false },
                title = { Text("修改昵称") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(12); takenMsg = null },
                            singleLine = true,
                            label = { Text("最多 12 个字") },
                            isError = takenMsg != null,
                            supportingText = {
                                if (takenMsg != null) Text(takenMsg!!, color = MaterialTheme.colorScheme.error)
                            },
                        )
                        if (suggestions.isNotEmpty()) {
                            Text(
                                "换个没被占用的试试：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestions.take(3).forEach { s ->
                                    AssistChip(
                                        onClick = { newName = s.take(12); takenMsg = null },
                                        label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !checking && newName.isNotBlank(),
                        onClick = {
                            checking = true; takenMsg = null
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                // 阻塞式查重必须放 IO 线程；服务器不可达时 best-effort 放行（下次联机仍会登记）
                                val res = com.brainquest.game.net.PkClient.checkNameAvailability(
                                    player.pkServerUrl, newName, player.identity,
                                )
                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    checking = false
                                    when {
                                        res == null -> saveName(newName)
                                        res.first -> { takenMsg = "「$newName」已被占用，换一个吧"; suggestions = res.second }
                                        else -> saveName(newName)
                                    }
                                }
                            }
                        },
                    ) { Text(if (checking) "校验中…" else "确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }, enabled = !checking) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
