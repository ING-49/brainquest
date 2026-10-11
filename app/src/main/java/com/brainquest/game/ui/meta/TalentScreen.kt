package com.brainquest.game.ui.meta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.brainquest.game.ui.CoinIcon
import com.brainquest.game.ui.CoinText
import com.brainquest.game.ui.PageHeader

/** 天赋卡定义（id 对应 PlayerState.dungeonPerks / AppViewModel.dungeonPerkCost 的键） */
private data class TalentDef(val id: String, val icon: String, val name: String, val perLevel: String, val effect: (Int) -> String)

private val TALENTS = listOf(
    TalentDef("hp", "❤️", "生命强化", "+8 生命上限 / 级") { n -> "生命上限 +${8 * n}" },
    TalentDef("atk", "⚔️", "攻击强化", "+1 攻击 / 级") { n -> "攻击 +${1 * n}" },
    TalentDef("spd", "👟", "敏捷强化", "+4 移速 / 级") { n -> "移速 +${4 * n}" },
    TalentDef("crit", "🎯", "暴击强化", "+3% 暴击率 / 级") { n -> "暴击率 +${3 * n}%" },
    TalentDef("skillcd", "🌀", "技能强化", "技能冷却 −6% / 级") { n -> "技能冷却 −${6 * n}%" },
    TalentDef("gold", "💰", "财富天赋", "开局金币 +30 / 级") { n -> "开局金币 +${30 * n}" },
)

/** 地牢局外养成：六系永久天赋（金币升级，进地牢自动生效）。横屏子页（批14：唯一入口是地牢大厅） */
@Composable
fun TalentScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val totalLevels = player.dungeonPerks.values.sum()

    Row(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xF20B0E14)).padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        // 左栏 30%：页头 + 汇总（总等级/金币/说明）
        Column(Modifier.weight(0.30f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("⭐ 天赋养成", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFFFD54F))
            Text("局外永久强化 · 地牢专属", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF78909C))
            Spacer(Modifier.height(10.dp))
            Text(
                "← 返回大厅",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF82B1FF),
                modifier = Modifier.clickable { nav.popBackStack() }.padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("天赋总等级", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF78909C))
            Text("$totalLevels", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.brainquest.game.ui.CoinIcon(20.dp)
                Text(
                    " ${player.coins}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFFFD54F),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("金币升级 · 进地牢时自动生效", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF78909C))
        }
        Spacer(Modifier.width(18.dp))
        // 右栏 70%：3×2 天赋卡网格
        Column(Modifier.weight(0.70f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {

        // 六张天赋卡 3×2（批13 横屏：两行三列铺满，不再纵向长列表）
        TALENTS.chunked(3).forEach { rowCards ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCards.forEach { t ->
                    val level = player.dungeonPerks[t.id] ?: 0
                    val cost = vm.dungeonPerkCost(t.id)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.icon, style = MaterialTheme.typography.titleLarge)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("${t.name} · Lv.$level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (level == 0) t.perLevel
                                        else "已生效 ${t.effect(level)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = { vm.buyDungeonPerk(t.id) },
                                enabled = player.coins >= cost,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoinIcon(size = 13.dp)
                                    Text(" $cost", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                // 补位空槽（保持三列对齐）
                repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        }   // 右栏（3×2 网格）
    }   // 根 Row
}

