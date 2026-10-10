package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** 地牢局外养成：六系永久天赋（金币升级，进地牢自动生效） */
@Composable
fun TalentScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val totalLevels = player.dungeonPerks.values.sum()

    Column(
        Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState()),   // 6 卡可能超出屏幕（批12 扩容）
    ) {
        PageHeader(
            "⭐ 天赋养成",
            onBack = { nav.popBackStack() },
            subtitle = "局外永久强化 · 地牢专属",
        )

        // 汇总卡：总等级 + 金币余额
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("天赋总等级 $totalLevels", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("金币升级 · 进地牢时自动生效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CoinText(player.coins)
            }
        }

        // 三张天赋卡：当前等级 / 已生效 / 下一级费用（金币不足置灰）
        TALENTS.forEach { t ->
            val level = player.dungeonPerks[t.id] ?: 0
            val cost = vm.dungeonPerkCost(t.id)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.icon, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("${t.name} · Lv.$level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (level == 0) "未升级 · 下一级 ${t.perLevel}"
                            else "已生效 ${t.effect(level)} · 下一级 ${t.perLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { vm.buyDungeonPerk(t.id) },
                        enabled = player.coins >= cost,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(size = 13.dp)
                            Text(" $cost", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
