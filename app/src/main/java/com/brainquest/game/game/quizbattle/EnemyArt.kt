package com.brainquest.game.game.quizbattle

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.sin

/** 批61（卡2）：速算英雄怪种自绘——七系 + BOSS 全部代码绘制。
 *  坑20：emoji 不承担唯一关键视觉（老设备字体缺失/跨设备渲染不一致）；
 *  剪影互异（50% 缩放可辨）；明暗按批54 地牢矢量标准三件套：
 *  下半暗部 + 左上高光 + 顶部轮廓光。所有几何按画布短边 s 等比缩放。 */
object EnemyArt {

    /** kind：0-6 = BattleState.enemyKinds 七系；7 = BOSS */
    fun drawEnemy(kind: Int, scope: DrawScope) {
        with(scope) {
            val s = minOf(size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            when (kind) {
                0 -> goblin(s, cx, cy)
                1 -> microbe(s, cx, cy)
                2 -> devil(s, cx, cy)
                3 -> bat(s, cx, cy)
                4 -> golem(s, cx, cy)
                5 -> mage(s, cx, cy)
                6 -> skeleton(s, cx, cy)
                else -> boss(s, cx, cy)
            }
        }
    }

    /** 顶部轮廓光：上半圆细弧（批54 三件套之一） */
    private fun DrawScope.rim(cx: Float, cy: Float, r: Float) {
        drawArc(
            Color(0x40FFFFFF), startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f),
            style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
        )
    }

    /** 左上高光（批54 三件套之一） */
    private fun DrawScope.hl(cx: Float, cy: Float, r: Float) {
        drawOval(Color(0x40FFFFFF), Offset(cx - r * 0.55f, cy - r * 0.72f), Size(r * 0.5f, r * 0.28f))
    }

    /** 下半暗部：裁剪进圆形剪影的底部渐变（批54 三件套之一） */
    private fun DrawScope.bottomShade(cx: Float, cy: Float, r: Float) {
        val clip = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        clipPath(clip) {
            drawRect(
                Brush.verticalGradient(listOf(Color(0x00000000), Color(0x4D000000))),
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f),
            )
        }
    }

    /** 0 哥布林：赤红天狗面具——圆脸长鼻白眉怒目 */
    private fun DrawScope.goblin(s: Float, cx: Float, cy: Float) {
        val r = s * 0.40f
        drawCircle(Color(0xFFE15A5A), r, Offset(cx, cy))
        bottomShade(cx, cy, r)
        hl(cx, cy, r)
        rim(cx, cy, r)
        // 长鼻（天狗特征，向下）
        val nose = Path().apply {
            moveTo(cx - s * 0.06f, cy + s * 0.01f)
            lineTo(cx + s * 0.06f, cy + s * 0.01f)
            lineTo(cx, cy + s * 0.26f)
            close()
        }
        drawPath(nose, Color(0xFFC62828))
        // 白眉（倒八字）
        drawLine(Color(0xFFECEFF1), Offset(cx - r * 0.66f, cy - r * 0.42f), Offset(cx - r * 0.14f, cy - r * 0.16f), s * 0.035f)
        drawLine(Color(0xFFECEFF1), Offset(cx + r * 0.66f, cy - r * 0.42f), Offset(cx + r * 0.14f, cy - r * 0.16f), s * 0.035f)
        // 怒目
        drawCircle(Color(0xFFECEFF1), r * 0.17f, Offset(cx - r * 0.36f, cy + r * 0.02f))
        drawCircle(Color(0xFFECEFF1), r * 0.17f, Offset(cx + r * 0.36f, cy + r * 0.02f))
        drawCircle(Color(0xFF263238), r * 0.08f, Offset(cx - r * 0.36f, cy + r * 0.03f))
        drawCircle(Color(0xFF263238), r * 0.08f, Offset(cx + r * 0.36f, cy + r * 0.03f))
        // 白须两撇
        drawLine(Color(0xFFECEFF1), Offset(cx - r * 0.32f, cy + r * 0.55f), Offset(cx - r * 0.10f, cy + r * 0.80f), s * 0.03f)
        drawLine(Color(0xFFECEFF1), Offset(cx + r * 0.32f, cy + r * 0.55f), Offset(cx + r * 0.10f, cy + r * 0.80f), s * 0.03f)
    }

    /** 1 史莱姆（微生物）：橙焰圆球 + 放射短刺 + 内核斑点 */
    private fun DrawScope.microbe(s: Float, cx: Float, cy: Float) {
        val r = s * 0.36f
        for (i in 0 until 10) {   // 放射短刺（先画，根部被球体压住）
            val a = i * 0.6283f + 0.31f
            drawLine(
                Color(0xFFE64A19),
                Offset(cx + cos(a) * r * 0.90f, cy + sin(a) * r * 0.90f),
                Offset(cx + cos(a) * r * 1.16f, cy + sin(a) * r * 1.16f),
                s * 0.045f,
            )
        }
        drawCircle(Color(0xFFFF7043), r, Offset(cx, cy))
        bottomShade(cx, cy, r)
        hl(cx, cy, r)
        rim(cx, cy, r)
        // 双眼 + 内核斑
        drawCircle(Color(0xFF263238), r * 0.10f, Offset(cx - r * 0.28f, cy - r * 0.15f))
        drawCircle(Color(0xFF263238), r * 0.10f, Offset(cx + r * 0.28f, cy - r * 0.15f))
        drawCircle(Color(0xFFFFCCBC), r * 0.16f, Offset(cx + r * 0.18f, cy + r * 0.28f))
        drawCircle(Color(0xFFBF360C), r * 0.07f, Offset(cx - r * 0.12f, cy + r * 0.40f))
    }

    /** 2 小恶魔：绛紫圆脸 + 双弯角 + 得意笑弧 */
    private fun DrawScope.devil(s: Float, cx: Float, cy: Float) {
        val r = s * 0.38f
        // 双角（先画，压在脸后）
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.62f, cy - r * 0.42f)
                lineTo(cx - r * 0.92f, cy - r * 1.02f)
                lineTo(cx - r * 0.26f, cy - r * 0.70f)
                close()
            },
            Color(0xFF8E1848),
        )
        drawPath(
            Path().apply {
                moveTo(cx + r * 0.62f, cy - r * 0.42f)
                lineTo(cx + r * 0.92f, cy - r * 1.02f)
                lineTo(cx + r * 0.26f, cy - r * 0.70f)
                close()
            },
            Color(0xFF8E1848),
        )
        drawCircle(Color(0xFFC2185B), r, Offset(cx, cy))
        bottomShade(cx, cy, r)
        hl(cx, cy, r)
        rim(cx, cy, r)
        drawCircle(Color(0xFFFFEB3B), r * 0.13f, Offset(cx - r * 0.34f, cy - r * 0.14f))
        drawCircle(Color(0xFFFFEB3B), r * 0.13f, Offset(cx + r * 0.34f, cy - r * 0.14f))
        drawCircle(Color(0xFF263238), r * 0.06f, Offset(cx - r * 0.34f, cy - r * 0.13f))
        drawCircle(Color(0xFF263238), r * 0.06f, Offset(cx + r * 0.34f, cy - r * 0.13f))
        // 笑弧
        drawArc(
            Color(0xFF4A0E22), startAngle = 25f, sweepAngle = 130f, useCenter = false,
            topLeft = Offset(cx - r * 0.45f, cy - r * 0.05f), size = Size(r * 0.9f, r * 0.75f),
            style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
        )
    }

    /** 3 蝙蝠：紫翼三角膜 + 小圆身 + 立耳 */
    private fun DrawScope.bat(s: Float, cx: Float, cy: Float) {
        val r = s * 0.25f
        // 双翼（锯齿膜，先画）
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.5f, cy - r * 0.1f)
                lineTo(cx - r * 1.9f, cy - r * 0.9f)
                lineTo(cx - r * 1.35f, cy + r * 0.4f)
                lineTo(cx - r * 0.95f, cy - r * 0.1f)
                lineTo(cx - r * 0.7f, cy + r * 0.55f)
                close()
            },
            Color(0xFF7E57C2),
        )
        drawPath(
            Path().apply {
                moveTo(cx + r * 0.5f, cy - r * 0.1f)
                lineTo(cx + r * 1.9f, cy - r * 0.9f)
                lineTo(cx + r * 1.35f, cy + r * 0.4f)
                lineTo(cx + r * 0.95f, cy - r * 0.1f)
                lineTo(cx + r * 0.7f, cy + r * 0.55f)
                close()
            },
            Color(0xFF7E57C2),
        )
        // 立耳
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.5f, cy - r * 0.75f); lineTo(cx - r * 0.30f, cy - r * 1.45f); lineTo(cx - r * 0.05f, cy - r * 0.85f); close()
            },
            Color(0xFF9575CD),
        )
        drawPath(
            Path().apply {
                moveTo(cx + r * 0.5f, cy - r * 0.75f); lineTo(cx + r * 0.30f, cy - r * 1.45f); lineTo(cx + r * 0.05f, cy - r * 0.85f); close()
            },
            Color(0xFF9575CD),
        )
        drawCircle(Color(0xFF9575CD), r, Offset(cx, cy))
        bottomShade(cx, cy, r)
        hl(cx, cy, r)
        drawCircle(Color(0xFFFFEB3B), r * 0.18f, Offset(cx - r * 0.32f, cy - r * 0.15f))
        drawCircle(Color(0xFFFFEB3B), r * 0.18f, Offset(cx + r * 0.32f, cy - r * 0.15f))
        drawCircle(Color(0xFF263238), r * 0.08f, Offset(cx - r * 0.32f, cy - r * 0.14f))
        drawCircle(Color(0xFF263238), r * 0.08f, Offset(cx + r * 0.32f, cy - r * 0.14f))
    }

    /** 4 石头人：石板方身 + 裂纹 + 方眼怒眉刻嘴 */
    private fun DrawScope.golem(s: Float, cx: Float, cy: Float) {
        val w = s * 0.72f; val h = s * 0.78f
        val x0 = cx - w / 2f; val y0 = cy - h / 2f
        drawRoundRect(Color(0xFF78909C), Offset(x0, y0), Size(w, h), CornerRadius(w * 0.18f))
        // 下半暗部（矩形版：直接渐变盖下半）
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0x00000000), Color(0x4D000000))),
            Offset(x0, y0), Size(w, h), CornerRadius(w * 0.18f),
        )
        // 顶部高光条（批54 三件套的矩形版）
        drawRoundRect(Color(0x26FFFFFF), Offset(x0 + w * 0.08f, y0 + h * 0.06f), Size(w * 0.84f, h * 0.10f), CornerRadius(w * 0.05f))
        // 裂纹
        drawLine(Color(0xFF455A64), Offset(cx - w * 0.10f, y0 + h * 0.42f), Offset(cx + w * 0.02f, y0 + h * 0.58f), s * 0.02f)
        drawLine(Color(0xFF455A64), Offset(cx + w * 0.02f, y0 + h * 0.58f), Offset(cx - w * 0.06f, y0 + h * 0.74f), s * 0.02f)
        // 方眼 + 怒眉
        drawRect(Color(0xFFECEFF1), Offset(cx - w * 0.26f, cy - h * 0.18f), Size(w * 0.16f, h * 0.12f))
        drawRect(Color(0xFFECEFF1), Offset(cx + w * 0.10f, cy - h * 0.18f), Size(w * 0.16f, h * 0.12f))
        drawRect(Color(0xFF37474F), Offset(cx - w * 0.23f, cy - h * 0.14f), Size(w * 0.10f, h * 0.06f))
        drawRect(Color(0xFF37474F), Offset(cx + w * 0.13f, cy - h * 0.14f), Size(w * 0.10f, h * 0.06f))
        drawLine(Color(0xFF37474F), Offset(cx - w * 0.30f, cy - h * 0.28f), Offset(cx - w * 0.08f, cy - h * 0.23f), s * 0.025f)
        drawLine(Color(0xFF37474F), Offset(cx + w * 0.30f, cy - h * 0.28f), Offset(cx + w * 0.08f, cy - h * 0.23f), s * 0.025f)
        // 刻线嘴
        drawLine(Color(0xFF37474F), Offset(cx - w * 0.18f, cy + h * 0.22f), Offset(cx + w * 0.18f, cy + h * 0.22f), s * 0.03f)
    }

    /** 5 暗影法师：深靛尖帽斗篷 + 阴影脸 + 幽光双目 */
    private fun DrawScope.mage(s: Float, cx: Float, cy: Float) {
        val r = s * 0.34f
        // 尖帽
        drawPath(
            Path().apply {
                moveTo(cx, cy - s * 0.46f)
                lineTo(cx + r * 1.05f, cy + r * 0.15f)
                lineTo(cx - r * 1.05f, cy + r * 0.15f)
                close()
            },
            Color(0xFF5C6BC0),
        )
        // 帽檐
        drawRoundRect(Color(0xFF3F51B5), Offset(cx - r * 1.05f, cy + r * 0.02f), Size(r * 2.1f, r * 0.28f), CornerRadius(r * 0.14f))
        // 斗篷肩
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.95f, cy + s * 0.5f)
                lineTo(cx - r * 0.55f, cy + r * 0.42f)
                lineTo(cx + r * 0.55f, cy + r * 0.42f)
                lineTo(cx + r * 0.95f, cy + s * 0.5f)
                close()
            },
            Color(0xFF4553A0),
        )
        // 阴影脸 + 幽光双目
        drawCircle(Color(0xFF1A2033), r * 0.72f, Offset(cx, cy + r * 0.30f))
        drawCircle(Color(0xFF80DEEA).copy(alpha = 0.30f), r * 0.24f, Offset(cx - r * 0.28f, cy + r * 0.26f))
        drawCircle(Color(0xFF80DEEA).copy(alpha = 0.30f), r * 0.24f, Offset(cx + r * 0.28f, cy + r * 0.26f))
        drawCircle(Color(0xFF80DEEA), r * 0.13f, Offset(cx - r * 0.28f, cy + r * 0.26f))
        drawCircle(Color(0xFF80DEEA), r * 0.13f, Offset(cx + r * 0.28f, cy + r * 0.26f))
    }

    /** 6 骷髅武士：骨白颅骨 + 方下颌 + 黑洞眼窝 + 牙缝 */
    private fun DrawScope.skeleton(s: Float, cx: Float, cy: Float) {
        val r = s * 0.38f
        val scy = cy - r * 0.15f
        drawCircle(Color(0xFFECEFF1), r, Offset(cx, scy))
        bottomShade(cx, scy, r)
        hl(cx, scy, r)
        rim(cx, scy, r)
        // 下颌
        drawRoundRect(Color(0xFFCFD8DC), Offset(cx - r * 0.52f, cy + r * 0.45f), Size(r * 1.04f, r * 0.55f), CornerRadius(r * 0.16f))
        // 眼窝
        drawOval(Color(0xFF263238), Offset(cx - r * 0.55f, scy - r * 0.30f), Size(r * 0.42f, r * 0.34f))
        drawOval(Color(0xFF263238), Offset(cx + r * 0.13f, scy - r * 0.30f), Size(r * 0.42f, r * 0.34f))
        // 鼻腔三角
        drawPath(
            Path().apply {
                moveTo(cx, scy + r * 0.10f); lineTo(cx - r * 0.10f, scy + r * 0.30f); lineTo(cx + r * 0.10f, scy + r * 0.30f); close()
            },
            Color(0xFF263238),
        )
        // 牙缝
        for (i in -2..2) {
            drawLine(Color(0xFF90A4AE), Offset(cx + i * r * 0.20f, cy + r * 0.50f), Offset(cx + i * r * 0.20f, cy + r * 0.74f), s * 0.02f)
        }
    }

    /** 7 BOSS：大鬼面——双弯角 + 幽光眼 + 獠牙咧嘴 + 金冠菱 */
    private fun DrawScope.boss(s: Float, cx: Float, cy: Float) {
        val r = s * 0.42f
        // 双弯角（先画）
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.55f)
                lineTo(cx - r * 0.95f, cy - r * 1.05f)
                lineTo(cx - r * 1.08f, cy - r * 0.50f)
                lineTo(cx - r * 0.62f, cy - r * 0.28f)
                close()
            },
            Color(0xFF6D2A2A),
        )
        drawPath(
            Path().apply {
                moveTo(cx + r * 0.55f, cy - r * 0.55f)
                lineTo(cx + r * 0.95f, cy - r * 1.05f)
                lineTo(cx + r * 1.08f, cy - r * 0.50f)
                lineTo(cx + r * 0.62f, cy - r * 0.28f)
                close()
            },
            Color(0xFF6D2A2A),
        )
        drawCircle(Color(0xFFB23A3A), r, Offset(cx, cy))
        bottomShade(cx, cy, r)
        hl(cx, cy, r)
        rim(cx, cy, r)
        // 幽光眼
        drawOval(Color(0xFFFF8A80), Offset(cx - r * 0.52f, cy - r * 0.35f), Size(r * 0.34f, r * 0.20f))
        drawOval(Color(0xFFFF8A80), Offset(cx + r * 0.18f, cy - r * 0.35f), Size(r * 0.34f, r * 0.20f))
        // 怒眉
        drawLine(Color(0xFF3A1414), Offset(cx - r * 0.62f, cy - r * 0.52f), Offset(cx - r * 0.14f, cy - r * 0.30f), s * 0.035f)
        drawLine(Color(0xFF3A1414), Offset(cx + r * 0.62f, cy - r * 0.52f), Offset(cx + r * 0.14f, cy - r * 0.30f), s * 0.035f)
        // 咧嘴獠牙
        drawRoundRect(Color(0xFF3A1414), Offset(cx - r * 0.40f, cy + r * 0.22f), Size(r * 0.80f, r * 0.26f), CornerRadius(r * 0.10f))
        drawRect(Color(0xFFECEFF1), Offset(cx - r * 0.32f, cy + r * 0.22f), Size(r * 0.14f, r * 0.16f))
        drawRect(Color(0xFFECEFF1), Offset(cx - r * 0.06f, cy + r * 0.22f), Size(r * 0.14f, r * 0.16f))
        drawRect(Color(0xFFECEFF1), Offset(cx + r * 0.20f, cy + r * 0.22f), Size(r * 0.14f, r * 0.16f))
        // 金冠菱（额头）
        drawPath(
            Path().apply {
                moveTo(cx, cy - r * 0.62f); lineTo(cx + r * 0.10f, cy - r * 0.44f); lineTo(cx, cy - r * 0.26f); lineTo(cx - r * 0.10f, cy - r * 0.44f); close()
            },
            Color(0xFFFFD54F),
        )
    }
}
