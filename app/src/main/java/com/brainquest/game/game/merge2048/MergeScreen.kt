package com.brainquest.game.game.merge2048

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType

private fun tierColor(tier: Int): Color = when (tier) {
    1 -> Color(0xFFEEE4DA)
    2 -> Color(0xFFEDE0C8)
    3 -> Color(0xFFF2B179)
    4 -> Color(0xFFF59563)
    5 -> Color(0xFFF67C5F)
    6 -> Color(0xFFF65E3B)
    7 -> Color(0xFFEDCF72)
    8 -> Color(0xFFEDCC61)
    else -> Color(0xFF3C3A32)
}

private fun tierTextColor(tier: Int): Color =
    if (tier <= 2) Color(0xFF776E65) else Color.White

@Composable
fun MergeScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf("english") }
    val tiersData = remember(mode) { PairsData.load(context, mode) }
    var game by remember(mode) { mutableStateOf(tiersData?.let { Merge2048Game(it.tiers) }) }
    var tick by remember(mode) { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var rewardGiven by remember { mutableStateOf(false) }

    BackHandler(enabled = game != null) { mode = "" ; game = null }

    if (game == null || tiersData == null) {
        // 模式选择
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader("🧩 知识2048", onBack = { nav.popBackStack() }, subtitle = "滑动让互为配对的瓷砖撞在一起")
            Text("选择模式", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            PairsData.modes.forEach { (id, name) ->
                val best = player.bestScores["g2048_$id"] ?: 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    onClick = { mode = id; game = PairsData.load(context, id)?.let { Merge2048Game(it.tiers) } },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (id == "english") "🔤" else "🔢", style = MaterialTheme.typography.headlineMedium)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("历史最高 $best 分", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        return
    }

    val g = game!!

    fun finishIfNeeded() {
        if ((g.gameOver || g.win) && !showResult && !rewardGiven) {
            rewardGiven = true
            val isNewBest = vm.reportBest("g2048_$mode", g.score)
            vm.addCoins(g.score / 20 + if (g.win) 60 else 0)
            vm.addXp(g.score / 30 + if (g.win) 40 else 0)
            Sfx.play(context, player.soundOn, if (g.win) SfxType.WIN else SfxType.LOSE)
            showResult = true
        }
    }

    fun doMove(dir: Dir) {
        if (g.gameOver || g.win) return
        if (g.move(dir)) {
            Sfx.play(context, player.soundOn, SfxType.MERGE)
            finishIfNeeded()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(mode) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val (dx, dy) = dragAmount
                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        if (dx > 40) doMove(Dir.RIGHT) else if (dx < -40) doMove(Dir.LEFT)
                    } else {
                        if (dy > 40) doMove(Dir.DOWN) else if (dy < -40) doMove(Dir.UP)
                    }
                }
            },
    ) {
        PageHeader("🧩 ${tiersData.name}", onBack = { game = null }, subtitle = "滑动合并 · 配对正确才能合成")

        // key=version：棋盘/数值每次变化强制重建这一段
        key(g.version) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("得分 ${g.score}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (g.lastWrongText != null) "❌ ${g.lastWrongText}" else "合成对子升级 · 目标 ${1 shl tiersData.tiers.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (g.lastWrongText != null) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { game = Merge2048Game(tiersData.tiers); rewardGiven = false; showResult = false }) {
                Text("重开")
            }
        }

        // 棋盘
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .aspectRatio(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFBBADA0)),
        ) {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(g.size) { r ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(g.size) { c ->
                            val tile = g.grid[r][c]
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        tile?.let { tierColor(it.tier) } ?: Color(0xFFCDC1B4),
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tile != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            g.tileText(tile),
                                            color = tierTextColor(tile.tier),
                                            fontSize = if ((g.tileText(tile).length) > 6) 12.sp else 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 15.sp,
                                            maxLines = 2,
                                        )
                                        Text(
                                            "${tile.value}",
                                            color = tierTextColor(tile.tier).copy(alpha = 0.7f),
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 方向按钮（配合滑动手势，模拟器上点按更方便）
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { doMove(Dir.UP) }) { Text("⬆️") }
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                Button(onClick = { doMove(Dir.LEFT) }) { Text("⬅️") }
                Button(onClick = { doMove(Dir.DOWN) }) { Text("⬇️") }
                Button(onClick = { doMove(Dir.RIGHT) }) { Text("➡️") }
            }
        }
        } // key(g.version)
    }

    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text(if (g.win) "🏆 合成成功！" else "游戏结束") },
            text = {
                Column {
                    Text("得分 ${g.score} · 合并 ${g.merges} 次 · 配错 ${g.mistakes} 次")
                    Text(if (g.win) "拿到了最高级瓷砖，奖励丰厚！" else "差一点，再来一局？")
                }
            },
            confirmButton = {
                Button(onClick = { game = Merge2048Game(tiersData.tiers); rewardGiven = false; showResult = false }) { Text("再来一局") }
            },
            dismissButton = { TextButton(onClick = { showResult = false; game = null }) { Text("返回") } },
        )
    }
}
