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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.brainquest.game.game.dungeon.render.GameActionButton
import com.brainquest.game.game.dungeon.render.GameBackdrop
import com.brainquest.game.game.dungeon.render.GameDialog
import com.brainquest.game.game.dungeon.render.GamePanel
import com.brainquest.game.game.dungeon.render.GameUi
import com.brainquest.game.game.dungeon.render.HudHpBar
import com.brainquest.game.game.dungeon.render.HudStatChip
import com.brainquest.game.game.dungeon.render.HudXpBar
import com.brainquest.game.game.dungeon.render.ResultStat
import com.brainquest.game.game.dungeon.render.RoundIconButton
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
    val hpFrac: Float,
    val xpFrac: Float,
)

/** 迷你幸存者：左半屏摇杆移动，自动攻击最近敌人，杀敌拾取经验，升级三选一，活得越久越难 */
@Composable
fun SurvivorScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    // 游戏实例全局唯一：重开用 game.start()（内部 reset），防止 pointerInput(Unit) 闭包绑到旧实例
    val game = remember { SurvivorGame() }
    var hud by remember { mutableStateOf(Hud(SurvivorGame.Phase.READY, 0, 0, 1, 1f, 0f)) }
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
            val h = Hud(
                game.phase, game.timeSec(), game.kills, game.level,
                game.hp / game.maxHp.coerceAtLeast(1f),
                game.xp.toFloat() / game.xpNext.coerceAtLeast(1),
            )
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

        }

        // ---------- HUD（批24：左上玩家面板 + 中央目标芯片 + 右上暂停） ----------
        if (hud.phase == SurvivorGame.Phase.PLAYING) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                GamePanel(Modifier.width(220.dp), contentPadding = 9.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2C3A56), Color(0xFF121826))),
                                    CircleShape,
                                )
                                .border(1.5.dp, GameUi.Gold.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Lv.${hud.level}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GameUi.Gold)
                        }
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("生命", fontSize = 10.sp, color = GameUi.TextDim)
                                Text("${(hud.hpFrac * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            HudHpBar(hud.hpFrac, "", Modifier.fillMaxWidth().padding(top = 3.dp), height = 13.dp)
                        }
                    }
                    HudXpBar(hud.xpFrac, Modifier.fillMaxWidth().padding(top = 5.dp), height = 4.dp)
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudStatChip("⏱", formatTime(hud.timeSec), tint = GameUi.Cyan)
                    HudStatChip("💀", "${hud.kills}", tint = GameUi.Hp)
                }
                Spacer(Modifier.weight(1f))
                RoundIconButton("Ⅱ", onClick = { game.pause() }, size = 42.dp)
            }
        }

        // ---------- Ready（批24：场景背景 + 说明面板） ----------
        if (hud.phase == SurvivorGame.Phase.READY) {
            Box(Modifier.fillMaxSize()) {
                GameBackdrop(accent = Color(0xFF4FC3F7))
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GamePanel(Modifier.widthIn(max = 560.dp), accent = Color(0xFF4FC3F7), contentPadding = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧟", fontSize = 30.sp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    "迷你幸存者",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text("弹幕求生 · 杀敌升级 · 三选一", fontSize = 11.sp, color = GameUi.TextDim)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        listOf(
                            "🕹 左半屏拖动 = 移动，角色自动攻击最近敌人",
                            "💀 击杀怪物拾取经验球，升级后三选一强化",
                            "⏱ 活得越久，怪物越多越强",
                        ).forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = GameUi.Text, modifier = Modifier.padding(vertical = 3.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if ((player.bestScores["survivor_best"] ?: 0) > 0)
                                "🏆 最高生存 ${player.bestScores["survivor_best"]} 秒 · 累计击杀 ${player.survivorTotalKills}"
                            else "还没有纪录，来活久一点！",
                            style = MaterialTheme.typography.labelMedium,
                            color = GameUi.Gold,
                        )
                        Spacer(Modifier.height(16.dp))
                        GameActionButton(
                            "开始游戏",
                            onClick = { game.start() },
                            // 批31：主按钮加深蓝提高对比（原浅蓝底深字发灰）
                            accent = Color(0xFF0288D1),
                            textColor = Color.White,
                            height = 48.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        GameActionButton(
                            "返回",
                            onClick = { nav.popBackStack() },
                            primary = false,
                            height = 40.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ---------- LevelUp 三选一（批24 卡牌化） ----------
        if (hud.phase == SurvivorGame.Phase.LEVELUP) {
            Box(Modifier.fillMaxSize().background(Color(0xB305070C)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬆️", fontSize = 26.sp)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                "升级！选择一项强化",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text("Lv.${hud.level} · 强化持续到本局结束", fontSize = 10.sp, color = GameUi.TextDim)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 780.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        game.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(Modifier.weight(1f), accent = GameUi.Xp, contentPadding = 16.dp) {
                                Text("强化 0${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GameUi.Gold)
                                Text(
                                    up.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(up.desc, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)
                                Spacer(Modifier.height(12.dp))
                                GameActionButton(
                                    "选择",
                                    onClick = { game.chooseUpgrade(up.id) },
                                    accent = Color(0xFF4FC3F7),
                                    textColor = Color(0xFF06121A),
                                    height = 38.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- Paused（批24 统一弹窗） ----------
        if (hud.phase == SurvivorGame.Phase.PAUSED) {
            GameDialog(title = "已暂停", accent = Color(0xFF4FC3F7), width = 380.dp) {
                Text(
                    "休息一下，怪物也停住了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameUi.TextDim,
                )
                Spacer(Modifier.height(14.dp))
                GameActionButton(
                    "继续",
                    onClick = { game.resume() },
                    accent = Color(0xFF4FC3F7),
                    textColor = Color(0xFF06121A),
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GameActionButton(
                    "重新开始",
                    onClick = { game.start(); rewarded = false },
                    primary = false,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GameActionButton(
                    "退出",
                    onClick = { confirmExit = true },
                    primary = false,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---------- GameOver（批24：结算统计格） ----------
        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            GameDialog(title = "💀 游戏结束", accent = GameUi.Hp, width = 480.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (newRecord) {
                        Text(
                            "🎉 新纪录！",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GameUi.Gold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultStat("⏱", formatTime(hud.timeSec), "生存时间", Modifier.weight(1f), GameUi.Cyan)
                        ResultStat("💀", "${hud.kills}", "击杀", Modifier.weight(1f), GameUi.Hp)
                        ResultStat("🏆", "${player.bestScores["survivor_best"] ?: 0}s", "最高纪录", Modifier.weight(1f), GameUi.Gold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "累计击杀 ${player.survivorTotalKills}",
                        fontSize = 10.sp,
                        color = GameUi.TextDim,
                    )
                    Spacer(Modifier.height(14.dp))
                    GameActionButton(
                        "重新开始",
                        onClick = { rewarded = false; game.start() },
                        accent = Color(0xFF4FC3F7),
                        textColor = Color(0xFF06121A),
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    GameActionButton(
                        "返回应用",
                        onClick = { nav.popBackStack() },
                        primary = false,
                        height = 40.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---------- 返回确认（批24 统一弹窗） ----------
        if (confirmExit) {
            GameDialog(title = "退出本局？", accent = GameUi.Hp, width = 400.dp) {
                Text(
                    "当前进度会正常结算（计最高纪录与奖励），是否返回大厅？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GameUi.TextDim,
                )
                Spacer(Modifier.height(14.dp))
                GameActionButton(
                    "返回大厅",
                    onClick = {
                        confirmExit = false
                        if (hud.phase == SurvivorGame.Phase.PLAYING || hud.phase == SurvivorGame.Phase.PAUSED) {
                            vm.addSurvivorResult(hud.timeSec, hud.kills)
                        }
                        nav.popBackStack()
                    },
                    accent = GameUi.Hp,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GameActionButton(
                    "继续",
                    onClick = {
                        confirmExit = false
                        if (hud.phase == SurvivorGame.Phase.PAUSED) game.resume()
                    },
                    primary = false,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
