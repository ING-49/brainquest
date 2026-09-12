package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.Achievements
import com.brainquest.game.ui.PageHeader

@Composable
fun AchievementsScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            "🏅 成就墙",
            onBack = { nav.popBackStack() },
            subtitle = "已解锁 ${player.achievements.size} / ${Achievements.all.size}",
        )
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(Achievements.all.size) { i ->
                val def = Achievements.all[i]
                val unlocked = player.achievements.containsKey(def.id)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (unlocked) def.icon else "🔒", style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(def.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(def.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (unlocked) "✅" else "+${def.reward}🪙",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
