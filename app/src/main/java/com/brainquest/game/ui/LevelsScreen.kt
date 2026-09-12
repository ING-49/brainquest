package com.brainquest.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🗺️ 闯关地图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "选择科目开始冒险 · 已获 ${player.totalStars} ★",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(subjectRows.size) { idx ->
                val (subject, unlocked, cleared) = subjectRows[idx]
                val key = subjectKey(subject)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = unlocked) { nav.navigate(Routes.levels(key)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .background(
                                    if (unlocked) subjectColor(subject).copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unlocked) Subjects.emoji(subject) else "🔒",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (unlocked) subjectColor(subject) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (unlocked) Subjects.desc(subject) else "先在前一科目通过 2 关解锁",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$cleared/8", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("关卡", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(subject, onBack = { nav.popBackStack() }, subtitle = Subjects.desc(subject))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(levelRows.size) { idx ->
                val (level, unlocked, stars) = levelRows[idx]
                val isBoss = level == 8

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = unlocked) { nav.navigate(Routes.battle(key, level)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
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
