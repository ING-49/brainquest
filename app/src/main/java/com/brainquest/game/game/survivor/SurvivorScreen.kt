package com.brainquest.game.game.survivor

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlin.math.hypot

// 配色：深海蓝底 + 网格，玩家青色、敌人红系、子弹金黄、经验球绿
private val BG = Color(0xFF101826)
private val GRID = Color(0xFF1D2A3E)
private val PLAYER_C = Color(0xFF4FC3F7)
private val ENEMY_C = Color(0xFFE15A5A)
private val ENEMY_BIG_C = Color(0xFFB23A3A)
private val BULLET_C = Color(0xFFFFD54F)
private val ORB_C = Color(0xFF7EE38A)
private val HP_C = Color(0xFFEF5350)
private val XP_C = Color(0xFF66BB6A)
private val BAR_BG = Color(0x66000000)
private val JOY_C = Color(0x55FFFFFF)

/** HUD 快照：只有内容变化才写 Compose 状态，避免每帧重组（数值条由 Canvas 每帧绘制） */
private data class Hud(
    val phase: SurvivorGame.Phase,
    val timeSec: Int,
    val kills: Int,
    val level: Int,
)

/** 迷你幸存者：左半屏摇杆移动，自动攻击最近敌人，杀敌拾取经验，升级三选一，活得越久越难 */
@Composable
fun SurvivorScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    // 游戏实例全局唯一：重开用 game.start()（内部 reset），防止 pointerInput(Unit) 闭包绑到旧实例
    val game = remember { SurvivorGame() }
    var hud by remember { mutableStateOf(Hud(SurvivorGame.Phase.READY, 0, 0, 1)) }
    var rewarded by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val frame = remember { mutableIntStateOf(0) }
    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }

    // 游戏循环：withFrameNanos 驱动 tick，frame 自增只触发 Canvas 重绘（不重组）
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) game.tick((t - last) / 1e9f)
                last = t
            }
            frame.intValue++
            val h = Hud(game.phase, game.timeSec(), game.kills, game.level)
            if (h != hud) hud = h
        }
    }

    // 音效：升级 / 受伤 / 结束（从 hud 变化触发，主线程播放）
    LaunchedEffect(hud.phase) {
        when (hud.phase) {
            SurvivorGame.Phase.LEVELUP -> Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CORRECT)
            SurvivorGame.Phase.GAMEOVER -> Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CRASH)
            else -> {}
        }
    }

    // 结算（一局一次）：最高生存/单局击杀/累计击杀入档 + 金币经验
    LaunchedEffect(hud.phase) {
        if (hud.phase == SurvivorGame.Phase.GAMEOVER && !rewarded) {
            rewarded = true
            newRecord = vm.addSurvivorResult(hud.timeSec, hud.kills)
        }
    }

    val inOverlays = hud.phase != SurvivorGame.Phase.PLAYING
    BackHandler(enabled = hud.phase != SurvivorGame.Phase.READY) {
        if (hud.phase == SurvivorGame.Phase.PLAYING) game.pause()
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
                            // 左半屏按下生成浮动摇杆；右半屏忽略
                            if (off.x <= size.width / 2f) {
                                game.joyActive = true
                                game.joyBaseX = off.x
                                game.joyBaseY = off.y
                                game.joyX = 0f
                                game.joyY = 0f
                            }
                        },
                        onDrag = { change, drag ->
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

            // 背景网格（随相机平移）
            val step = 64f
            var gx = -game.camX % step
            if (gx < 0) gx += step
            while (gx < size.width) {
                drawLine(GRID, Offset(gx, 0f), Offset(gx, size.height), 2f)
                gx += step
            }
            var gy = -game.camY % step
            if (gy < 0) gy += step
            while (gy < size.height) {
                drawLine(GRID, Offset(0f, gy), Offset(size.width, gy), 2f)
                gy += step
            }

            // 经验球
            for (o in game.orbs) {
                if (!o.alive) continue
                val sx = o.x - game.camX; val sy = o.y - game.camY
                if (sx < -20f || sy < -20f || sx > size.width + 20f || sy > size.height + 20f) continue
                drawCircle(ORB_C, game.orbR, Offset(sx, sy))
            }

            // 敌人（受伤后画细血条）
            for (e in game.enemies) {
                if (!e.alive) continue
                val sx = e.x - game.camX; val sy = e.y - game.camY
                if (sx < -40f || sy < -40f || sx > size.width + 40f || sy > size.height + 40f) continue
                drawCircle(if (e.r > 20f) ENEMY_BIG_C else ENEMY_C, e.r, Offset(sx, sy))
                if (e.hp < e.maxHp) {
                    val w = e.r * 2f
                    drawRect(BAR_BG, Offset(sx - e.r, sy - e.r - 8f), Size(w, 3f))
                    drawRect(HP_C, Offset(sx - e.r, sy - e.r - 8f), Size(w * (e.hp / e.maxHp), 3f))
                }
            }

            // 子弹
            for (b in game.bullets) {
                if (!b.alive) continue
                drawCircle(BULLET_C, game.bulletR, Offset(b.x - game.camX, b.y - game.camY))
            }

            // 玩家（无敌帧期间闪烁）
            val psx = game.px - game.camX
            val psy = game.py - game.camY
            val blink = game.invincibleNow() > 0f && (game.elapsed * 12f).toInt() % 2 == 0
            if (!blink) {
                drawCircle(PLAYER_C, game.playerR, Offset(psx, psy))
                drawCircle(Color.White, game.playerR - 5f, Offset(psx, psy))
            }

            // 虚拟摇杆
            if (game.joyActive) {
                drawCircle(JOY_C, 56f, Offset(game.joyBaseX, game.joyBaseY), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(JOY_C, 26f, Offset(game.joyBaseX + game.joyX * joyMaxPx, game.joyBaseY + game.joyY * joyMaxPx))
            }

            // HUD 数值条（左上：血条 + 经验条）
            val barW = (size.width * 0.34f).coerceAtMost(340f)
            drawRoundRect(BAR_BG, Offset(20f, 34f), Size(barW, 16f), CornerRadius(8f))
            drawRoundRect(HP_C, Offset(20f, 34f), Size(barW * (game.hp / game.maxHp).coerceIn(0f, 1f), 16f), CornerRadius(8f))
            drawRoundRect(BAR_BG, Offset(20f, 56f), Size(barW, 10f), CornerRadius(5f))
            drawRoundRect(XP_C, Offset(20f, 56f), Size(barW * (game.xp.toFloat() / game.xpNext).coerceIn(0f, 1f), 10f), CornerRadius(5f))
        }

        // ---------- HUD 文本与暂停 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Lv.${hud.level}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(top = 10.dp),
            )
            Text(
                "⏱ ${formatTime(hud.timeSec)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "💀 ${hud.kills}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            OutlinedButton(
                onClick = { game.pause() },
                enabled = hud.phase == SurvivorGame.Phase.PLAYING,
                modifier = Modifier.padding(start = 8.dp).height(40.dp),
            ) { Text("⏸") }
        }

        // ---------- Ready ----------
        if (hud.phase == SurvivorGame.Phase.READY) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC101826)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🧟 迷你幸存者", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "左半屏摇杆移动 · 自动攻击 · 杀敌升级三选一",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier.padding(top = 6.dp),
                )
                val best = player.bestScores["survivor_best"] ?: 0
                Text(
                    if (best > 0) "🏆 最高生存 $best 秒" else "还没有纪录，来活久一点！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFD54F),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(onClick = { game.start() }, modifier = Modifier.padding(top = 18.dp)) { Text("开始游戏") }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp)) { Text("返回") }
            }
        }

        // ---------- LevelUp 三选一 ----------
        if (hud.phase == SurvivorGame.Phase.LEVELUP) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⬆️ 升级！选择一项强化", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                game.pendingUpgrades.forEach { up ->
                    Card(
                        onClick = { game.chooseUpgrade(up.id) },
                        modifier = Modifier.fillMaxWidth(0.8f).padding(top = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(up.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(up.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ---------- Paused ----------
        if (hud.phase == SurvivorGame.Phase.PAUSED) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⏸ 已暂停", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = { game.resume() }, modifier = Modifier.padding(top = 16.dp).width(160.dp)) { Text("继续") }
                OutlinedButton(
                    onClick = { game.start() ; rewarded = false },
                    modifier = Modifier.padding(top = 8.dp).width(160.dp),
                ) { Text("重新开始") }
                OutlinedButton(onClick = { confirmExit = true }, modifier = Modifier.padding(top = 8.dp).width(160.dp)) { Text("退出") }
            }
        }

        // ---------- GameOver ----------
        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC101826)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("💀 游戏结束", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "生存 ${formatTime(hud.timeSec)} · 击杀 ${hud.kills}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (newRecord) {
                    Text("🎉 新纪录！", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                }
                Text(
                    "🏆 最高生存 ${player.bestScores["survivor_best"] ?: 0} 秒 · 累计击杀 ${player.survivorTotalKills}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(
                    onClick = { rewarded = false; game.start() },
                    modifier = Modifier.padding(top = 16.dp).width(160.dp),
                ) { Text("重新开始") }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp).width(160.dp)) { Text("返回应用") }
            }
        }

        // ---------- 返回确认 ----------
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("退出本局？") },
                text = { Text("当前进度会正常结算（计最高纪录与奖励），是否返回大厅？") },
                confirmButton = {
                    Button(onClick = {
                        confirmExit = false
                        if (hud.phase == SurvivorGame.Phase.PLAYING || hud.phase == SurvivorGame.Phase.PAUSED) {
                            vm.addSurvivorResult(hud.timeSec, hud.kills)
                        }
                        nav.popBackStack()
                    }) { Text("返回大厅") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        confirmExit = false
                        if (hud.phase == SurvivorGame.Phase.PAUSED) game.resume()
                    }) { Text("继续下") }
                },
            )
        }
    }
}

private fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
