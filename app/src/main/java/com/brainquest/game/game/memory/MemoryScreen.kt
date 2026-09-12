package com.brainquest.game.game.memory

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.starText
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay

private data class LevelSpec(val rows: Int, val cols: Int) {
    val pairs: Int get() = rows * cols / 2
    val label: String get() = "${rows}×${cols}"
}

@Composable
fun MemoryScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current
    val sets = remember { MemoryData.loadSets(context) }

    var chosenSet by remember { mutableStateOf<MemorySet?>(null) }
    var spec by remember { mutableStateOf(LevelSpec(4, 4)) }
    var game by remember { mutableStateOf<MemoryGame?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var rewardGiven by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    // ---------- 选择页 ----------
    if (chosenSet == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader("🃏 记忆翻牌", onBack = { nav.popBackStack() }, subtitle = "翻开两张卡，配对知识点")
            Text("选择知识集", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            sets.forEach { set ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    onClick = { chosenSet = set; game = null },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📚", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(set.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${set.pairs.size} 组知识对", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        return
    }

    val set = chosenSet!!

    // ---------- 尺寸选择 / 游戏 ----------
    if (game == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader("🃏 ${set.name}", onBack = { chosenSet = null })
            Text("选择难度", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            listOf(LevelSpec(3, 4), LevelSpec(4, 4), LevelSpec(4, 5)).forEach { s ->
                val best = player.bestScores["memory_${set.name}_${s.label}"] ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    onClick = {
                        spec = s
                        game = MemoryGame(set, s.rows, s.cols)
                        rewardGiven = false
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎴", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("${s.label}（${s.pairs} 对）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("最佳：$best 步通关", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        return
    }

    val g = game!!

    LaunchedEffect(g) {
        while (!g.finished) delay(500)
        if (!rewardGiven) {
            rewardGiven = true
            val stars = g.stars()
            val coins = 20 + stars * 15
            vm.reportBest("memory_${set.name}_${spec.label}", g.moves)
            vm.addCoins(coins)
            vm.addXp(10 + stars * 8)
            Sfx.play(context, player.soundOn, SfxType.WIN)
            showResult = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // key=version：卡牌状态变化强制重建这一段
        key(g.version) {
        PageHeader("🃏 ${set.name} ${spec.label}", onBack = { game = null }, subtitle = "步数 ${g.moves} · 已配对 ${g.matchedPairs}/${g.totalPairs}")

        hint?.let {
            Text(
                it,
                color = Color(0xFF2E7D32),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(spec.cols),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items((0 until spec.rows * spec.cols).toList()) { index ->
                val matched = g.isMatched(index)
                val flipped = g.isFlipped(index)
                val bg by animateColorAsState(
                    targetValue = when {
                        matched -> Color(0xFFA5D6A7)
                        flipped -> Color(0xFFFFF59D)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    label = "cardBg",
                )
                Card(
                    modifier = Modifier
                        .aspectRatio(0.85f)
                        .clickable(enabled = !busy && !flipped && !matched) {
                            when (g.tap(index)) {
                                "flip", "none" -> Sfx.play(context, player.soundOn, SfxType.FLIP)
                                "match" -> {
                                    Sfx.play(context, player.soundOn, SfxType.CORRECT)
                                    hint = "✅ ${g.cardText(index)} ↔ ${if (g.cards[index].side == 0) set.pairs[g.cards[index].pairIndex].right else set.pairs[g.cards[index].pairIndex].left}"
                                }
                                "miss" -> {
                                    Sfx.play(context, player.soundOn, SfxType.WRONG)
                                    busy = true
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = bg),
                ) {
                    Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                        if (flipped || matched) {
                            Text(
                                g.cardText(index),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp,
                                maxLines = 3,
                            )
                        } else {
                            Text("❓", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { game = MemoryGame(set, spec.rows, spec.cols); hint = null },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) { Text("🔄 重新洗牌") }
        } // key(g.version)
    }

    // 配错：短暂停顿后翻回
    if (busy) {
        LaunchedEffect(g.moves) {
            delay(900)
            g.resetFlipped()
            busy = false
        }
    }

    if (showResult) {
        val stars = g.stars()
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text("🎉 配对完成！") },
            text = {
                Column {
                    Text("评价：${starText(stars)}")
                    Text("共用 ${g.moves} 步 · 金币 +${20 + stars * 15} · 经验 +${10 + stars * 8}")
                }
            },
            confirmButton = {
                Button(onClick = { game = MemoryGame(set, spec.rows, spec.cols); showResult = false; rewardGiven = false }) { Text("再来一局") }
            },
            dismissButton = { TextButton(onClick = { showResult = false; game = null; chosenSet = null }) { Text("返回") } },
        )
    }
}
