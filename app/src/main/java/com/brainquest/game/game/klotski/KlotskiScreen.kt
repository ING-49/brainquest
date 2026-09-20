package com.brainquest.game.game.klotski

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlin.math.abs

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
            Sfx.play(context, player.soundOn, SfxType.WIN)
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
            subtitle = if (lv == null) "选一关，把曹操滑到底部出口" else "滑动棋子或点选后按方向键",
        )

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
            val halfCellPx = with(androidx.compose.ui.platform.LocalDensity.current) { (cell / 2).toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cell * KlotskiLevels.ROWS)
                    .background(Color(0xFF8D6E63), RoundedCornerShape(12.dp)),
            ) {
                // 出口提示（底部中央两格）
                Box(
                    Modifier
                        .offset(x = cell * KlotskiLevels.EXIT_COL, y = cell * KlotskiLevels.EXIT_ROW)
                        .size(cell * 2, cell)
                        .padding(4.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                )
                val version = g.version  // 读版本号触发重组
                val selected = g.selectedId
                g.snapshot().forEach { b ->
                    val isCao = b.name == KlotskiLevels.CAO_NAME
                    val color = when {
                        isCao -> Color(0xFFC62828)
                        b.w > 1 && b.h == 1 -> Color(0xFF1565C0)
                        b.h > 1 && b.w == 1 -> Color(0xFF2E7D32)
                        else -> Color(0xFF6D4C41)
                    }
                    val threshold = halfCellPx
                    Box(
                        Modifier
                            .offset(x = cell * b.col + 4.dp, y = cell * b.row + 4.dp)
                            .size(cell * b.w - 8.dp, cell * b.h - 8.dp)
                            .background(color, RoundedCornerShape(8.dp))
                            .clickable { g.select(b.id) }
                            .pointerInput(b.id, version) {
                                var acc = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { acc = Offset.Zero },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        acc += drag
                                        if (acc.getDistance() > threshold) {
                                            val moved = if (abs(acc.x) > abs(acc.y)) {
                                                g.move(b.id, 0, if (acc.x > 0) 1 else -1)
                                            } else {
                                                g.move(b.id, if (acc.y > 0) 1 else -1, 0)
                                            }
                                            acc = Offset.Zero
                                            if (moved) Sfx.play(context, player.soundOn, SfxType.CLICK)
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (b.w == 1 && b.h == 2) {
                                // 竖将：名字竖排
                                b.name.forEach { ch ->
                                    Text("$ch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            } else {
                                Text(
                                    b.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isCao) 22.sp else 15.sp,
                                )
                            }
                        }
                        if (selected == b.id) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }

        // 方向键（点选棋子后可用；模拟器/单手操作友好）
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = { if (g.selectedId >= 0) g.move(g.selectedId, -1, 0) },
                    enabled = g.selectedId >= 0 && g.canMove(g.selectedId, -1, 0),
                ) { Text("↑") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (g.selectedId >= 0) g.move(g.selectedId, 0, -1) },
                        enabled = g.selectedId >= 0 && g.canMove(g.selectedId, 0, -1),
                    ) { Text("←") }
                    OutlinedButton(
                        onClick = { if (g.selectedId >= 0) g.move(g.selectedId, 1, 0) },
                        enabled = g.selectedId >= 0 && g.canMove(g.selectedId, 1, 0),
                    ) { Text("↓") }
                    OutlinedButton(
                        onClick = { if (g.selectedId >= 0) g.move(g.selectedId, 0, 1) },
                        enabled = g.selectedId >= 0 && g.canMove(g.selectedId, 0, 1),
                    ) { Text("→") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { g.reset(); rewarded = false; showWin = false; newRecord = false }) { Text("🔄 重新开始") }
            OutlinedButton(onClick = { game = null; level = null }) { Text("🗺️ 换一关") }
        }
        Text(
            "💡 提示：曹操（红块）需滑到下方出口；把挡路的竖将上下腾挪、横将左右移动来开路。",
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
