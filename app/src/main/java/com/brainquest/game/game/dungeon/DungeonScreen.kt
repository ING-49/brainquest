package com.brainquest.game.game.dungeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.RoomType
import com.brainquest.game.game.dungeon.render.EntityRenderer
import kotlin.math.hypot

private val BG = Color(0xFF0B0D14)
private val JOY_C = Color(0x55FFFFFF)

/** HUD 快照：只在变化时写 Compose 状态（数值条由 Canvas 每帧绘制） */
private data class Hud(
    val phase: DungeonGame.Phase,
    val floor: Int,
    val cleared: Int,
    val roomsTotal: Int,
    val hp: Int,
    val maxHp: Int,
    val timeSec: Int,
    val kills: Int,
)

/** 地牢幸存者：选职业 → 探索地牢（清房开门选房间）→ 层末 Boss → 5 层通关 */
@Composable
fun DungeonScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    // 游戏实例全局唯一：重开走 reset()，防止摇杆 pointerInput(Unit) 闭包绑旧实例
    val game = remember { DungeonGame() }
    var hud by remember {
        mutableStateOf(Hud(DungeonGame.Phase.READY, 1, 1, 0, 100, 100, 0, 0))
    }
    var confirmExit by remember { mutableStateOf(false) }
    val frame = remember { mutableIntStateOf(0) }
    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }

    // 游戏循环
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) game.tick((t - last) / 1e9f)
                last = t
            }
            frame.intValue++
            val h = Hud(
                game.phase, game.floor, game.clearedRooms, game.rooms.size,
                game.hp, game.maxHp, game.runTimeSec, game.totalKills,
            )
            if (h != hud) hud = h
        }
    }

    BackHandler(enabled = hud.phase != DungeonGame.Phase.READY) {
        if (hud.phase == DungeonGame.Phase.EXPLORING) game.pause()
        confirmExit = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BG)
            .onSizeChanged { game.setViewport(it.width.toFloat(), it.height.toFloat()) },
    ) {
        // ---------- 世界绘制 ----------
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            if (off.x <= size.width / 2f) {
                                game.joyActive = true
                                game.joyBaseX = off.x
                                game.joyBaseY = off.y
                                game.joyX = 0f
                                game.joyY = 0f
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (!game.joyActive) return@detectDragGestures
                            val cur = change.position
                            var dx = cur.x - game.joyBaseX
                            var dy = cur.y - game.joyBaseY
                            val len = hypot(dx, dy)
                            if (len > joyMaxPx) { dx = dx / len * joyMaxPx; dy = dy / len * joyMaxPx }
                            game.joyX = dx / joyMaxPx
                            game.joyY = dy / joyMaxPx
                        },
                        onDragEnd = { game.joyActive = false; game.joyX = 0f; game.joyY = 0f },
                        onDragCancel = { game.joyActive = false; game.joyX = 0f; game.joyY = 0f },
                    )
                },
        ) {
            frame.intValue   // 订阅：每帧重绘
            EntityRenderer.drawWorld(this, game, frame.intValue / 60f)
            for (d in game.dummies) EntityRenderer.drawDummy(this, game, d, frame.intValue / 60f)
            EntityRenderer.drawPlayer(this, game, frame.intValue / 60f)
            EntityRenderer.drawBars(this, game)
        }

        // ---------- HUD ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "❤️ ${hud.hp}/${hud.maxHp}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(top = 14.dp),
            )
            Text(
                "🏰 第${hud.floor}层 · 房间 ${hud.cleared}/${hud.roomsTotal} · ⏱ ${formatTime(hud.timeSec)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { game.pause() },
                enabled = hud.phase == DungeonGame.Phase.EXPLORING,
                modifier = Modifier.padding(top = 10.dp).height(40.dp),
            ) { Text("⏸") }
        }

        // 小地图（右上，暂停按钮下方）
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            Minimap(game, Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp))
        }

        // ---------- Ready ----------
        if (hud.phase == DungeonGame.Phase.READY) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC0B0D14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🏰 地牢幸存者", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "5 层地牢 · 清怪开门选房间 · 层末 Boss",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(onClick = { game.toClassSelect() }, modifier = Modifier.padding(top = 18.dp)) { Text("进入地牢") }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp)) { Text("返回") }
            }
        }

        // ---------- 职业选择 ----------
        if (hud.phase == DungeonGame.Phase.CLASS_SELECT) {
            Column(
                Modifier.fillMaxSize().background(Color(0xF00B0D14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("选择职业", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "最高层数纪录：${player.bestScores["dungeon_floor"] ?: 0} 层",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFD54F),
                    modifier = Modifier.padding(top = 2.dp),
                )
                ClassDef.ALL.forEach { c ->
                    Card(
                        onClick = { game.selectClass(c.id) },
                        modifier = Modifier.fillMaxWidth(0.85f).padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            // 职业色块小模型预览
                            androidx.compose.foundation.Canvas(Modifier.width(34.dp).height(46.dp)) {
                                drawRoundRect(Color(c.bodyColor), Offset(size.width / 2 - 9, size.height / 2 - 8), Size(18f, 20f), CornerRadius(5f))
                                drawCircle(Color(c.accentColor), 8f, Offset(size.width / 2, size.height / 2 - 18f))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(c.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "❤️${c.maxHp} · ⚔️${c.attack} · ${c.weaponName} · 被动：${c.passiveName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- Paused ----------
        if (hud.phase == DungeonGame.Phase.PAUSED) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⏸ 已暂停", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = { game.resume() }, modifier = Modifier.padding(top = 16.dp).width(160.dp)) { Text("继续") }
                OutlinedButton(onClick = { confirmExit = true }, modifier = Modifier.padding(top = 8.dp).width(160.dp)) { Text("退出") }
            }
        }

        // ---------- Victory（阶段 1 占位结算） ----------
        if (hud.phase == DungeonGame.Phase.VICTORY) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC0B0D14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🏆 地牢通关！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                Text(
                    "用时 ${formatTime(hud.timeSec)} · 击杀 ${hud.kills}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { vm.reportBest("dungeon_floor", DungeonGame.MAX_FLOOR) },
                    enabled = false,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("结算（阶段4开放）") }
                Button(onClick = { game.reset(); game.toClassSelect() }, modifier = Modifier.padding(top = 12.dp).width(160.dp)) { Text("再来一局") }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp).width(160.dp)) { Text("返回应用") }
            }
        }

        // ---------- 返回确认 ----------
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("退出本局？") },
                text = { Text("探索进度不会保存，是否返回大厅？") },
                confirmButton = {
                    Button(onClick = { confirmExit = false; nav.popBackStack() }) { Text("返回大厅") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        confirmExit = false
                        if (hud.phase == DungeonGame.Phase.PAUSED) game.resume()
                    }) { Text("继续下") }
                },
            )
        }
    }
}

/** 小地图：房间缩略矩形，已探索亮起、当前高亮、连线表门 */
@Composable
private fun Minimap(game: DungeonGame, modifier: Modifier) {
    Canvas(modifier.width(150.dp).height(110.dp).background(Color(0x66000000), RoundedCornerShape(8.dp))) {
        val rooms = game.rooms
        if (rooms.isEmpty()) return@Canvas
        val minX = rooms.minOf { it.gx }
        val maxX = rooms.maxOf { it.gx }
        val minY = rooms.minOf { it.gy }
        val maxY = rooms.maxOf { it.gy }
        val cols = (maxX - minX + 1).coerceAtLeast(1)
        val rows = (maxY - minY + 1).coerceAtLeast(1)
        val cellW = size.width / (cols + 1)
        val cellH = size.height / (rows + 1)
        val rw = cellW * 0.72f
        val rh = cellH * 0.72f

        fun cell(r: com.brainquest.game.game.dungeon.model.Room): Offset =
            Offset((r.gx - minX + 0.5f) * cellW, (r.gy - minY + 0.5f) * cellH)

        // 门连线（先画线后画块）
        for (room in rooms) {
            for ((d, n) in room.neighbors) {
                if (d == Dir.LEFT || d == Dir.UP) continue
                if (!room.discovered && !n.discovered) continue
                val a = cell(room); val b = cell(n)
                drawLine(Color(0x88AAAAAA), a, b, 3f)
            }
        }
        for (room in rooms) {
            if (!room.discovered) continue
            val c = cell(room)
            val current = room === game.currentRoom
            val fill = when {
                current -> Color(0xFFFFD54F)
                room.visited -> Color(0xFF5C6BC0)
                else -> Color(0xFF37474F)
            }
            drawRoundRect(fill, Offset(c.x - rw / 2, c.y - rh / 2), Size(rw, rh), CornerRadius(4f))
            if (current) drawRoundRect(Color.White, Offset(c.x - rw / 2, c.y - rh / 2), Size(rw, rh), CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            // 房型点
            val dot = when (room.type) {
                RoomType.BOSS -> Color(0xFFE15A5A)
                RoomType.ELITE -> Color(0xFFB388FF)
                RoomType.CHEST -> Color(0xFFFFD54F)
                RoomType.SHOP -> Color(0xFF7EE38A)
                else -> null
            }
            if (dot != null) drawCircle(dot, 3.5f, Offset(c.x, c.y))
        }
    }
}

private fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
