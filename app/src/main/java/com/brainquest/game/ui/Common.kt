package com.brainquest.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/** 统计卡：三层结构（图标 / 数值 / 文字），数值单行不折行 */
@Composable
fun StatChip(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** 金币图标：代码绘制（🪙 属较新 emoji，部分设备字体缺失会显示为方框） */
@Composable
fun CoinIcon(size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color(0xFFFBBF24), radius = r)
        drawCircle(Color(0xFFD97706), radius = r * 0.82f, style = Stroke(width = r * 0.16f))
        drawCircle(Color(0xFFFDE68A), radius = r * 0.4f)
    }
}

/** 金币行：图标 + 数值（替代 "🪙 N" 文本拼接，图标随文字大小缩放） */
@Composable
fun CoinText(
    amount: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight? = FontWeight.Bold,
    color: Color = Color.Unspecified,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        CoinIcon(size = (style.fontSize.value * 0.95f).dp)
        Text("$amount", style = style, fontWeight = fontWeight, color = color)
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
        BqProgressBar(
            progress = if (need == 0) 1f else cur.toFloat() / need,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            height = 8.dp,
        )
    }
}

/** 统一进度条：药丸圆角、无缺口（M3 自带条在未满时有断层感），进度变化处直接换用本组件 */
@Composable
fun BqProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
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
