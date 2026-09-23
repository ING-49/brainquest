"""ProfileScreen：改名 + 头像选择面板（Q版 8 款 + 相册上传 + 经典 emoji）"""
src = open('app/src/main/java/com/brainquest/game/ui/meta/ProfileScreen.kt', encoding='utf-8').read()

old = '''import androidx.compose.foundation.clickable'''
new = '''import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size'''
assert old in src
src = src.replace(old, new, 1)
old = '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue'''
new = '''import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale'''
assert old in src
src = src.replace(old, new, 1)

old = '''        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(player.avatar, 64)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(player.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Lv.${levelForXp(player.xp)} · 累计经验 ${player.xp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatChip("🪙", "金币", "${player.coins}")
                }'''
new = '''        var showAvatarPicker by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val ctx = getApplication<android.app.Application>()
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
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { showAvatarPicker = true }) {
                        AvatarBadge(player.avatar, 64)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f).clickable { showRename = true }) {
                        Text(player.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Lv.${levelForXp(player.xp)} · 累计经验 ${player.xp} · 点击头像换装，点击昵称改名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatChip("🪙", "金币", "${player.coins}")
                }'''
assert old in src
src = src.replace(old, new, 1)

# 头像选择对话框 + 改名对话框（追加在 Composable 末尾 return 前——放在最后）
old = '''        MenuCard("⚙️ 设置与更新", "音效 · 震动 · 热更新 · APK升级") { nav.navigate(Routes.SETTINGS) }
    }
}'''
new = '''        MenuCard("⚙️ 设置与更新", "音效 · 震动 · 热更新 · APK升级") { nav.navigate(Routes.SETTINGS) }

        if (showAvatarPicker) {
            val kawaii = listOf(
                "kawaii_0", "kawaii_1", "kawaii_2", "kawaii_3",
                "kawaii_4", "kawaii_5", "kawaii_6", "kawaii_7",
            )
            val classic = listOf("🧑‍🎓", "🐻", "🐱", "🦊", "🐼", "🦁", "🐸", "🐵", "🦉", "🤖", "👻", "🧙")
            val customFiles = remember {
                java.io.File(getApplication<android.app.Application>().filesDir, "avatars")
                    .listFiles { f -> f.extension == "jpg" }?.map { "custom://${it.name}" } ?: emptyList()
            }
            AlertDialog(
                onDismissRequest = { showAvatarPicker = false },
                title = { Text("选择头像") },
                text = {
                    Column {
                        Text("✨ Q 版头像", style = MaterialTheme.typography.labelLarge)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            items(kawaii) { id ->
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
                                    val ctx = getApplication<android.app.Application>()
                                    val bmp = remember(id) {
                                        runCatching {
                                            android.graphics.BitmapFactory.decodeFile(
                                                java.io.File(ctx.filesDir, "avatars/" + id.removePrefix("custom://")).absolutePath,
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
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("修改昵称") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(12) },
                        singleLine = true,
                        label = { Text("最多 12 个字") },
                    )
                },
                confirmButton = {
                    Button(onClick = { vm.rename(newName); showRename = false }) { Text("确定") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showRename = false }) { Text("取消") }
                },
            )
        }
    }
}'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/ui/meta/ProfileScreen.kt', 'w', encoding='utf-8').write(src)
print('ProfileScreen 头像/改名 OK')
