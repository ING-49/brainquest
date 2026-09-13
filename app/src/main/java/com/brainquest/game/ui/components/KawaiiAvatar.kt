package com.brainquest.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Q 版二次元风头像：马卡龙底色 × 发色 × 表情（微笑/眨眼），代码绘制无版权风险。
 * variant 0~7。
 */
private data class KawaiiStyle(val bg: Color, val hair: Color, val accent: Color)

private val STYLES = listOf(
    KawaiiStyle(Color(0xFFFFE3EC), Color(0xFF8D6E63), Color(0xFFFF8FAB)), // 樱粉
    KawaiiStyle(Color(0xFFE3F2FD), Color(0xFF4A4A4A), Color(0xFF90CAF9)), // 蓝蓝
    KawaiiStyle(Color(0xFFF3E5F5), Color(0xFF6D4C41), Color(0xFFCE93D8)), // 香芋
    KawaiiStyle(Color(0xFFE8F5E9), Color(0xFF33691E), Color(0xFF81C784)), // 抹茶
    KawaiiStyle(Color(0xFFFFF3E0), Color(0xFF5D4037), Color(0xFFFFB74D)), // 奶黄
    KawaiiStyle(Color(0xFFE0F7FA), Color(0xFF00695C), Color(0xFF4DD0E1)), // 薄荷
    KawaiiStyle(Color(0xFFFCE4EC), Color(0xFFAD1457), Color(0xFFF48FB1)), // 草莓
    KawaiiStyle(Color(0xFFEFEBE9), Color(0xFF3E2723), Color(0xFFBCAAA4)), // 奶咖
)

@Composable
fun KawaiiAvatar(variant: Int, size: Dp, modifier: Modifier = Modifier) {
    val style = STYLES[variant.coerceIn(0, STYLES.size - 1)]
    val wink = variant % 2 == 1
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            // 背景圆
            drawCircle(style.bg, radius = w / 2f)
            // 脸（下半大圆）
            drawCircle(Color(0xFFFFF1E0), radius = w * 0.34f, center = Offset(w / 2f, h * 0.62f))
            // 刘海（上半弧形头发）
            drawArc(
                color = style.hair,
                startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(w * 0.16f, h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.68f, w * 0.68f),
            )
            // 发饰蝴蝶结（accent）
            drawCircle(style.accent, radius = w * 0.055f, center = Offset(w * 0.24f, h * 0.26f))
            // 眼睛
            val eyeY = h * 0.62f
            val eyeDx = w * 0.115f
            if (wink) {
                // 眨眼：右眼为弧线
                drawArc(
                    color = Color(0xFF333333), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(w / 2f + eyeDx - w * 0.045f, eyeY - w * 0.045f),
                    size = androidx.compose.ui.geometry.Size(w * 0.09f, w * 0.09f),
                    style = Stroke(width = w * 0.018f, cap = StrokeCap.Round),
                )
            } else {
                drawCircle(Color(0xFF333333), radius = w * 0.048f, center = Offset(w / 2f + eyeDx, eyeY))
                drawCircle(Color.White, radius = w * 0.016f, center = Offset(w / 2f + eyeDx + w * 0.014f, eyeY - w * 0.016f))
            }
            drawCircle(Color(0xFF333333), radius = w * 0.048f, center = Offset(w / 2f - eyeDx, eyeY))
            drawCircle(Color.White, radius = w * 0.016f, center = Offset(w / 2f - eyeDx + w * 0.014f, eyeY - w * 0.016f))
            // 腮红
            drawCircle(
                style.accent.copy(alpha = 0.45f), radius = w * 0.045f,
                center = Offset(w / 2f - eyeDx - w * 0.055f, eyeY + w * 0.05f),
            )
            drawCircle(
                style.accent.copy(alpha = 0.45f), radius = w * 0.045f,
                center = Offset(w / 2f + eyeDx + w * 0.055f, eyeY + w * 0.05f),
            )
            // 嘴（微笑弧）
            drawArc(
                color = Color(0xFF6D4C41), startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(w / 2f - w * 0.04f, eyeY + w * 0.035f),
                size = androidx.compose.ui.geometry.Size(w * 0.08f, w * 0.06f),
                style = Stroke(width = w * 0.015f, cap = StrokeCap.Round),
            )
        }
    }
}
