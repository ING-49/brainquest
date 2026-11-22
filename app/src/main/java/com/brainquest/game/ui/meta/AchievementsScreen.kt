package com.brainquest.game.ui.meta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.brainquest.game.data.Achievements
import com.brainquest.game.ui.CoinIcon
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.BqProgressBar
import com.brainquest.game.ui.pressScale

@Composable
fun AchievementsScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val unlockedCount = player.achievements.size
    val total = Achievements.all.size

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            "🏅 成就墙",
            onBack = { nav.popBackStack() },
            subtitle = "已解锁 $unlockedCount / $total",
        )

        // 汇总头卡：解锁率进度
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏅", style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("解锁进度 $unlockedCount / $total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (unlockedCount == total) "全部达成，传奇！🎉" else "再解锁 ${total - unlockedCount} 个拿满奖励",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BqProgressBar(
                    progress = if (total == 0) 0f else unlockedCount.toFloat() / total,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    trackColor = MaterialTheme.colorScheme.surface,
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(Achievements.all.size) { i ->
                val def = Achievements.all[i]
                val unlocked = player.achievements.containsKey(def.id)
                // 成就卡无点击行为（批58：仅描边+彩底图标强化）
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 批58：图标彩底块（已解锁=金色 14% 底，未解锁=灰底），FuncChip 同视觉语言
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (unlocked) Color(0xFFFFB300).copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (unlocked) def.icon else "🔒", style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(def.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 未解锁且有进度定义：显示进度条（如 128/200）
                            if (!unlocked) {
                                val p = def.progress?.invoke(player)
                                if (p != null && p.second > 0 && p.first < p.second) {
                                    BqProgressBar(
                                        progress = (p.first.toFloat() / p.second).coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        height = 6.dp,
                                    )
                                    Text(
                                        "${p.first.coerceAtMost(p.second)} / ${p.second}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        // 批58：已解锁=金色圆徽章；待解锁=CoinIcon+数字（替代 "✅"/"+N 金币" 裸文本）
                        if (unlocked) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFFFC94D), Color(0xFFF9A825))),
                                        CircleShape,
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E)) }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                                CoinIcon(13.dp)
                                Text("+${def.reward}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            }
                        }
                    }
                }
            }
        }
    }
}
