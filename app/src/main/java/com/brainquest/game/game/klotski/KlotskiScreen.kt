package com.brainquest.game.game.klotski

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlin.math.abs

// 浅色棋盘 + 高饱和棋子：拉开底色与棋子对比（原来深棕底 + 深棕卒几乎糊在一起）
private val BOARD_BG = Color(0xFFF3E7D3)
private val BOARD_EDGE = Color(0xFF8D6E63)
private val CELL_WELL = Color(0x14000000)
private val EXIT_BG = Color(0x33FFB300)
private val EXIT_EDGE = Color(0xFFFB8C00)
private val EXIT_TEXT = Color(0xFFE65100)
private val DOOR_GROUND = Color(0xFFFBEDCB)   // 门外地面：不透明浅暖色，用于门洞缺口与板外延伸
private val PIECE_EDGE = Color(0x40FFFFFF)
private val SELECT_RING = Color(0xFFFFB300)
private val CAO_COLOR = Color(0xFFD32F2F)
private val H_BLOCK_COLOR = Color(0xFF1565C0)
private val V_BLOCK_COLOR = Color(0xFF2E7D32)
private val PAWN_COLOR = Color(0xFF6D4C41)

private val MOVE_MS = 150
private val DRAG_MS = 70

/** 华容道：滑动棋子把曹操移到底部中央出口（步数越少越好） */
@Composable
fun KlotskiScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    var level by remember { mutableStateOf<KlotskiLevel?>(null) }
    var game by remember { mutableStateOf<KlotskiGame?>(null) }
    var showWin by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var rewarded by remember { mutableStateOf(false) }

    val g = game
    val lv = level

    // 通关结算（只发一次奖励）
    LaunchedEffect(g, g?.solved) {
        if (g != null && g.solved && !rewarded && lv != null) {
            rewarded = true
            val key = "hrd_${lv.id}"
            newRecord = vm.reportBestLow(key, g.moves)
            vm.addCoins((50 - g.moves / 4).coerceAtLeast(10) + if (newRecord) 30 else 0)
            vm.addXp(25 + if (newRecord) 15 else 0)
            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.WIN)
            showWin = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        // 系统返回键：在对局中先退回选关，再返回才离开页面
        androidx.activity.compose.BackHandler(enabled = game != null) {
            game = null
            level = null
        }
        PageHeader(
            "🧱 华容道",
            onBack = { if (game != null) { game = null; level = null } else nav.popBackStack() },
            subtitle = if (lv == null) "选一关，把曹操滑到底部出口" else "滑动棋子 · 把曹操移到底部出口",
        )

        // 操作按钮在上方（v1.6.12）：棋盘在下方，滑动棋子时手不挡按钮
        if (g != null && lv != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    g.reset(); rewarded = false; showWin = false; newRecord = false
                    Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                }) { Text("🔄 重新开始") }
                OutlinedButton(onClick = { game = null; level = null }) { Text("🗺️ 换一关") }
            }
        }

        if (g == null || lv == null) {
            // 选关
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                KlotskiLevels.all.forEach { l ->
                    val best = player.bestScores["hrd_${l.id}"]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            level = l
                            game = KlotskiGame(l)
                            rewarded = false; showWin = false; newRecord = false
                            Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CLICK)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (best != null && best > 0) "我的最少 $best 步" else "未通关",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (best != null && best > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "参考 ${l.par} 步",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            return@Column
        }

        // 状态栏
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🗺️ ${lv.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("步数 ${g.moves}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val best = player.bestScores["hrd_${lv.id}"]
            Text(
                if (best != null && best > 0) "最少 $best 步" else "最少 —",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 棋盘：4 列 × 5 行，曹操滑到 (row 3, col 1) 即胜
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / KlotskiLevels.COLS
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            val thresholdPx = cellPx / 2f
            val version = g.version  // 读版本号驱动棋盘重组（棋子列表据此刷新）
            val selected = g.selectedId

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * KlotskiLevels.ROWS)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BOARD_BG)
                    .border(BorderStroke(2.dp, BOARD_EDGE), RoundedCornerShape(14.dp))
                    // 点棋盘空白处取消选中
                    .pointerInput(Unit) { detectTapGestures { g.clearSelection() } }
                    // 选中后可在棋盘任意位置滑动来移动它（曹操这种大块更好操作）
                    .pointerInput(selected) {
                        if (selected < 0) return@pointerInput
                        var acc = Offset.Zero
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero },
                            onDrag = { change, drag ->
                                change.consume()
                                acc += drag
                                if (acc.getDistance() > thresholdPx) {
                                    val moved = if (abs(acc.x) > abs(acc.y)) {
                                        g.move(selected, 0, if (acc.x > 0) 1 else -1)
                                    } else {
                                        g.move(selected, if (acc.y > 0) 1 else -1, 0)
                                    }
                                    acc = Offset.Zero
                                    if (moved) Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.MOVE)
                                }
                            },
                        )
                    },
            ) {
                // 格纹：让棋盘格可见
                for (r in 0 until KlotskiLevels.ROWS) {
                    for (c in 0 until KlotskiLevels.COLS) {
                        Box(
                            Modifier
                                .offset(x = cell * c + 3.dp, y = cell * r + 3.dp)
                                .size(cell - 6.dp)
                                .background(CELL_WELL, RoundedCornerShape(8.dp)),
                        )
                    }
                }
                // 出口（底部中央两格）
                Box(
                    Modifier
                        .offset(x = cell * KlotskiLevels.EXIT_COL + 3.dp, y = cell * KlotskiLevels.EXIT_ROW + 3.dp)
                        .size(cell * 2 - 6.dp, cell - 6.dp)
                        .background(EXIT_BG, RoundedCornerShape(8.dp))
                        .border(BorderStroke(2.dp, EXIT_EDGE), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("出口", color = EXIT_TEXT, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                g.snapshot().forEach { b ->
                    key(b.id) {
                        KlotskiBlock(
                            block = b,
                            cell = cell,
                            cellPx = cellPx,
                            thresholdPx = thresholdPx,
                            selected = selected == b.id,
                            onSelect = {
                                g.select(b.id)
                                Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.SELECT)
                            },
                            onTryMove = { dr, dc ->
                                val moved = g.move(b.id, dr, dc)
                                if (moved) Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.MOVE)
                                moved
                            },
                        )
                    }
                }
            }

            // ---------- 门式出口：底边框在出口处断开，配门柱与门外地面，像墙上开的门 ----------
            val doorX = cell * KlotskiLevels.EXIT_COL
            val doorW = cell * 2
            val boardBottom = cell * KlotskiLevels.ROWS
            val postW = 5.dp
            // 门柱：立在出口两侧，向下探出板外一点
            Box(
                Modifier
                    .offset(x = doorX, y = boardBottom - 13.dp)
                    .size(postW, 22.dp)
                    .background(EXIT_EDGE, RoundedCornerShape(2.dp)),
            )
            Box(
                Modifier
                    .offset(x = doorX + doorW - postW, y = boardBottom - 13.dp)
                    .size(postW, 22.dp)
                    .background(EXIT_EDGE, RoundedCornerShape(2.dp)),
            )
            // 墙体缺口：盖掉门洞范围内的底边框，出口看起来是"开"的
            Box(
                Modifier
                    .offset(x = doorX + postW, y = boardBottom - 4.dp)
                    .size(doorW - postW * 2, 8.dp)
                    .background(DOOR_GROUND),
            )
            // 门外地面：从缺口向外延伸一小截，指向"从这里出去"
            Box(
                Modifier
                    .offset(x = doorX + postW + 5.dp, y = boardBottom + 1.dp)
                    .size(doorW - postW * 2 - 10.dp, 9.dp)
                    .background(DOOR_GROUND, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
            )
        }

        Text(
            "💡 提示：曹操（红块）需滑到下方出口；把挡路的竖将上下腾挪、横将左右移动来开路。" +
                "大块不好滑时，先点一下棋子，再在棋盘任意位置滑动即可。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )

        if (showWin) {
            AlertDialog(
                onDismissRequest = { showWin = false },
                title = { Text("🎉 曹操已到出口！") },
                text = {
                    Column {
                        Text("用了 ${g.moves} 步", style = MaterialTheme.typography.titleMedium)
                        if (newRecord) Text("🏅 新纪录！", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    val idx = KlotskiLevels.all.indexOfFirst { it.id == lv.id }
                    val next = KlotskiLevels.all.getOrNull(idx + 1)
                    if (next != null) {
                        Button(onClick = {
                            showWin = false
                            level = next
                            game = KlotskiGame(next)
                            rewarded = false; newRecord = false
                        }) { Text("下一关：${next.name}") }
                    } else {
                        Button(onClick = { showWin = false; game = null; level = null }) { Text("返回选关") }
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showWin = false
                        g.reset()
                        rewarded = false; newRecord = false
                    }) { Text("再来一次") }
                },
            )
        }
    }
}

/**
 * 单个棋子：位置 = 格位 + 拖动偏移，统一交给一个动画值过渡。
 * 这样拖动跟手、过阈值落格、松手回弹、重开归位都是同一条平滑路径，
 * 不需要额外的动画协程（避免多个协程抢同一个动画值）。
 * 注意：列表项里的 remember 必须靠外层 key(id) 绑定，否则重组时会串位。
 */
@Composable
private fun KlotskiBlock(
    block: KBlock,
    cell: Dp,
    cellPx: Float,
    thresholdPx: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onTryMove: (Int, Int) -> Boolean,
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var acc = remember { Offset.Zero }
    var dragging by remember { mutableStateOf(false) }
    var stepped by remember { mutableStateOf(false) }   // 一次手势只走一步：走过即忽略后续拖动

    fun clampAxis(o: Offset): Offset = if (abs(o.x) > abs(o.y)) {
        Offset(o.x.coerceIn(-cellPx, cellPx), 0f)
    } else {
        Offset(0f, o.y.coerceIn(-cellPx, cellPx))
    }

    // 拖动中缩短过渡时间，跟手更紧；落格/回弹用稍长一点，滑行更柔和
    val moveSpec = tween<Dp>(if (dragging) DRAG_MS else MOVE_MS, easing = FastOutSlowInEasing)
    val x by animateDpAsState(
        targetValue = cell * block.col + 4.dp + with(density) { dragOffset.x.toDp() },
        animationSpec = moveSpec,
        label = "blockX",
    )
    val y by animateDpAsState(
        targetValue = cell * block.row + 4.dp + with(density) { dragOffset.y.toDp() },
        animationSpec = moveSpec,
        label = "blockY",
    )

    // 兜底：未拖动时（重开/由棋盘手势移动）偏移一定归零
    LaunchedEffect(block.row, block.col, dragging) {
        if (!dragging) dragOffset = Offset.Zero
    }

    val isCao = block.name == KlotskiLevels.CAO_NAME
    val color = when {
        isCao -> CAO_COLOR
        block.w > 1 && block.h == 1 -> H_BLOCK_COLOR
        block.h > 1 && block.w == 1 -> V_BLOCK_COLOR
        else -> PAWN_COLOR
    }
    val shape = RoundedCornerShape(10.dp)

    Box(
        Modifier
            .offset(x = x, y = y)
            .size(cell * block.w - 8.dp, cell * block.h - 8.dp)
            .graphicsLayer {
                scaleX = if (selected) 1.03f else 1f
                scaleY = if (selected) 1.03f else 1f
            }
            .background(color, shape)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) SELECT_RING else PIECE_EDGE),
                shape,
            )
            .clickable { onSelect() }
            .pointerInput(block.id) {
                detectDragGestures(
                    onDragStart = {
                        acc = Offset.Zero
                        dragging = true
                        stepped = false
                    },
                    onDragEnd = {
                        acc = Offset.Zero
                        dragging = false
                        stepped = false
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        acc = Offset.Zero
                        dragging = false
                        stepped = false
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        if (stepped) return@detectDragGestures
                        acc += delta
                        dragOffset = clampAxis(acc)
                        if (acc.getDistance() > thresholdPx) {
                            val horizontal = abs(acc.x) > abs(acc.y)
                            val dr = if (horizontal) 0 else if (acc.y > 0) 1 else -1
                            val dc = if (horizontal) (if (acc.x > 0) 1 else -1) else 0
                            // 一次手势只滑一步：走完即锁住，累计位移清零（松手后才能走下一步）
                            if (onTryMove(dr, dc)) {
                                stepped = true
                                acc = Offset.Zero
                            } else {
                                acc = Offset.Zero
                            }
                            dragOffset = clampAxis(acc)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (block.w == 1 && block.h == 2) {
                // 竖将：名字竖排
                block.name.forEach { ch ->
                    Text("$ch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Text(
                    block.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCao) 22.sp else 16.sp,
                )
            }
        }
    }
}
