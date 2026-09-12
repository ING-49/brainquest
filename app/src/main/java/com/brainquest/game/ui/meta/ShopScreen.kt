package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.brainquest.game.data.Items
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.theme.AppThemes

private val avatars = listOf("🧑‍🎓", "🐻", "🐱", "🦊", "🐼", "🦁", "🐸", "🐵", "🦉", "🤖", "👻", "🧙")

@Composable
fun ShopScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader("🛒 商店", onBack = { nav.popBackStack() }, subtitle = "持有金币 ${player.coins}")

        // 道具
        Text("🎒 道具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        Items.all.forEach { def ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(def.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("${def.name}（持有 ${player.items[def.id] ?: 0}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(def.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.buyItem(def.id) }, enabled = player.coins >= def.price) {
                        Text("🪙 ${def.price}")
                    }
                }
            }
        }

        // 主题
        Text("🎨 主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemes.all.forEach { theme ->
                val owned = player.ownedThemes.contains(theme.id)
                val active = player.activeTheme == theme.id
                OutlinedButton(
                    onClick = {
                        if (owned) vm.setTheme(theme.id) else if (vm.buyTheme(theme.id, theme.price)) vm.setTheme(theme.id)
                    },
                ) {
                    Text(
                        if (active) "✅${theme.name}" else if (owned) theme.name else "${theme.name} ${theme.price}",
                    )
                }
            }
        }

        // 头像
        Text("😀 头像", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().height(((avatars.size / 6 + 1) * 74).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(avatars) { emoji ->
                val owned = player.ownedAvatars.contains(emoji)
                val active = player.avatar == emoji
                Card(
                    onClick = {
                        if (owned) vm.setAvatar(emoji) else if (vm.buyAvatar(emoji, 100)) vm.setAvatar(emoji)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        Modifier.padding(6.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(emoji, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            when {
                                active -> "使用中"
                                owned -> "点击"
                                else -> "🪙100"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
