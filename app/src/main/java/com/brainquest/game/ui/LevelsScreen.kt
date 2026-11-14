package com.brainquest.game.ui

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.data.subjectKey

private fun subjectColor(subject: String): Color = when (subject) {
    Subjects.MATH -> Color(0xFFEF6C00)
    Subjects.LOGIC -> Color(0xFF7B1FA2)
    Subjects.ENGLISH -> Color(0xFF1976D2)
    Subjects.SCIENCE -> Color(0xFF00897B)
    Subjects.CODING -> Color(0xFF455A64)
    Subjects.ADV_MATH -> Color(0xFFC62828)
    Subjects.LIN_ALG -> Color(0xFF5D4037)
    Subjects.PROBABILITY -> Color(0xFFF9A825)
    Subjects.RF_CIRCUITS -> Color(0xFFAD1457)
    Subjects.COMMUNICATION -> Color(0xFF283593)
    else -> Color(0xFF607D8B)
}

/** 批30：继续冒险目标（key/科目名/关卡/已通关数） */
private data class ContinueTarget(val key: String, val subject: String, val level: Int, val cleared: Int)

/** 科目选择（闯关地图） */
@Composable
fun SubjectsScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    // 预计算每科解锁状态：必须在 LazyColumn 之外完成。
    // 若在 item 内做顺序累加，滚动重组会乱序重算导致解锁状态错乱（已修复的 bug）。
    val subjectRows = remember(player.levelStars) {
        var prev = true
        Subjects.all.map { subject ->
            val key = subjectKey(subject)
            val cleared = (1..8).count { (player.levelStars["${key}_$it"] ?: 0) > 0 }
            val unlocked = prev
            prev = prev && cleared >= 2 // 下一科目需本科目通过2关
            Triple(subject, unlocked, cleared)
        }
    }

    // 批30：继续冒险目标（同样预计算）——取「有进度且未通关」的最后一个已解锁科目里
    // 第一关未通关的关卡（前一关必已拿星，故必定已解锁）；零进度新手回退到第一个可玩关；
    // 全部通关则两者皆 null
    val cont = remember(player.levelStars) {
        var best: ContinueTarget? = null
        var first: ContinueTarget? = null
        var prev = true
        Subjects.all.forEach { subject ->
            val key = subjectKey(subject)
            val cleared = (1..8).count { (player.levelStars["${key}_$it"] ?: 0) > 0 }
            val unlocked = prev
            prev = prev && cleared >= 2
            if (unlocked) {
                val nextLevel = (1..8).firstOrNull { (player.levelStars["${key}_$it"] ?: 0) == 0 }
                if (nextLevel != null) {
                    val t = ContinueTarget(key, subject, nextLevel, cleared)
                    if (first == null) first = t
                    if (cleared > 0) best = t
                }
            }
        }
        best ?: first
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🗺️ 闯关地图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "选择科目开始冒险 · 已获 ${player.totalStars} ★",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 批30：继续冒险主 CTA（页面唯一主按钮，一键直达上次进度关卡）
        // 批32：白描边呼吸（唯一主 CTA 有生命感）+ 按压缩放
        val ctaPulseTr = rememberInfiniteTransition(label = "ctaPulse")
        val ctaPulse by ctaPulseTr.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "ctaPulseA",
        )
        val ctaInteraction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(ctaInteraction, 0.98f)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
                    ),
                )
                .border(1.6.dp, Color.White.copy(alpha = 0.25f + 0.25f * ctaPulse), RoundedCornerShape(14.dp))
                .clickable(interactionSource = ctaInteraction, indication = null) {
                    if (cont != null) nav.navigate(Routes.battle(cont.key, cont.level))
                    else nav.navigate(Routes.levels(subjectKey(Subjects.all.first())))
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    // 批31：金色渐变播放钮——全卡唯一暖色焦点，视线先落这里
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFC94D), Color(0xFFF9A825))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 16.sp) }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(
                    if (cont != null) {
                        if (cont.level == 8) "继续冒险 · BOSS 战" else "继续冒险 · 第 ${cont.level} 关"
                    } else "🏆 全部通关！重温冒险",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    if (cont != null) "${cont.subject} · ${cont.cleared}/8 已通关" else "再去第一科刷新纪录吧",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        // 批25：2 列网格（10 科一屏半看完，替代旧版单列长列表）；预计算解锁状态不变
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(subjectRows.chunked(2).size) { rowIdx ->
                val rowSubjects = subjectRows.chunked(2)[rowIdx]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSubjects.forEach { (subject, unlocked, cleared) ->
                        val key = subjectKey(subject)
                        val shape = RoundedCornerShape(14.dp)
                        val subInteraction = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .pressScale(subInteraction, 0.965f)
                                .weight(1f)
                                .clip(shape)
                                .background(
                                    if (unlocked) subjectColor(subject).copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape,
                                )
                                .border(
                                    1.dp,
                                    if (unlocked) subjectColor(subject).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape,
                                )
                                .clickable(interactionSource = subInteraction, indication = null, enabled = unlocked) { nav.navigate(Routes.levels(key)) }
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .background(
                                            if (unlocked) subjectColor(subject).copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (unlocked) Subjects.emoji(subject) else "🔒",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (unlocked) subjectColor(subject) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                    Text(subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        if (unlocked) "${Subjects.desc(subject)}" else "前一科通过 2 关解锁",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (unlocked) {
                                    // 批31：科目色实心胶囊 CTA，替代裸文本
                                    Text(
                                        "开始冒险",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(subjectColor(subject))
                                            .padding(horizontal = 9.dp, vertical = 3.dp),
                                    )
                                } else {
                                    Text(
                                        "未解锁",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "★$cleared/8",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cleared >= 2) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (unlocked) {
                                // 批31：星级进度条（视觉量化闯关进度，替代纯数字）
                                BqProgressBar(
                                    progress = cleared / 8f,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    height = 5.dp,
                                    color = subjectColor(subject),
                                )
                            }
                        }
                    }
                    repeat(2 - rowSubjects.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 单个科目的关卡列表 */
@Composable
fun LevelListScreen(vm: AppViewModel, nav: NavHostController, subject: String) {
    val player by vm.player.collectAsState()
    val key = subjectKey(subject)

    // 预计算关卡解锁状态（同理：不能在 LazyColumn item 内累加）
    val levelRows = remember(player.levelStars, key) {
        var prev = true
        (1..8).map { level ->
            val stars = player.levelStars["${key}_$level"] ?: 0
            val unlocked = prev
            prev = prev && stars > 0
            Triple(level, unlocked, stars)
        }
    }
    // 批31：下一个要打的关卡 = 第一个已解锁未通关（高亮「进行中」）
    val nextLevel = levelRows.firstOrNull { it.second && it.third == 0 }?.first
    val clearedCount = levelRows.count { it.third > 0 }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(subject, onBack = { nav.popBackStack() }, subtitle = Subjects.desc(subject))
        // 批31：科目通关进度条（header 直接量化全科进度）
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BqProgressBar(
                progress = clearedCount / 8f,
                modifier = Modifier.weight(1f),
                height = 6.dp,
                color = subjectColor(subject),
            )
            Text(
                " $clearedCount/8",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = subjectColor(subject),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(levelRows.size) { idx ->
                val (level, unlocked, stars) = levelRows[idx]
                val isBoss = level == 8
                val isNext = unlocked && level == nextLevel

                val lvInteraction = remember { MutableInteractionSource() }
                Card(
                    onClick = { if (unlocked) nav.navigate(Routes.battle(key, level)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(lvInteraction, 0.98f),
                    enabled = unlocked,
                    interactionSource = lvInteraction,
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            unlocked -> MaterialTheme.colorScheme.surfaceContainerLow
                            else -> MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    ),
                    border = if (isNext) BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    when {
                                        !unlocked -> MaterialTheme.colorScheme.surfaceVariant
                                        isBoss -> Color(0xFFFFEBEE)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (!unlocked) "🔒" else if (isBoss) "👹" else "$level",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                if (isBoss) "BOSS 战" else "第 $level 关",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (isBoss) "最强敌人，谨慎挑战！" else "难度 ${level / 2 + 1} · 答题攻击敌人",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isNext) {
                            // 批31：进行中徽章
                            Text(
                                "▶ 进行中",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        } else {
                            Text(
                                starText(stars),
                                color = Color(0xFFFFB300),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
