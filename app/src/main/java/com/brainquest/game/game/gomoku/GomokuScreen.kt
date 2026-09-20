package com.brainquest.game.game.gomoku

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay

/** 五子棋：人机对战（本地 AI，离线可玩） */
@Composable
fun GomokuScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var difficulty by remember { mutableIntStateOf(1) }
    var game by remember { mutableStateOf(GomokuGame(15, 1)) }
    var sessionWins by remember { mutableIntStateOf(0) }
    var sessionLosses by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var rewarded by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    // AI 回合：稍作停顿再落子（有"思考"感，也避免瞬间连点）
    LaunchedEffect(game, game.moves, game.winner) {
        if (!game.finished && !game.playerTurn) {
            delay(350)
            game.aiTurn()
        }
    }

    // 结算与奖励（一局一次）
    LaunchedEffect(game, game.winner) {
        if (game.winner != 0 && !rewarded) {
            rewarded = true
            if (game.winner == 1) {
                sessionWins++
                streak++
                vm.reportBest("gomoku_wins", (player.bestScores["gomoku_wins"] ?: 0) + 1)
                vm.reportBest("gomoku_best_streak", streak)
                vm.addCoins(40 + streak * 5)
                vm.addXp(25)
                Sfx.play(context, player.soundOn, SfxType.WIN)
            } else {
                sessionLosses++
                streak = 0
                Sfx.play(context, player.soundOn, SfxType.LOSE)
            }
            showResult = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        PageHeader("⚫ 五子棋", onBack = { nav.popBackStack() }, subtitle = "你执黑先手 · 先连成五子者胜")

        // 难度
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("简单", "普通", "困难").forEachIndexed { i, label ->
                FilterChip(
                    selected = difficulty == i,
                    onClick = {
                        difficulty = i
                        game = GomokuGame(15, i)
                        rewarded = false; showResult = false
                    },
                    label = { Text(label) },
                )
            }
            Text(
                "总胜场 ${player.bestScores["gomoku_wins"] ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本局 ${sessionWins} 胜 ${sessionLosses} 负", style = MaterialTheme.typography.labelLarge)
            Text("🔥 连胜 $streak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                if (game.finished) "本局结束" else if (game.playerTurn) "轮到你" else "电脑思考中…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 棋盘 15×15
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / game.size
            val ver = game.version  // 读版本号触发重组
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * game.size)
                    .background(Color(0xFFE0B980), RoundedCornerShape(10.dp)),
            ) {
                for (r in 0 until game.size) {
                    for (c in 0 until game.size) {
                        val v = game.cell(r, c)
                        val isLast = game.lastMove == (r to c)
                        val inWin = game.winLine.contains(r to c)
                        Box(
                            Modifier
                                .offset(x = cell * c, y = cell * r)
                                .size(cell)
                                .clickable(enabled = game.playerTurn && !game.finished) {
                                    if (game.playerPlace(r, c)) Sfx.play(context, player.soundOn, SfxType.CLICK)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v == 0) {
                                Box(Modifier.size(2.dp).background(Color(0x33000000), CircleShape))
                            } else {
                                Box(
                                    Modifier
                                        .size(cell * 0.82f)
                                        .background(if (v == 1) Color(0xFF212121) else Color(0xFFFAFAFA), CircleShape)
                                        .border(
                                            BorderStroke(
                                                if (inWin) 2.dp else 1.dp,
                                                if (inWin) Color(0xFFFFC107) else Color(0x33000000),
                                            ),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLast) {
                                        Box(Modifier.size(cell * 0.22f).background(Color(0xFFE53935), CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { game.undo(); rewarded = false; showResult = false },
                enabled = game.moves > 0 && !game.thinking && !game.finished,
            ) { Text("↩️ 悔棋") }
            OutlinedButton(onClick = {
                game.reset(); rewarded = false; showResult = false
            }) { Text("🔄 重开一局") }
        }
        Text(
            "💡 电脑棋力：简单（会走神）/ 普通（攻守均衡）/ 困难（带两步预判）。悔棋可撤销双方各一手。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (showResult) {
            AlertDialog(
                onDismissRequest = { showResult = false },
                title = { Text(if (game.winner == 1) "🏆 五子连珠，你赢了！" else "💻 电脑先连成五子") },
                text = {
                    Column {
                        Text("本局：${sessionWins} 胜 ${sessionLosses} 负", style = MaterialTheme.typography.bodyMedium)
                        if (game.winner == 1) {
                            Text("🔥 当前连胜 $streak 场", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        game.reset(); rewarded = false; showResult = false
                    }) { Text("再来一局") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResult = false }) { Text("看看棋盘") }
                },
            )
        }
    }
}
