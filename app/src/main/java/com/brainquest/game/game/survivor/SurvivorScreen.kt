package com.brainquest.game.game.survivor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.statusBars
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
import com.brainquest.game.game.dungeon.render.gameTopInsets
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
    // 批42：数学坐标纸——副格(原GRID)/主格线/坐标轴
    private val GRID_MAJOR = Color(0xFF2E4A6B)
    private val AXIS_C = Color(0xFF3D5A80)
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
/** 批42：强化 id → 数学字形 */
private val upGlyph = mapOf("atk" to "×", "aspd" to "∂", "spd" to "√", "hp" to "|x|", "pickup" to "π")

@Composable
fun SurvivorScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current

    // 沉浸全屏已收敛至 AppRoot 单一所有者（批61：屏级 hide/show 会与横屏路由间跳转的 onDispose 顺序打架）
    // （批38 原注释：横屏游戏页隐藏系统栏，离开自动恢复）

    // 游戏实例全局唯一：重开用 game.start()（内部 reset），防止 pointerInput(Unit) 闭包绑到旧实例
    val game = remember { SurvivorGame() }
    var hud by remember { mutableStateOf(Hud(SurvivorGame.Phase.READY, 0, 0, 1, 1f, 0f)) }
    var rewarded by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var showCodex by remember { mutableStateOf(false) }   // 批48：数学空间图鉴
    val frame = remember { mutableIntStateOf(0) }
    val sfxPrev = remember { intArrayOf(0, 0, 0, 0, 0) }   // 批50：音效差量基线
    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }
    // 批61：浮动摇杆出生点安全夹取基准（px）——避挖孔/手势条，环 r56px
    val dSIns = LocalDensity.current
    val joySafeL = WindowInsets.safeDrawing.getLeft(dSIns, androidx.compose.ui.platform.LocalLayoutDirection.current).toFloat()
    val joySafeT = WindowInsets.safeDrawing.getTop(dSIns).toFloat()
    val joySafeB = WindowInsets.safeDrawing.getBottom(dSIns).toFloat()
    // 批42：数学字形测量缓存（drawText 需要 TextLayoutResult；按字形缓存避免每帧测量）
    val textMeasurer = rememberTextMeasurer()
    val glyphMetrics = remember { mutableMapOf<String, androidx.compose.ui.text.TextLayoutResult>() }

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
            // 批50：反馈音效（差量触发；ToneGenerator 自带 60ms 节流）
            if (game.sfxShoot > sfxPrev[0]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.SHOOT); sfxPrev[0] = game.sfxShoot }
            if (game.sfxHit > sfxPrev[1]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.HIT); sfxPrev[1] = game.sfxHit }
            if (game.sfxDeath > sfxPrev[2]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.DEATH); sfxPrev[2] = game.sfxDeath }
            if (game.sfxOrb > sfxPrev[3]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.ORB); sfxPrev[3] = game.sfxOrb }
            if (game.sfxHurt > sfxPrev[4]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CRASH); sfxPrev[4] = game.sfxHurt }
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
            vm.markSurvivorSeen(game.defeatedKinds.map { k -> "SURV_" + listOf("DIGIT", "OP", "GEOM", "ELITE")[k.coerceIn(0, 3)] })   // 批48：图鉴记录
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
                            // 左半屏按下生成浮动摇杆；右半屏忽略；出生点夹进安全区（批61：避挖孔/手势条）
                            if (off.x <= size.width / 2f) {
                                game.joyActive = true
                                game.joyBaseX = off.x.coerceIn(joySafeL + 56f, size.width / 2f)
                                game.joyBaseY = off.y.coerceIn(joySafeT + 56f, size.height - joySafeB - 56f)
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

            // 批53：远景漂浮几何线框（空间层次：远景层，视差 0.35）
            for (i in 0..2) {
                val seed = i * 77.7f
                val spanW = size.width + 400f
                val spanH = size.height + 300f
                val baseX = (seed * 13.7f) % spanW
                val baseY = (seed * 7.3f) % spanH
                val px2 = ((baseX - game.camX * 0.35f + kotlin.math.sin(game.elapsed * 0.13f + seed) * 40f) % spanW + spanW) % spanW - 200f
                val py2 = ((baseY - game.camY * 0.35f + kotlin.math.cos(game.elapsed * 0.11f + seed) * 30f) % spanH + spanH) % spanH - 150f
                val wr = 90f + i * 40f
                val sides2 = 3 + i
                val wpath = androidx.compose.ui.graphics.Path()
                for (k in 0 until sides2) {
                    val ang2 = -1.5707963f + k * (6.2831853f / sides2)
                    val vx2 = px2 + kotlin.math.cos(ang2) * wr
                    val vy2 = py2 + kotlin.math.sin(ang2) * wr
                    if (k == 0) wpath.moveTo(vx2, vy2) else wpath.lineTo(vx2, vy2)
                }
                wpath.close()
                drawPath(wpath, Color(0xFF3D5A80).copy(alpha = 0.10f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
            // 背景网格（批42：数学坐标纸——每 5 格一条主格线，过暗不抢主体）
            val step = 64f
            var gx = -game.camX % step
            if (gx < 0) gx += step
            while (gx < size.width) {
                val worldX = gx + game.camX
                val major = (worldX / step).toInt() % 5 == 0
                drawLine(if (major) GRID_MAJOR else GRID, Offset(gx, 0f), Offset(gx, size.height), if (major) 3f else 2f)
                gx += step
            }
            var gy = -game.camY % step
            if (gy < 0) gy += step
            while (gy < size.height) {
                val worldY = gy + game.camY
                val major = (worldY / step).toInt() % 5 == 0
                drawLine(if (major) GRID_MAJOR else GRID, Offset(0f, gy), Offset(size.width, gy), if (major) 3f else 2f)
                gy += step
            }
            // 批53：格心装饰符号（hash 恒定：大小/角度/透明度三重变化，集中在轴附近引导视线）
            val symPool = listOf("π", "√", "∑", "∞", "∆", "+", "−", "×", "÷")
            var dgy = -game.camY % (step * 2)
            if (dgy < 0) dgy += step * 2
            while (dgy < size.height) {
                var dgx = -game.camX % (step * 2)
                if (dgx < 0) dgx += step * 2
                while (dgx < size.width) {
                    val wx2 = dgx + game.camX; val wy2 = dgy + game.camY
                    val h2 = ((wx2.toInt() * 73856093) xor (wy2.toInt() * 19349663))
                    if ((h2 ushr 5) % 100 < 22) {
                        val g2 = symPool[(h2 ushr 9) % symPool.size]
                        val rot2 = ((h2 ushr 13) % 30 - 15) * 0.0174f
                        val sc2 = 9 + (h2 ushr 17) % 7
                        val lay2 = glyphMetrics.getOrPut("deco$g2${sc2 / 4}") {
                            textMeasurer.measure(g2, TextStyle(fontSize = sc2.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        }
                        withTransform({
                            translate(dgx + step - 4f, dgy + step - 4f)
                            rotate(rot2, pivot = Offset.Zero)
                        }) {
                            drawText(lay2, color = Color(0x14FFFFFF), topLeft = Offset(-lay2.size.width / 2f, -lay2.size.height / 2f))
                        }
                    }
                    dgx += step * 2
                }
                dgy += step * 2
            }
            // X/Y 坐标轴（世界 0,0）与原点
            val ox = 0f - game.camX
            val oy = 0f - game.camY
            if (ox > -10f && ox < size.width + 10f) drawLine(AXIS_C, Offset(ox, 0f), Offset(ox, size.height), 4f)
            if (oy > -10f && oy < size.height + 10f) drawLine(AXIS_C, Offset(0f, oy), Offset(size.width, oy), 4f)
            if (ox > -30f && ox < size.width + 30f && oy > -30f && oy < size.height + 30f) {
                drawCircle(AXIS_C, 10f, Offset(ox, oy), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }

            // 经验球（批50：脉动 + 磁吸光晕 + 大球渲染）
            for (o in game.orbs) {
                if (!o.alive) continue
                val sx = o.x - game.camX; val sy = o.y - game.camY
                if (sx < -20f || sy < -20f || sx > size.width + 20f || sy > size.height + 20f) continue
                val pulse = 0.85f + 0.25f * kotlin.math.sin(game.elapsed * 5f + o.x * 0.03f)
                val orbR = if (o.value > 1) game.orbR * 1.6f else game.orbR
                if (o.magnet) drawCircle(ORB_C.copy(alpha = 0.18f), orbR + 6f, Offset(sx, sy))   // 磁吸光晕
                drawCircle(ORB_C, orbR * pulse, Offset(sx, sy))
                drawCircle(Color(0xFFDFFFE3), orbR * pulse * 0.35f, Offset(sx - orbR * 0.2f, sy - orbR * 0.2f))   // 高光
            }

            // 批50：死亡粒子迸溅（按 kind 配色，8 颗放射 + 中心白闪，寿命 0.35s）
            for (d in game.deathFx) {
                val k = (d.age / 0.35f).coerceIn(0f, 1f)
                val dcol = Color(d.color)
                val cx = d.x - game.camX; val cy = d.y - game.camY
                for (i in 0..7) {
                    val ang = i * 0.7853981f + d.seed % 16 * 0.1f
                    val dist = d.age * (140f + (i % 3) * 60f)
                    drawCircle(dcol.copy(alpha = (1f - k) * 0.9f), 3.5f * (1f - k * 0.6f), Offset(cx + kotlin.math.cos(ang) * dist, cy + kotlin.math.sin(ang) * dist))
                }
                if (d.age < 0.12f) {
                    val fw = 1f - d.age / 0.12f
                    drawCircle(Color.White.copy(alpha = fw * 0.85f), 18f * fw, Offset(cx, cy))
                }
            }

            // 敌人（受伤后画细血条）
            for (e in game.enemies) {
                if (!e.alive) continue
                val sx = e.x - game.camX; val sy = e.y - game.camY
                if (sx < -40f || sy < -40f || sx > size.width + 40f || sy > size.height + 40f) continue
                if (e.sides >= 3) {
                    // 批42：大怪=几何线框体（三角/方/五边），微光填充
                    val path = Path()
                    for (k in 0 until e.sides) {
                        val ang = -1.5707963f + k * (6.2831853f / e.sides)
                        val vx = sx + kotlin.math.cos(ang) * e.r
                        val vy = sy + kotlin.math.sin(ang) * e.r
                        if (k == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                    }
                    path.close()
                    val geoC = if (e.kind == 3) ENEMY_BIG_C else Color(0xFFFFB74D)   // 批48：几何狂橙，精英保持深红
                    drawPath(path, geoC.copy(alpha = 0.30f))
                    drawPath(path, geoC, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                    if (e.r > 28f) {
                        // 批45：精英 ∑ 求和体——几何框内金色大 ∑
                        var sumLayout = glyphMetrics["∑BIG"]
                        if (sumLayout == null) {
                            sumLayout = textMeasurer.measure("∑", TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            glyphMetrics["∑BIG"] = sumLayout
                        }
                        val stl = Offset(sx - sumLayout.size.width / 2f, sy - sumLayout.size.height / 2f)
                        for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                            drawText(sumLayout, color = ENEMY_BIG_C, topLeft = stl + off)
                        }
                        drawText(sumLayout, topLeft = stl)
                    }
                } else {
                    // 批48：按种类配色——数字崽红 / 运算符蜂青 / 几何狂橙
                    val kindColor = when (e.kind) {
                        1 -> Color(0xFF4DD0E1)
                        2 -> Color(0xFFFFB74D)
                        else -> if (e.r > 20f) ENEMY_BIG_C else ENEMY_C
                    }
                    drawCircle(kindColor, e.r, Offset(sx, sy))
                    // 批42：发光数学字形（四向重绘 + 白心）
                    var layout = glyphMetrics[e.glyph]
                    if (layout == null) {
                        layout = textMeasurer.measure(
                            e.glyph,
                            TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        )
                        glyphMetrics[e.glyph] = layout
                    }
                    val tl = Offset(sx - layout.size.width / 2f, sy - layout.size.height / 2f)
                    for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                        drawText(layout, color = kindColor, topLeft = tl + off)
                    }
                    drawText(layout, topLeft = tl)
                }
                // 批50：命中白闪（hitFlash 0.1s）
                if (e.hitFlash > 0f) {
                    drawCircle(Color.White.copy(alpha = (e.hitFlash * 6f).coerceAtMost(0.8f)), e.r + 2f, Offset(sx, sy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
                if (e.hp < e.maxHp) {
                    val w = e.r * 2f
                    drawRect(BAR_BG, Offset(sx - e.r, sy - e.r - 8f), Size(w, 3f))
                    drawRect(HP_C, Offset(sx - e.r, sy - e.r - 8f), Size(w * (e.hp / e.maxHp), 3f))
                }
            }

            // 子弹（批45：金色 × 乘号——两段交叉线，数学主题）
            for (b in game.bullets) {
                if (!b.alive) continue
                val bx = b.x - game.camX; val by = b.y - game.camY
                drawLine(BULLET_C, Offset(bx - 5f, by - 5f), Offset(bx + 5f, by + 5f), 3f)
                drawLine(BULLET_C, Offset(bx - 5f, by + 5f), Offset(bx + 5f, by - 5f), 3f)
            }

            // 批50：移动拖尾（渐隐青圆，越旧越淡越小）
            for (t in game.trail) {
                val tx = t.x - game.camX; val ty = t.y - game.camY
                val a = 1f - (t.age / 0.3f).coerceIn(0f, 1f)
                drawCircle(PLAYER_C.copy(alpha = 0.22f * a), game.playerR * (0.35f + 0.4f * a), Offset(tx, ty))
            }

            // 玩家（批53：程序化发光火柴人——《火柴人vs数学》美术本体）
            val psx = game.px - game.camX
            val psy = game.py - game.camY
            val blink = game.invincibleNow() > 0f && (game.elapsed * 12f).toInt() % 2 == 0
            if (!blink) {
                val hf = (game.hurtFlash / 0.3f).coerceIn(0f, 1f)
                val moving = game.joyActive && (game.joyX != 0f || game.joyY != 0f)
                val walk = game.elapsed * 10f
                val swing = if (moving) 1f else 0f
                val bob = if (moving) kotlin.math.sin(walk * 2f) * 1.5f else kotlin.math.sin(game.elapsed * 2.2f) * 1.2f
                val hipY = psy - 11f + bob * 0.4f
                val neckY = psy - 22f + bob
                val headY = neckY - 7.5f + bob
                val legSwing = kotlin.math.sin(walk) * 7f * swing
                val armSwing = kotlin.math.sin(walk + 3.14159f) * 5.5f * swing
                val atkP = game.attackProg()
                val fx = game.faceX; val fy = game.faceY
                val fl = kotlin.math.hypot(fx, fy).coerceAtLeast(0.001f)
                val dirX = fx / fl; val dirY = fy / fl
                val segs = mutableListOf<Pair<Offset, Offset>>()
                val neck = Offset(psx, neckY)
                val hip = Offset(psx, hipY)
                segs.add(neck to hip)   // 躯干
                val knee1 = Offset(psx + legSwing * 0.55f, hipY + 5.5f)
                val foot1 = Offset(knee1.x + legSwing * 0.5f, psy)
                val knee2 = Offset(psx - legSwing * 0.55f, hipY + 5.5f)
                val foot2 = Offset(knee2.x - legSwing * 0.5f, psy)
                segs.add(hip to knee1); segs.add(knee1 to foot1)
                segs.add(hip to knee2); segs.add(knee2 to foot2)
                val shoulder = Offset(neck.x, neck.y + 1.5f)
                val attacking = atkP < 0.25f
                if (attacking) {
                    // 批53：攻击姿态——前臂伸向目标方向（射击方向即朝向）
                    val hand = Offset(neck.x + dirX * 15f, neck.y + dirY * 15f - 3f)
                    val elbow = Offset((neck.x + hand.x) / 2f, (neck.y + hand.y) / 2f + 2f)
                    segs.add(shoulder to elbow); segs.add(elbow to hand)
                    val arm2Elbow = Offset(neck.x - dirX * 4f - armSwing * 0.4f, neck.y + 6f)
                    val arm2Hand = Offset(arm2Elbow.x - 3f, arm2Elbow.y + 7f)
                    segs.add(shoulder to arm2Elbow); segs.add(arm2Elbow to arm2Hand)
                } else {
                    val el1 = Offset(shoulder.x + armSwing * 0.5f, shoulder.y + 5.5f)
                    val hd1 = Offset(el1.x + armSwing * 0.45f, el1.y + 6f)
                    segs.add(shoulder to el1); segs.add(el1 to hd1)
                    val el2 = Offset(shoulder.x - armSwing * 0.5f, shoulder.y + 5.5f)
                    val hd2 = Offset(el2.x - armSwing * 0.45f, el2.y + 6f)
                    segs.add(shoulder to el2); segs.add(el2 to hd2)
                }
                // 受击挤压拉伸（绕脚底缩放，衰减随 hurtFlash）
                withTransform({
                    scale(1f + 0.12f * hf, 1f - 0.16f * hf, pivot = Offset(psx, psy))
                }) {
                    // 光晕层（粗线低透明）→ 主干层（细线）——火柴人是发光体
                    for (seg in segs) {
                        drawLine(PLAYER_C.copy(alpha = 0.30f), seg.first, seg.second, 8f)
                    }
                    for (seg in segs) {
                        drawLine(PLAYER_C, seg.first, seg.second, 3.5f)
                    }
                    // 头：发光圆 + 白核（朝向侧微偏）
                    val headPos = Offset(psx + dirX * 1.5f, headY)
                    drawCircle(PLAYER_C.copy(alpha = 0.30f), 9.5f, headPos)
                    drawCircle(PLAYER_C, 6.5f, headPos)
                    drawCircle(Color.White, 2.6f, Offset(headPos.x - 1f, headPos.y - 1.5f))
                    // 关节点（肘/膝小亮点）
                    for (j in listOf(knee1, knee2)) drawCircle(Color.White.copy(alpha = 0.5f), 1.5f, j)
                }
            }

            // 虚拟摇杆
            if (game.joyActive) {
                drawCircle(JOY_C, 56f, Offset(game.joyBaseX, game.joyBaseY), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(JOY_C, 26f, Offset(game.joyBaseX + game.joyX * joyMaxPx, game.joyBaseY + game.joyY * joyMaxPx))
            }

            // 批50：升级金环（从升级点扩散，0.5s）
            if (game.levelRingT > 0f) {
                val rx = game.levelRingX - game.camX; val ry = game.levelRingY - game.camY
                val k = (game.levelRingT / 0.5f).coerceIn(0f, 1f)
                drawCircle(GameUi.Gold.copy(alpha = 0.7f * k), 170f * (1f - k) + 20f, Offset(rx, ry), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
            }

            // 批50：受击红晕（屏幕边缘，随 hurtFlash 衰减）
            if (game.hurtFlash > 0f) {
                val a = (game.hurtFlash / 0.3f).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0x00FF5252), Color(0x66FF5252).copy(alpha = 0.5f * a), Color(0x00FF5252)),
                        startY = 0f, endY = size.height,
                    )
                )
            }

        }

        // ---------- HUD（批24：左上玩家面板 + 中央目标芯片 + 右上暂停） ----------
        if (hud.phase == SurvivorGame.Phase.PLAYING) {
            Row(
                Modifier.fillMaxWidth()
                    .gameTopInsets()
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
        // ---------- 批48：数学空间图鉴（4 种怪档案） ----------
        if (showCodex) {
            Box(Modifier.fillMaxSize().background(Color(0xE6070A14))) {
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))   // 批61：图鉴补 insets（Top 用 statusBars，同 gameTopInsets 缘由）
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("←", onClick = { showCodex = false }, size = 42.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("📐 数学空间图鉴", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GameUi.Gold)
                            Text(
                                "已遭遇 ${survCodexEntries.count { player.survivorSeen.contains(it.key) }} / ${survCodexEntries.size} · 击杀后解锁档案",
                                fontSize = 10.sp, color = GameUi.TextDim,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    survCodexEntries.forEach { e ->
                        val seen = player.survivorSeen.contains(e.key)   // 批48 修正：读持久化而非每局实例
                        GamePanel(Modifier.fillMaxWidth().padding(vertical = 6.dp), accent = if (seen) GameUi.Gold else null, contentPadding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.glyph, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (seen) e.color else Color(0x55FFFFFF))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(if (seen) e.name else "？？？（${e.name}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(if (seen) "已收录" else "未遭遇", fontSize = 10.sp, color = if (seen) GameUi.Gold else GameUi.TextFaint)
                                }
                            }
                            if (seen) {
                                Text("属性：${e.attr}", style = MaterialTheme.typography.bodySmall, color = GameUi.Text, modifier = Modifier.padding(top = 8.dp))
                                Text("行动习惯：${e.behavior}", style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim, modifier = Modifier.padding(top = 3.dp))
                            } else {
                                Text("档案加密——遭遇并击杀后解锁。", style = MaterialTheme.typography.bodySmall, color = GameUi.TextFaint, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GameActionButton("返回", onClick = { showCodex = false }, primary = false, height = 44.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (hud.phase == SurvivorGame.Phase.READY && !showCodex) {   // 批48：图鉴开启时让位（坑40 叠加顺序）
            Box(Modifier.fillMaxSize()) {
                GameBackdrop(accent = Color(0xFF4FC3F7))
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))   // 批61：READY 补 insets
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GamePanel(Modifier.widthIn(max = 560.dp), accent = Color(0xFF4FC3F7), contentPadding = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("∑", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)   // 批42
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    "迷你幸存者",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text("数学空间 · 几何生存 · 三选一", fontSize = 11.sp, color = GameUi.TextDim)   // 批42 主题化
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
                            "📜 图鉴",
                            onClick = { showCodex = true },
                            primary = false,
                            height = 44.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
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
            // 批50：卡片入场动画（alpha/scale 220ms）
            val lvIn = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(hud.phase) {
                if (hud.phase == SurvivorGame.Phase.LEVELUP) {
                    lvIn.snapTo(0f)
                    lvIn.animateTo(1f, androidx.compose.animation.core.tween(220))
                }
            }
            Box(
                Modifier.fillMaxSize().background(Color(0xB305070C)).graphicsLayer {
                    alpha = 0.4f + 0.6f * lvIn.value
                    val sc = 0.92f + 0.08f * lvIn.value
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))   // 批61：升级三选一补 insets
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp),
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
                            GamePanel(Modifier.weight(1f), accent = if (up.rare) GameUi.Gold else GameUi.Xp, contentPadding = 16.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 批42：强化的数学字形标识
                                    Text(
                                        upGlyph[up.id] ?: "?",
                                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GameUi.Xp,
                                    )
                                    Text(
                                        if (up.rare) "✦ 稀有强化" else "强化 0${idx + 1}",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (up.rare) GameUi.Gold.copy(alpha = 0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(game.elapsed * 5f))) else GameUi.Gold,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )   // 批45：稀有卡金标；批53：流光
                                }
                                Text(
                                    up.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(up.desc, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)
                                // 批53：数值对比（当前 → 升级后，从实际数值计算）
                                val curTxt = when (up.id) {
                                    "atk", "atk2" -> "伤害 ${"%.0f".format(game.attackDmg)}"
                                    "aspd" -> "间隔 ${"%.2f".format(game.attackInterval)}s"
                                    "spd" -> "移速 ${"%.0f".format(game.moveSpeed)}"
                                    "hp", "hp2" -> "生命 ${"%.0f".format(game.maxHp)}"
                                    else -> "拾取 ${"%.0f".format(game.pickupRange)}"
                                }
                                val aftTxt = when (up.id) {
                                    "atk" -> "伤害 ${"%.0f".format(game.attackDmg * 1.25f)}"
                                    "atk2" -> "伤害 ${"%.0f".format(game.attackDmg * 1.5f)}"
                                    "aspd" -> "间隔 ${"%.2f".format(game.attackInterval * 0.85f)}s"
                                    "spd" -> "移速 ${"%.0f".format(game.moveSpeed * 1.10f)}"
                                    "hp" -> "生命 ${"%.0f".format(game.maxHp + 25f)}"
                                    "hp2" -> "生命 ${"%.0f".format(game.maxHp + 60f)}"
                                    else -> "拾取 ${"%.0f".format(game.pickupRange * 1.30f)}"
                                }
                                Text(
                                    "$curTxt → $aftTxt",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GameUi.Xp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
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
        }   // 批53：Box(graphicsLayer) 闭合

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
            // 批53：结算卡入场动画 + 新纪录脉动
            val goIn = remember { androidx.compose.animation.core.Animatable(0f) }
            val recPulse = rememberInfiniteTransition(label = "recPulse")
            val recA = recPulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse), label = "recA")
            androidx.compose.runtime.LaunchedEffect(hud.phase) {
                if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
                    goIn.snapTo(0f)
                    goIn.animateTo(1f, androidx.compose.animation.core.tween(220))
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 0.3f + 0.7f * goIn.value
                    val sc = 0.9f + 0.1f * goIn.value
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center,
            ) {
            GameDialog(title = "💀 游戏结束", accent = GameUi.Hp, width = 480.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (newRecord) {
                        Text(
                            "🎉 新纪录！",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GameUi.Gold.copy(alpha = recA.value),   // 批53：金色脉动
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


/** 批48：数学空间图鉴条目 */
private data class SurvCodexEntry(
    val key: String,
    val kindIdx: Int,
    val glyph: String,
    val name: String,
    val color: androidx.compose.ui.graphics.Color,
    val attr: String,
    val behavior: String,
)

private val survCodexEntries = listOf(
    SurvCodexEntry("SURV_DIGIT", 0, "1", "数字崽", androidx.compose.ui.graphics.Color(0xFFE15A5A),
        "血量低 · 速度中 · 威胁低",
        "无脑直线扑向玩家，成群出没——数量是唯一威胁"),
    SurvCodexEntry("SURV_OP", 1, "+", "运算符蜂", androidx.compose.ui.graphics.Color(0xFF4DD0E1),
        "血量低 · 速度快 · 威胁中",
        "顶着 + − 符号沿正弦轨迹绕飞，天然避开直线弹幕——需要预判拦截"),
    SurvCodexEntry("SURV_GEOM", 2, "△", "几何狂", androidx.compose.ui.graphics.Color(0xFFFFB74D),
        "血量中 · 速度爆发 · 威胁中高",
        "多边形线框体，每 3 秒蓄力冲刺一次（2.2 倍速），冲刺前会靠近锁定"),
    SurvCodexEntry("SURV_ELITE", 3, "∑", "∑ 求和体", androidx.compose.ui.graphics.Color(0xFFB23A3A),
        "血量极高 · 速度慢 · 威胁高",
        "每 60 秒降临的精英：六边线框+金色大 ∑，缓慢但坚定——击杀掉落大经验，优先级目标"),
)
