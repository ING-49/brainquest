package com.brainquest.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.brainquest.game.data.levelForXp
import com.brainquest.game.data.xpProgress

@Composable
fun PageHeader(title: String, onBack: () -> Unit, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatChip(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 玩家等级经验条 */
@Composable
fun XpBar(xp: Int, modifier: Modifier = Modifier) {
    val level = levelForXp(xp)
    val (cur, need) = xpProgress(xp)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Lv.$level ${"小勇者"}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("$cur / $need EXP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (need == 0) 1f else cur.toFloat() / need },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(8.dp),
        )
    }
}

/** 圆形 emoji 头像 */
@Composable
fun AvatarBadge(avatar: String, size: Int = 56) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            avatar.startsWith("kawaii_") ->
                com.brainquest.game.ui.components.KawaiiAvatar(
                    variant = avatar.removePrefix("kawaii_").toIntOrNull() ?: 0,
                    size = size.dp,
                )
            avatar.startsWith("custom://") -> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val bmp = remember(avatar) {
                    runCatching {
                        val f = java.io.File(ctx.filesDir, "avatars/" + avatar.removePrefix("custom://"))
                        android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                    }.getOrNull()
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "头像",
                        modifier = Modifier.size(size.dp).clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Text("🙂", style = MaterialTheme.typography.headlineMedium)
                }
            }
            else -> Text(avatar, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

fun starText(stars: Int): String = "★".repeat(stars) + "☆".repeat((3 - stars).coerceAtLeast(0))

val coinColor = Color(0xFFFFB300)
