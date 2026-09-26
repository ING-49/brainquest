package com.brainquest.game.game.gomoku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay

// 浅木色棋盘 + 深色网格线：原来黄褐底配白子几乎糊在一起
private val BOARD_BG = Color(0xFFF0DCB8)
private val BOARD_EDGE = Color(0xFF6D4C41)
private val GRID_LINE = Color(0x4D000000)
private val BLACK_STONE = Color(0xFF212121)
private val BLACK_STONE_EDGE = Color(0x4D000000)
private val WHITE_STONE = Color(0xFFFDFDFD)
private val WHITE_STONE_EDGE = Color(0xFF5D4037)
private val LAST_MOVE_COLOR = Color(0xFFE53935)
private val WIN_RING = Color(0xFFD81B60)
private val TURN_GLOW = Color(0xFF2E7D32)

private const val AI_DELAY_MS = 350L

/** 会话级棋局：退出页面后棋盘与战绩保留（模式选择页可「回到上一局」），进程被杀才清空 */
private object GomokuSession {
    var difficulty: Int = 1
    var game: GomokuGame? = null
    var sessionWins: Int = 0
    var sessionLosses: Int = 0
    var streak: Int = 0
    var rewarded: Boolean = false
}

/** 五子棋：模式选择（人机 / 好友同机双人）→ 对局。好友模式为两人对坐布局（顶条倒置）。 */
@Composable
fun GomokuScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var mode by remember { mutableStateOf<String?>(null) }   // 每次进入都先落在模式选择页
    var difficulty by remember { mutableIntStateOf(GomokuSession.difficulty) }
    var game by remember { mutableStateOf(GomokuSession.game ?: GomokuGame(15, difficulty)) }
    var sessionWins by remember { mutableIntStateOf(GomokuSession.sessionWins) }
    var sessionLosses by remember { mutableIntStateOf(GomokuSession.sessionLosses) }
    var streak by remember { mutableIntStateOf(GomokuSession.streak) }
    var rewarded by remember { mutableStateOf(GomokuSession.rewarded) }
    var showResult by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    fun startGame(m: String) {
        mode = m
        game = GomokuGame(15, difficulty, vsAi = m != "friend")
        rewarded = false; showResult = false
        GomokuSession.game = game
        GomokuSession.difficulty = difficulty
        GomokuSession.rewarded = false
    }

    // 回模式选择页：棋局保留在 GomokuSession，选页可「回到上一局」
    fun backToModeSelect() {
        mode = null
        showResult = false
        confirmExit = false
    }

    // 新落的子弹入一下，落子更有实感
    val pop = remember { Animatable(1f) }
    LaunchedEffect(game.lastMove) {
        if (game.lastMove != null) {
            pop.snapTo(0.72f)
            pop.animateTo(1f, tween(130))
        }
    }

    // AI 回合：稍作停顿再落子（有"思考"感，也避免瞬间连点）；好友模式无 AI
    LaunchedEffect(game, game.moves, game.winner, mode) {
        if (mode == "ai" && !game.finished && !game.playerTurn) {
            game.beginThinking()
            delay(AI_DELAY_MS)
            val before = game.moves
            game.aiTurn()
            if (game.moves != before) {
                Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.PLACE_AI)
            }
        }
    }

    // 结算与奖励（一局一次；好友同机对战不结算，防互刷）
    LaunchedEffect(game, game.winner, mode) {
        if (mode != null && game.winner != 0 && !rewarded) {
            rewarded = true
            if (mode == "ai") {
                if (game.winner == 1) {
                    sessionWins++
                    streak++
                    GomokuSession.sessionWins = sessionWins
                    GomokuSession.streak = streak
                    vm.reportBest("gomoku_wins", (player.bestScores["gomoku_wins"] ?: 0) + 1)
                    vm.reportBest("gomoku_best_streak", streak)
                    vm.addCoins(40 + streak * 5)
                    vm.addXp(25)
                    Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.WIN)
                } else {
                    sessionLosses++
                    streak = 0
                    GomokuSession.sessionLosses = sessionLosses
                    GomokuSession.streak = 0
                    Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.LOSE)
                }
            } else {
                Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.WIN)
            }
            GomokuSession.rewarded = true
            showResult = true
        }
    }

    // ---------- 模式选择页：标题固定顶部，模式卡片在剩余空间居中 ----------
    if (mode == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader("⚫ 五子棋", onBack = { nav.popBackStack() },
                subtitle = "先连成五子者胜 · 选择对战模式")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (game.moves > 0) {
                        ModeCard(
                            emoji = "▶️", title = "回到上一局",
                            desc = if (game.finished) "上局已分出胜负（${game.moves} 手），可复盘或重开"
                                   else "继续上局残留的棋盘（已下 ${game.moves} 手）",
                            onClick = {
                                Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                                mode = if (game.vsAi) "ai" else "friend"
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    ModeCard(
                        emoji = "🤖", title = "人机对战",
                        desc = "挑战本地 AI：简单 / 普通 / 困难三档，胜负有金币与成就奖励",
                        onClick = {
                            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                            startGame("ai")
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    ModeCard(
                        emoji = "👥", title = "好友对战（同机双人）",
                        desc = "两人对坐同一部手机轮流落子，上方信息倒置显示，轮到谁一目了然",
                        onClick = {
                            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                            startGame("friend")
                        },
                    )
                }
            }
        }
        return
    }

    val friendMode = mode == "friend"
    val undoEnabled = game.moves > 0 && !game.finished

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 对局中按返回先回到模式选择页（棋局保留），不直接丢掉
        androidx.activity.compose.BackHandler(enabled = mode != null) { confirmExit = true }
        PageHeader(
            "⚫ 五子棋",
            onBack = { if (mode != null) confirmExit = true else nav.popBackStack() },
            subtitle = if (friendMode) "好友对战 · 黑先白后 · 两人对坐轮流落子"
                       else "你执黑先手 · 先连成五子者胜",
        )

        if (friendMode) {
            // ---------- 好友对战：对坐布局（白方条倒置在顶部，黑方条在底部，棋盘居中） ----------
            FriendBar(
                sideLabel = "⚪ 白方", stoneColor = WHITE_STONE, stoneEdge = WHITE_STONE_EDGE,
                active = !game.finished && game.turnSide == "白",
                undoEnabled = undoEnabled,
                onUndo = { game.undo(); rewarded = false; GomokuSession.rewarded = false; showResult = false },
                onReset = { startGame("friend") },
                rotated = true,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                GomokuBoard(game, pop, context, player, friendMode)
            }
            Spacer(Modifier.height(6.dp))
            FriendBar(
                sideLabel = "⚫ 黑方", stoneColor = BLACK_STONE, stoneEdge = BLACK_STONE_EDGE,
                active = !game.finished && game.turnSide == "黑",
                undoEnabled = undoEnabled,
                onUndo = { game.undo(); rewarded = false; GomokuSession.rewarded = false; showResult = false },
                onReset = { startGame("friend") },
                rotated = false,
            )
            Text(
                "💡 两人对坐：上方信息已倒置，方便对面的人阅读。轮到谁，谁的信息条会亮起。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            // ---------- 人机对战：操作按钮在上方，不挡手 ----------
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("简单", "普通", "困难").forEachIndexed { i, label ->
                    FilterChip(
                        selected = difficulty == i,
                        onClick = {
                            difficulty = i
                            startGame("ai")
                            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
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

            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        game.undo(); rewarded = false; GomokuSession.rewarded = false; showResult = false
                        Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                    },
                    enabled = undoEnabled,
                ) { Text("↩️ 悔棋") }
                OutlinedButton(onClick = {
                    startGame("ai")
                    Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                }) { Text("🔄 重开一局") }
                Text(
                    "本局 ${sessionWins} 胜 ${sessionLosses} 负 · 🔥 连胜 $streak · " +
                        (if (game.finished) "本局结束" else if (game.playerTurn) "轮到你" else "电脑思考中…"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                GomokuBoard(game, pop, context, player, friendMode)
            }
            Text(
                "💡 电脑棋力：简单（会走神）/ 普通（攻守均衡）/ 困难（带两步预判）。" +
                    "悔棋会回到你上一手落子之前（电脑应的那手一并撤销）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("回到模式选择？") },
                text = { Text("当前棋局会保留，稍后可在模式选择页点「回到上一局」继续。") },
                confirmButton = {
                    Button(onClick = { backToModeSelect() }) { Text("回到选页") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmExit = false }) { Text("继续下") }
                },
            )
        }

        if (showResult) {
            AlertDialog(
                onDismissRequest = { showResult = false },
                title = {
                    Text(
                        when {
                            friendMode && game.winner == 1 -> "⚫ 黑方胜！"
                            friendMode -> "⚪ 白方胜！"
                            game.winner == 1 -> "🏆 五子连珠，你赢了！"
                            else -> "💻 电脑先连成五子"
                        }
                    )
                },
                text = {
                    Column {
                        if (mode == "ai") {
                            Text("本局：${sessionWins} 胜 ${sessionLosses} 负", style = MaterialTheme.typography.bodyMedium)
                            if (game.winner == 1) {
                                Text("🔥 当前连胜 $streak 场", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("换对方先手再来一局更公平哦", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (friendMode) startGame("friend") else startGame("ai")
                        showResult = false
                    }) { Text("再来一局") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResult = false }) { Text("看看棋盘") }
                },
            )
        }
    }
}

/** 模式选择大卡片（选择页内容居中） */
@Composable
private fun ModeCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 好友对战信息条：轮到谁谁亮起；顶部条整体旋转 180°（对面的人正着看） */
@Composable
private fun FriendBar(
    sideLabel: String,
    stoneColor: Color,
    stoneEdge: Color,
    active: Boolean,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    rotated: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (rotated) Modifier.rotate(180f) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) TURN_GLOW.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                BorderStroke(if (active) 2.dp else 1.dp, if (active) TURN_GLOW else Color.Transparent),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(sideLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (active) "轮到你了" else "等待中",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) TURN_GLOW else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onUndo, enabled = undoEnabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
            Text("↩️ 悔棋", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(onClick = onReset,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
            Text("🔄 重开", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 棋盘 15×15（人机与好友共用；好友模式下落子着色由 game 内部轮换） */
@Composable
private fun GomokuBoard(
    game: GomokuGame,
    pop: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    context: android.content.Context,
    player: com.brainquest.game.data.PlayerState,
    friendMode: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = maxWidth / game.size
        val boardShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(cell * game.size)
                .clip(boardShape)
                .background(BOARD_BG)
                .border(BorderStroke(2.dp, BOARD_EDGE), boardShape),
        ) {
            for (i in 1 until game.size) {
                Box(
                    Modifier
                        .offset(x = cell * i, y = 0.dp)
                        .width(1.dp)
                        .height(cell * game.size)
                        .background(GRID_LINE),
                )
                Box(
                    Modifier
                        .offset(x = 0.dp, y = cell * i)
                        .width(cell * game.size)
                        .height(1.dp)
                        .background(GRID_LINE),
                )
            }
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
                                if (game.playerPlace(r, c)) {
                                    Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.PLACE)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (v != 0) {
                            Box(
                                Modifier
                                    .size(cell * 0.82f * if (isLast) pop.value else 1f)
                                    .background(if (v == 1) BLACK_STONE else WHITE_STONE, CircleShape)
                                    .border(
                                        BorderStroke(
                                            if (v == 1) 1.dp else 1.5.dp,
                                            if (v == 1) BLACK_STONE_EDGE else WHITE_STONE_EDGE,
                                        ),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLast) {
                                    Box(Modifier.size(cell * 0.22f).background(LAST_MOVE_COLOR, CircleShape))
                                }
                            }
                        }
                        if (inWin) PulsingWinRing(CircleShape)
                    }
                }
            }
        }
    }
}

/** 成五格子的脉动描边（读动画状态的范围限制在这几个格子里） */
@Composable
private fun PulsingWinRing(shape: Shape) {
    val transition = rememberInfiniteTransition(label = "winRing")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "winRingAlpha",
    )
    Box(Modifier.fillMaxSize().border(BorderStroke(3.dp, WIN_RING.copy(alpha = alpha)), shape))
}
