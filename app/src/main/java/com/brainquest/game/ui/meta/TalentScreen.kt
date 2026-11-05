package com.brainquest.game.ui.meta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.game.dungeon.render.GameActionButton
import com.brainquest.game.game.dungeon.render.GameBackdrop
import com.brainquest.game.game.dungeon.render.GamePanel
import com.brainquest.game.game.dungeon.render.GameUi
import com.brainquest.game.game.dungeon.render.GameVignette
import com.brainquest.game.game.dungeon.render.PanelTitle
import com.brainquest.game.game.dungeon.render.RoundIconButton
import com.brainquest.game.ui.CoinIcon

/** 天赋卡定义（id 对应 PlayerState.dungeonPerks / AppViewModel.dungeonPerkCost 的键） */
private data class TalentDef(
    val id: String,
    val icon: String,
    val name: String,
    val perLevel: String,
    val accent: Color,
    val effect: (Int) -> String,
)

private val TALENTS = listOf(
    TalentDef("hp", "❤️", "生命强化", "+8 生命上限 / 级", Color(0xFFEF5350)) { n -> "生命上限 +${8 * n}" },
    TalentDef("atk", "⚔️", "攻击强化", "+1 攻击 / 级", Color(0xFFFFB74D)) { n -> "攻击 +${1 * n}" },
    TalentDef("spd", "👟", "敏捷强化", "+4 移速 / 级", Color(0xFF4FC3F7)) { n -> "移速 +${4 * n}" },
    TalentDef("crit", "🎯", "暴击强化", "+3% 暴击率 / 级", Color(0xFFBA68C8)) { n -> "暴击率 +${3 * n}%" },
    TalentDef("skillcd", "🌀", "技能强化", "技能冷却 −6% / 级", Color(0xFF4DB6AC)) { n -> "技能冷却 −${6 * n}%" },
    TalentDef("gold", "💰", "财富天赋", "开局金币 +30 / 级", Color(0xFFFFD54F)) { n -> "开局金币 +${30 * n}" },
)

/** 地牢局外养成：六系永久天赋（金币升级，进地牢自动生效）。横屏子页（批24 统一游戏 UI） */
@Composable
fun TalentScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val totalLevels = player.dungeonPerks.values.sum()
    val maxedCount = TALENTS.count { (player.dungeonPerks[it.id] ?: 0) >= vm.dungeonPerkMax(it.id) }

    Box(Modifier.fillMaxSize()) {
        GameBackdrop(accent = Color(0xFFBA68C8))
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            // 顶栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("←", onClick = { nav.popBackStack() }, size = 38.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", fontSize = 18.sp)
                        Text(
                            "天赋养成",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GameUi.Gold,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                    Text("局外永久强化 · 进入地牢时自动生效", fontSize = 10.sp, color = GameUi.TextDim)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xB30B1018))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(11.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoinIcon(15.dp)
                    Text(
                        " ${player.coins}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GameUi.Gold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // 左：汇总面板；右：3×2 天赋卡
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    Modifier.width(200.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    GamePanel(Modifier.fillMaxWidth(), accent = GameUi.Gold, contentPadding = 14.dp) {
                        PanelTitle("养成总览", "六系天赋 · 金币升级")
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$totalLevels",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = GameUi.Gold,
                            )
                            Text(
                                " 级",
                                style = MaterialTheme.typography.labelMedium,
                                color = GameUi.TextDim,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Text("天赋总等级", fontSize = 10.sp, color = GameUi.TextDim)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .background(Color(0x33000000)).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoinIcon(14.dp)
                            Text(
                                " ${player.coins}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GameUi.Gold,
                                modifier = Modifier.weight(1f),
                            )
                            Text("金币余额", fontSize = 9.sp, color = GameUi.TextFaint)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已满级 $maxedCount / ${TALENTS.size}",
                            fontSize = 10.sp,
                            color = if (maxedCount == TALENTS.size) GameUi.Xp else GameUi.TextDim,
                        )
                        Spacer(Modifier.height(8.dp))
                        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                            val frac = totalLevels.toFloat() / (TALENTS.size * 10)
                            drawRoundRect(Color(0x33FFFFFF), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(GameUi.GoldDeep, GameUi.Gold)),
                                size = androidx.compose.ui.geometry.Size(size.width * frac.coerceIn(0f, 1f), size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            )
                        }
                        Text(
                            "金币来自地牢与答题，升级后立即对所有新局生效。",
                            fontSize = 9.sp,
                            color = GameUi.TextFaint,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    TALENTS.chunked(3).forEach { rowCards ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowCards.forEach { t ->
                                val level = player.dungeonPerks[t.id] ?: 0
                                val cost = vm.dungeonPerkCost(t.id)
                                val maxLevel = vm.dungeonPerkMax(t.id)
                                val maxed = level >= maxLevel
                                GamePanel(Modifier.weight(1f), accent = t.accent, contentPadding = 12.dp) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                                .background(t.accent.copy(alpha = 0.16f))
                                                .border(1.dp, t.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(t.icon, fontSize = 18.sp) }
                                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                            Text(
                                                t.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                            )
                                            Text(
                                                "Lv.$level / $maxLevel",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = t.accent,
                                            )
                                        }
                                    }
                                    // 等级刻度：一眼看清成长进度
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        repeat(maxLevel) { i ->
                                            Box(
                                                Modifier.weight(1f).height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (i < level) t.accent else Color(0x26FFFFFF)),
                                            )
                                        }
                                    }
                                    Text(
                                        if (level == 0) t.perLevel else "已生效 ${t.effect(level)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GameUi.TextDim,
                                        modifier = Modifier.padding(top = 6.dp),
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    GameActionButton(
                                        if (maxed) "MAX" else "升级 · $cost",
                                        onClick = { vm.buyDungeonPerk(t.id) },
                                        enabled = !maxed && player.coins >= cost,
                                        accent = t.accent,
                                        textColor = Color(0xFF120F00),
                                        height = 34.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        GameVignette()
    }
}
