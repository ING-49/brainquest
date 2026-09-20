package com.brainquest.game.game.snake

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 贪吃蛇（2D）：滑动或方向键转向，吃豆变长，撞墙/咬到自己结束 */
@Composable
fun SnakeScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var game by remember { mutableStateOf(SnakeGame()) }
    var paused by remember { mutableStateOf(false) }
    var rewarded by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }

    // 游戏循环：每 tick 前进一步，速度随分数加快
    LaunchedEffect(game, paused, game.gameOver) {
        while (!game.gameOver && !paused) {
            delay(game.tickMs)
            game.step()
        }
    }

    // 结算（一局一次）
    LaunchedEffect(game, game.gameOver) {
        if (game.gameOver && !rewarded) {
            rewarded = true
            newRecord = vm.reportBest("snake_best", game.score)
            if (game.score > 0) {
                vm.addCoins(game.score / 2)
                vm.addXp(game.score / 3)
            }
            Sfx.play(context, player.soundOn, SfxType.LOSE)
            showResult = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        PageHeader("🐍 贪吃蛇", onBack = { nav.popBackStack() }, subtitle = "滑动屏幕或按方向键转向 · 吃到豆子变长")

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🍎 得分 ${game.score}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("长度 ${game.snake.size}", style = MaterialTheme.typography.titleSmall)
            Text(
                "最高分 ${player.bestScores["snake_best"] ?: 0}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 棋盘：滑动转向
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / game.cols
            val ver = game.version  // 读版本号触发重组
            val body = game.snake
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * game.rows)
                    .background(Color(0xFF1B5E20), RoundedCornerShape(10.dp))
                    .pointerInput(game, paused) {
                        var acc = Offset.Zero
                        val step = 30.dp.toPx()
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero },
                            onDrag = { change, drag ->
                                change.consume()
                                acc += drag
                                if (acc.getDistance() > step) {
                                    if (abs(acc.x) > abs(acc.y)) {
                                        game.turn(0, if (acc.x > 0) 1 else -1)
                                    } else {
                                        game.turn(if (acc.y > 0) 1 else -1, 0)
                                    }
                                    acc = Offset.Zero
                                }
                            },
                        )
                    },
            ) {
                // 食物
                Box(
                    Modifier
                        .offset(x = cell * game.food.second, y = cell * game.food.first)
                        .size(cell)
                        .padding(cell * 0.15f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE53935), CircleShape))
                }
                // 蛇身（头深身浅）
                body.forEachIndexed { i, (r, c) ->
                    Box(
                        Modifier
                            .offset(x = cell * c, y = cell * r)
                            .size(cell)
                            .padding(cell * 0.08f)
                            .background(
                                if (i == 0) Color(0xFF7CB342) else Color(0xFFAED581),
                                RoundedCornerShape(cell * 0.25f),
                            ),
                    )
                }
                if (!game.started) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "滑动屏幕开始",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (paused && !game.gameOver) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("⏸ 已暂停", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 方向键（模拟器 / 单手操作）
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(onClick = { game.turn(-1, 0) }) { Text("↑") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { game.turn(0, -1) }) { Text("←") }
                    OutlinedButton(onClick = { game.turn(1, 0) }) { Text("↓") }
                    OutlinedButton(onClick = { game.turn(0, 1) }) { Text("→") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { paused = !paused },
                enabled = !game.gameOver && game.started,
            ) { Text(if (paused) "▶️ 继续" else "⏸ 暂停") }
            OutlinedButton(onClick = {
                game = SnakeGame(); rewarded = false; showResult = false; paused = false; newRecord = false
            }) { Text("🔄 重开一局") }
        }
        Text(
            "💡 分数越高速度越快；撞墙或咬到自己即结束。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (showResult) {
            AlertDialog(
                onDismissRequest = { showResult = false },
                title = { Text("🕹️ 游戏结束") },
                text = {
                    Column {
                        Text("得分 ${game.score} · 长度 ${game.snake.size}", style = MaterialTheme.typography.titleMedium)
                        if (newRecord) Text("🏅 新纪录！", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        game = SnakeGame(); rewarded = false; showResult = false; paused = false; newRecord = false
                    }) { Text("再来一局") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResult = false }) { Text("看看棋盘") }
                },
            )
        }
    }
}
