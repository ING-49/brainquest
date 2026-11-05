package com.brainquest.game.game.dungeon.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 横屏游戏 UI 组件库（批24）。
 *
 * 目标：所有游戏界面共用一套「暗色金属面板 + 金色强调 + 圆角小组件」的视觉语言，
 * 替代此前各页面随手写的裸 Text/默认 M3 Card。设计参照主流横屏手游的 HUD 分层：
 * 左上玩家信息、顶部目标信息、右上功能/地图、右下操作簇。
 */
object GameUi {
    val PanelTop = Color(0xF21C2434)
    val PanelBottom = Color(0xF2111620)
    val PanelSolid = Color(0xFF141A26)
    val PanelLight = Color(0xFF202A3C)
    val Scrim = Color(0xD905070C)
    val Border = Color(0x26FFFFFF)
    val BorderStrong = Color(0x4DFFFFFF)
    val Gold = Color(0xFFFFD54F)
    val GoldDeep = Color(0xFFFFA000)
    val Cyan = Color(0xFF82D8FF)
    val Text = Color(0xFFECEFF1)
    val TextDim = Color(0xFF90A4AE)
    val TextFaint = Color(0xFF607D8B)
    val Hp = Color(0xFFEF5350)
    val HpDeep = Color(0xFF8E1B1B)
    val Xp = Color(0xFF66BB6A)
    val Primary = Color(0xFF7C4DFF)
    val PrimaryLight = Color(0xFFB388FF)

    val panelShape = RoundedCornerShape(16.dp)
}

/** 暗色渐变面板：带 1dp 细边，可加金色顶部光条（游戏内所有信息簇的统一容器） */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GameUi.panelShape,
    accent: Color? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GameUi.PanelTop, GameUi.PanelBottom)))
            .border(1.dp, if (accent != null) accent.copy(alpha = 0.45f) else GameUi.Border, shape)
            .padding(contentPadding),
    ) {
        if (accent != null) {
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(
                    Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.85f), Color.Transparent)),
                ),
            )
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** 面板标题行：左侧竖条强调 + 标题/副标题 + 右侧自定义尾件 */
@Composable
fun PanelTitle(
    title: String,
    subtitle: String? = null,
    accent: Color = GameUi.Gold,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GameUi.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = GameUi.TextDim, maxLines = 1)
            }
        }
        trailing?.invoke()
    }
}

/** 小标签：图标 + 数值（顶栏目标信息用） */
@Composable
fun HudStatChip(
    icon: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = GameUi.Text,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x8C0B1018))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 12.sp, lineHeight = 14.sp)
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
    }
}

/** 分段式玩家血条：渐变填充 + 高光 + 四等分刻度 + 居中数值 */
@Composable
fun HudHpBar(
    frac: Float,
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    barColor: Color = GameUi.Hp,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color(0xB3000000))
            .border(1.dp, Color(0x40FFFFFF), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = frac.coerceIn(0f, 1f)
            if (f > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.85f), barColor)),
                    size = Size(size.width * f, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.55f),
                    size = Size(size.width * f, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            // 四等分刻度：让血条有「可读的剩余段」而不是一根色条
            for (i in 1..3) {
                drawLine(
                    Color(0x33000000),
                    Offset(size.width * i / 4f, 1f),
                    Offset(size.width * i / 4f, size.height - 1f),
                    1.5f,
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** 经验条：细条 + 青绿渐变 */
@Composable
fun HudXpBar(frac: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier.height(height).clip(shape).background(Color(0x99000000))
            .border(1.dp, Color(0x1FFFFFFF), shape),
    ) {
        Box(
            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(height).background(
                Brush.horizontalGradient(listOf(Color(0xFF2E7D32), GameUi.Xp, Color(0xFFB2FF59))),
            ),
        )
    }
}

/** 圆形功能键（暂停/设置/返回/背包） */
@Composable
fun RoundIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = GameUi.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xF2263044), Color(0xF20E131D))))
            .border(1.dp, GameUi.BorderStrong, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = (size.value * 0.42f).sp, color = if (enabled) tint else GameUi.TextFaint)
    }
}

/** 游戏主按钮：横向渐变 + 亮边 + 底部阴影；primary=false 时是次级描边按钮 */
@Composable
fun GameActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    accent: Color = GameUi.Primary,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(
                if (primary) Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.72f)))
                else Brush.verticalGradient(listOf(Color(0xB31A2233), Color(0xB30F141E))),
            )
            .border(
                if (primary) 1.5.dp else 1.dp,
                if (primary) Color.White.copy(alpha = 0.38f) else GameUi.BorderStrong,
                shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) textColor else GameUi.TextFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 圆形技能键：底环 + 冷却扇形 + 就绪呼吸描边 + 图标/冷却文字 */
@Composable
fun SkillKey(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    cdFrac: Float = 0f,
    ready: Boolean = true,
    ringColor: Color = GameUi.Xp,
    caption: String? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xF2202838), Color(0xF20B0F17))))
            .border(1.5.dp, GameUi.BorderStrong, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f - 2f
            if (cdFrac > 0f) {
                drawArc(
                    Color(0xCC000000), -90f, 360f * cdFrac.coerceIn(0f, 1f), useCenter = true,
                    style = Stroke(width = r),
                )
                drawArc(
                    Color(0xFF78909C).copy(alpha = 0.85f), -90f, 360f * (1f - cdFrac.coerceIn(0f, 1f)),
                    useCenter = false, style = Stroke(3f, cap = StrokeCap.Round),
                )
            }
            if (ready && cdFrac <= 0f) {
                drawCircle(ringColor.copy(alpha = 0.22f), r, style = Stroke(5f))
                drawArc(ringColor, -90f, 360f, useCenter = false, style = Stroke(2.5f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = (size.value * 0.30f).sp)
            if (caption != null) {
                Text(
                    caption,
                    fontSize = (size.value * 0.14f).sp,
                    lineHeight = (size.value * 0.16f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCFD8DC),
                )
            }
        }
    }
}

/** 大攻击键：双层圆环 + 就绪弧 + 图标 + 文案 */
@Composable
fun AttackKey(
    icon: String,
    label: String,
    progress: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xF22C3850), Color(0xF20B0F17))))
            .border(2.dp, GameUi.BorderStrong, CircleShape)
            .pointerPress(onPress, onRelease),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f - 3f
            drawCircle(GameUi.Gold.copy(alpha = 0.10f), r)
            if (progress in 0f..1f) {
                drawArc(
                    GameUi.Gold.copy(alpha = 0.85f), -90f, 360f * (1f - progress),
                    useCenter = false, style = Stroke(3.5f, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = (size.value * 0.34f).sp)
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GameUi.Gold.copy(alpha = 0.9f),
            )
        }
    }
}

/** 按住/松开回调（攻击键长按连击用） */
private fun Modifier.pointerPress(onPress: () -> Unit, onRelease: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onPress()
                    try {
                        awaitRelease()
                    } finally {
                        onRelease()
                    }
                },
            )
        },
    )

/** 模式选择卡：图标磁贴 + 标题/描述 + 选中金边与勾选点 */
@Composable
fun ModeCard(
    icon: String,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(Color(0x4DFFD54F), Color(0x1AFFD54F)))
                else Brush.horizontalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))),
            )
            .border(1.5.dp, if (selected) GameUi.Gold else GameUi.Border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color(0x33FFD54F) else Color(0x14FFFFFF))
                .border(1.dp, if (selected) Color(0x66FFD54F) else Color(0x1FFFFFFF), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 20.sp) }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) GameUi.Gold else GameUi.Text,
                maxLines = 1,
            )
            Text(desc, style = MaterialTheme.typography.labelSmall, color = GameUi.TextDim, maxLines = 1)
        }
        if (selected) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(GameUi.Gold),
                contentAlignment = Alignment.Center,
            ) { Text("✓", fontSize = 11.sp, color = Color(0xFF1A1200), fontWeight = FontWeight.Bold) }
        }
    }
}

/** 大厅左侧竖排功能入口 */
@Composable
fun LobbyNavItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(60.dp)
            .clip(shape)
            .background(if (active) Color(0x33BA68C8) else Color(0x14FFFFFF))
            .border(1.dp, if (active) Color(0x88BA68C8) else Color(0x1FFFFFFF), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Text(icon, fontSize = 20.sp)
            if (badge != null) {
                Box(
                    Modifier.padding(start = 12.dp).size(14.dp).clip(CircleShape).background(GameUi.Hp),
                    contentAlignment = Alignment.Center,
                ) { Text(badge, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Text(label, fontSize = 10.sp, color = if (active) Color(0xFFE1BEE7) else GameUi.TextDim, maxLines = 1)
    }
}

/** 统一游戏弹窗：暗色幕布 + 描边面板 + 标题栏（可关闭） */
@Composable
fun GameDialog(
    title: String,
    accent: Color = GameUi.Gold,
    onDismiss: (() -> Unit)? = null,
    width: Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(GameUi.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = width)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFA1D2536), Color(0xFA10151F))))
                .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GameUi.Text,
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                )
                if (onDismiss != null) {
                    RoundIconButton("✕", onDismiss, size = 32.dp, tint = GameUi.TextDim)
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp).height(1.dp).background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent)),
                ),
            )
            content()
        }
    }
}

/** 结算统计格：图标 / 数值 / 标签 */
@Composable
fun ResultStat(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = GameUi.Text,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66000000))
            .border(1.dp, GameUi.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 13.sp)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
            )
        }
        Text(label, fontSize = 10.sp, color = GameUi.TextDim, maxLines = 1)
    }
}

/** 横向开关/档位胶囊 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = GameUi.Gold,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent else Color(0x1FFFFFFF))
            .border(1.dp, if (selected) accent else GameUi.Border, RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF141000) else Color.White,
        )
    }
}

/** 设置行开关：整行可点，右侧开/关胶囊 */
@Composable
fun ToggleSwitchRow(
    label: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = GameUi.Xp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x33000000))
            .border(1.dp, GameUi.Border, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggle(!on) },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = GameUi.Text, modifier = Modifier.weight(1f))
        ChoicePill(if (on) "开" else "关", selected = on, onClick = { onToggle(!on) }, accent = accent)
    }
}

/** 游戏场景背景：暗色渐变 + 网格 + 两侧火把光晕 + 缓慢漂浮的尘埃 */
@Composable
fun GameBackdrop(modifier: Modifier = Modifier, accent: Color = GameUi.Gold) {
    val tr = rememberInfiniteTransition(label = "backdrop")
    val t by tr.animateFloat(0f, 6.283f, infiniteRepeatable(tween(9000, easing = LinearEasing)))
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color(0xFF080A10))
        drawRect(
            Brush.verticalGradient(listOf(Color(0x00161B29), Color(0x99161B29), Color(0x00080A10))),
            size = size,
        )
        drawRect(
            Brush.verticalGradient(listOf(Color(0x1A2A3A56), Color(0x00080A10))),
            size = Size(size.width, size.height * 0.26f),
        )
        // 远景网格
        for (col in 0..16) {
            drawLine(Color(0x0AFFFFFF), Offset(col * size.width / 16f, 0f), Offset(col * size.width / 16f, size.height), 1f)
        }
        for (row in 0..8) {
            drawLine(Color(0x0AFFFFFF), Offset(0f, row * size.height / 8f), Offset(size.width, row * size.height / 8f), 1f)
        }
        // 两侧火把/法阵光晕
        for (side in 0..1) {
            val cx = if (side == 0) size.width * 0.14f else size.width * 0.86f
            val flick = 0.7f + 0.3f * kotlin.math.sin(t * 1.7f + side * 2.1f)
            drawCircle(accent.copy(alpha = 0.055f * flick), size.height * 0.30f, Offset(cx, size.height * 0.34f))
            drawCircle(accent.copy(alpha = 0.10f * flick), size.height * 0.09f, Offset(cx, size.height * 0.34f))
            drawCircle(accent.copy(alpha = 0.16f * flick), size.height * 0.035f, Offset(cx, size.height * 0.34f))
        }
        // 地面反光
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.05f))),
            topLeft = Offset(0f, size.height * 0.66f),
            size = Size(size.width, size.height * 0.34f),
        )
        // 漂浮尘埃
        val rng = kotlin.random.Random(11)
        repeat(26) {
            val x = rng.nextFloat() * size.width
            val baseY = rng.nextFloat() * size.height
            val speed = 8f + rng.nextFloat() * 22f
            val y = (baseY - t * speed) % size.height
            val a = 0.10f + rng.nextFloat() * 0.22f
            drawCircle(accent.copy(alpha = a), 1.2f + rng.nextFloat() * 1.6f, Offset(x, if (y < 0f) y + size.height else y))
        }
    }
}

/** 全场暗角（放在内容顶层，增加画面纵深） */
@Composable
fun GameVignette(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color(0x22000000), Color(0x66000000)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = kotlin.math.hypot(size.width, size.height) / 2f,
            ),
        )
    }
}
