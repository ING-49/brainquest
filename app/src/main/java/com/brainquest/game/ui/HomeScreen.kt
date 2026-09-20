package com.brainquest.game.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.Items
import com.brainquest.game.data.levelForXp

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    var checkInReward by remember { mutableStateOf(0) }

    val checkedIn = player.lastCheckIn == vm.today()
    val dailyDone = vm.isDailyDone()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶部欢迎卡
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(player.avatar)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("你好，${player.nickname}！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Lv.${levelForXp(player.xp)} · 连续挑战 ${player.dailyStreak} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatChip("🪙", "金币", "${player.coins}")
                }
                XpBar(player.xp, Modifier.padding(top = 10.dp))
            }
        }

        // 签到
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("📅 每日签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (checkedIn) "今日已签到，明天再来～" else "签到领金币，连续越多奖越多",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { checkInReward = vm.checkIn() },
                    enabled = !checkedIn,
                ) { Text(if (checkedIn) "已签到" else "签到") }
            }
        }

        // 每日挑战
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { nav.navigate(Routes.DAILY) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("每日挑战", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("10 道混合题，坚持天数越多奖励越丰厚", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (dailyDone) "✅" else "GO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // 小游戏入口
        Text("🎮 小游戏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { nav.navigate(Routes.SUBJECTS) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚔️", style = MaterialTheme.typography.headlineLarge)
                    Text("速算英雄", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("答题战斗闯关", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { nav.navigate(Routes.KLOTSKI) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("🧱", style = MaterialTheme.typography.headlineLarge)
                    Text("华容道", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("滑动移块救曹操", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { nav.navigate(Routes.GOMOKU) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚫", style = MaterialTheme.typography.headlineLarge)
                    Text("五子棋", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("挑战电脑棋力", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { nav.navigate(Routes.SNAKE) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("🐍", style = MaterialTheme.typography.headlineLarge)
                    Text("贪吃蛇", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("滑动吃豆变长", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { nav.navigate(Routes.PK) },
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🌐", style = MaterialTheme.typography.headlineLarge)
                Text("联机对战", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("快速匹配 / 好友房间 / 局域网", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 快捷入口
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AssistChip(onClick = { nav.navigate(Routes.WRONGBOOK) }, label = { Text("📖 错题本 ${player.wrongBook.size}") })
            AssistChip(onClick = { nav.navigate(Routes.SHOP) }, label = { Text("🛒 商店") })
            AssistChip(onClick = { nav.navigate(Routes.ACHIEVEMENTS) }, label = { Text("🏅 成就 ${player.achievements.size}") })
        }

        // 我的道具
        SectionCard("🎒 我的道具") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Items.all.forEach { def ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(def.emoji, style = MaterialTheme.typography.titleLarge)
                        Text("×${player.items[def.id] ?: 0}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(def.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 最佳成绩
        if (player.bestScores.isNotEmpty()) {
            SectionCard("🏆 最佳成绩") {
                player.bestScores.entries.sortedByDescending { it.value }.take(5).forEach { (k, v) ->
                    val name = when {
                        k.startsWith("hrd_") -> "华容道·${k.removePrefix("hrd_")}（最少步数）"
                        k == "gomoku_wins" -> "五子棋·总胜场"
                        k == "gomoku_best_streak" -> "五子棋·最佳连胜"
                        k == "snake_best" -> "贪吃蛇·最高分"
                        else -> k
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text("$v", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
