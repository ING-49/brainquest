package com.brainquest.game.ui.meta

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.Routes
import com.brainquest.game.data.levelForXp
import com.brainquest.game.ui.AvatarBadge
import com.brainquest.game.ui.StatChip
import com.brainquest.game.ui.XpBar

@Composable
fun ProfileScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(player.avatar, 64)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(player.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Lv.${levelForXp(player.xp)} · 累计经验 ${player.xp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatChip("🪙", "金币", "${player.coins}")
                }
                XpBar(player.xp, Modifier.padding(top = 10.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            StatChip("🎯", "答对", "${player.totalCorrect}", Modifier.weight(1f))
            StatChip("💥", "答错", "${player.totalWrong}", Modifier.weight(1f))
            StatChip("📈", "正确率", "${(player.accuracy * 100).toInt()}%", Modifier.weight(1f))
            StatChip("⭐", "星星", "${player.totalStars}", Modifier.weight(1f))
        }

        MenuCard("🏅 成就墙", "已解锁 ${player.achievements.size} 个成就") { nav.navigate(Routes.ACHIEVEMENTS) }
        MenuCard("📖 错题本", "${player.wrongBook.count { !it.mastered }} 道待复习") { nav.navigate(Routes.WRONGBOOK) }
        MenuCard("🛒 商店", "道具 · 主题 · 头像") { nav.navigate(Routes.SHOP) }
        MenuCard("⚙️ 设置与更新", "音效 · 震动 · 热更新 · APK升级") { nav.navigate(Routes.SETTINGS) }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
