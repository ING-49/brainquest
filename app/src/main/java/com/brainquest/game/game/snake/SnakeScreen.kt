package com.brainquest.game.game.snake

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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

// 深色棋盘 + 亮绿蛇头、头亮尾暗渐变：弱化背景与蛇身同色系的问题
private val BOARD_BG = Color(0xFF16301B)
private val BOARD_EDGE = Color(0xFF0D1F11)
private val GRID_LINE = Color(0x14FFFFFF)
private val HEAD_COLOR = Color(0xFF69F0AE)
private val HEAD_EDGE = Color(0xCCFFFFFF)
private val BODY_NEAR = Color(0xFF00C853)
private val BODY_FAR = Color(0xFF1B7A28)
private val FOOD_COLOR = Color(0xFFFF5252)
private val FOOD_GLINT = Color(0x99FFFFFF)

/** 贪吃蛇（2D）：滑动转向，吃豆变长，撞墙/咬到自己结束 */
@Composable
fun SnakeScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var game by remember { mutableStateOf(SnakeGame()) }
    var paused by remember { mutableStateOf(false) }
    var rewarded by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val inGame = game.started && !game.gameOver

    // 游戏循环：每 tick 前进一步（key 含 started，起步后等满一个完整间隔才走第一步）
    LaunchedEffect(game, paused, game.gameOver, game.started) {
        while (!game.gameOver && !paused) {
            delay(game.tickMs)
            game.step()
        }
    }

    // 起步音
    LaunchedEffect(game, game.started) {
        if (game.started && !game.gameOver) {
            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.START)
        }
    }

    // 吃到豆子
    LaunchedEffect(game, game.score) {
        if (game.score > 0) Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.EAT)
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
            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CRASH)
            showResult = true
            if (newRecord) {
                delay(700)
                Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.WIN)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 对局中按返回先暂停并确认，避免一按就丢掉这一局
        androidx.activity.compose.BackHandler(enabled = inGame) {
            paused = true
            confirmExit = true
        }
        PageHeader(
            "🐍 贪吃蛇",
            onBack = {
                if (inGame) {
                    paused = true
                    confirmExit = true
                } else {
                    nav.popBackStack()
                }
            },
            subtitle = "滑动屏幕转向 · 吃到豆子变长",
        )

        // 操作按钮在上方（v1.6.12）：游戏区在下方，拇指滑动不挡按钮
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { paused = !paused },
                enabled = !game.gameOver && game.started,
            ) { Text(if (paused) "▶️ 继续" else "⏸ 暂停") }
            OutlinedButton(onClick = {
                game = SnakeGame(); rewarded = false; showResult = false; paused = false; newRecord = false
            }) { Text("🔄 重开一局") }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🍎 得分 ${game.score}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("速度 ${game.speedLabel}", style = MaterialTheme.typography.titleSmall)
            Text(
                "最高分 ${player.bestScores["snake_best"] ?: 0}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 棋盘：整屏滑动转向
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / game.cols
            val ver = game.version  // 读版本号触发重组
            val body = game.snake
            val boardShape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * game.rows)
                    .clip(boardShape)
                    .background(BOARD_BG)
                    .border(BorderStroke(3.dp, BOARD_EDGE), boardShape)
                    .pointerInput(game, paused) {
                        var acc = Offset.Zero
                        val step = 30.dp.toPx()
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero },
                            onDrag = { change, drag ->
                                change.consume()
                                acc += drag
                                if (acc.getDistance() > step) {
                                    // 主轴判定，斜滑不误判
                                    val horizontal = abs(acc.x) > abs(acc.y) * 1.2f
                                    val vertical = abs(acc.y) > abs(acc.x) * 1.2f
                                    when {
                                        horizontal -> game.turn(0, if (acc.x > 0) 1 else -1)
                                        vertical -> game.turn(if (acc.y > 0) 1 else -1, 0)
                                    }
                                    acc = Offset.Zero
                                }
                            },
                        )
                    },
            ) {
                // 格线：便于判断格距
                for (i in 1 until game.cols) {
                    Box(
                        Modifier
                            .offset(x = cell * i, y = 0.dp)
                            .width(1.dp)
                            .height(cell * game.rows)
                            .background(GRID_LINE),
                    )
                }
                for (i in 1 until game.rows) {
                    Box(
                        Modifier
                            .offset(x = 0.dp, y = cell * i)
                            .width(cell * game.cols)
                            .height(1.dp)
                            .background(GRID_LINE),
                    )
                }
                // 食物
                Box(
                    Modifier
                        .offset(x = cell * game.food.second, y = cell * game.food.first)
                        .size(cell)
                        .padding(cell * 0.15f),
                    contentAlignment = Alignment.Center,
                ) {
                    BreathingFood()
                }
                // 蛇身（头亮尾暗）
                body.forEachIndexed { i, (r, c) ->
                    val shape = RoundedCornerShape(cell * 0.25f)
                    val color = if (i == 0) {
                        HEAD_COLOR
                    } else {
                        val tailT = if (body.size <= 1) 0f else (i.toFloat() / (body.size - 1)).coerceIn(0f, 1f)
                        lerp(BODY_NEAR, BODY_FAR, tailT)
                    }
                    Box(
                        Modifier
                            .offset(x = cell * c, y = cell * r)
                            .size(cell)
                            .padding(cell * 0.08f)
                            .background(color, shape)
                            .then(
                                if (i == 0) Modifier.border(BorderStroke(1.5.dp, HEAD_EDGE), shape) else Modifier,
                            ),
                    )
                }
                if (!game.started) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "滑动屏幕开始",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (paused && !game.gameOver) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⏸ 已暂停", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            "💡 滑动屏幕转向（禁止 180° 掉头）；分数越高速度越快，撞墙或咬到自己即结束。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("退出这一局？") },
                text = { Text("当前得分不会结算（不给金币和经验），退出后需要重新开始。") },
                confirmButton = {
                    Button(onClick = { confirmExit = false; nav.popBackStack() }) { Text("退出") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmExit = false; paused = false }) { Text("继续玩") }
                },
            )
        }

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

/** 食物：轻微呼吸 + 白高光，视线更容易捕捉 */
@Composable
private fun BreathingFood() {
    val transition = rememberInfiniteTransition(label = "food")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "foodScale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(FOOD_COLOR, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxSize(0.32f)
                .background(FOOD_GLINT, CircleShape),
        )
    }
}
