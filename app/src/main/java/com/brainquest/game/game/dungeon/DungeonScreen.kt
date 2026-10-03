package com.brainquest.game.game.dungeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.R
import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.Equipment
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.RoomType
import com.brainquest.game.game.dungeon.render.EntityRenderer
import com.brainquest.game.game.dungeon.render.GamePalette
import kotlin.math.hypot

private val BG = com.brainquest.game.game.dungeon.render.GamePalette.BG_DEEP
private val JOY_C = com.brainquest.game.game.dungeon.render.GamePalette.UI_SHOUT

/** HUD 快照：只在变化时写 Compose 状态（数值条由 Canvas 每帧绘制） */
private data class Hud(
    val phase: DungeonGame.Phase,
    val floor: Int,
    val cleared: Int,
    val roomsTotal: Int,
    val hp: Int,
    val maxHp: Int,
    val level: Int,
    val timeSec: Int,
    val kills: Int,
    val bossHp: Float,      // 0..1，<=0 = 无 Boss
    val bossPhase: Int,
    val skillReady: Boolean,
    val skillCd: Int,
    val streak: Int,
    val lobbyCls: String,
    val portalNear: Boolean,
    val hasPortal: Boolean,
    val transition: Float,
)

/** 地牢幸存者：选职业 → 探索地牢（清怪开门选房间）→ 层末 Boss → 5 层通关 */
@Composable
fun DungeonScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastPlayerFlash = 0f

    // 游戏实例全局唯一：重开走 reset()，防止摇杆 pointerInput(Unit) 闭包绑旧实例
    val game = remember { DungeonGame() }
    val engineRef = game.engine
    // DEBUG：自动化验收的自动驾驶（intent extra 打开）
    LaunchedEffect(Unit) {
        game.autopilot = com.brainquest.game.util.DebugFlags.autopilot
        game.engine.autoAttack = game.autopilot   // 手动模式走攻击键；自动驾驶保持持续攻击
    }
    var hud by remember {
        mutableStateOf(Hud(DungeonGame.Phase.READY, 1, 1, 0, 100, 100, 1, 0, 0, 0f, 0, true, 0, 0, "knight", false, false, 0f))
    }
    var confirmExit by remember { mutableStateOf(false) }
    var showBag by remember { mutableStateOf(false) }
    var joyBase by remember { mutableStateOf(Offset.Zero) }
    var joyOn by remember { mutableStateOf(false) }
    val frame = remember { mutableIntStateOf(0) }
    val animT = remember { mutableFloatStateOf(0f) }   // 统一动画时间源（真实 dt 累计，秒）
    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    // 地牢内锁横屏（manifest 已加 configChanges，旋转不重建、游戏局不丢）；退出恢复竖屏
    // 真全屏：挖孔屏短边延伸 + 系统栏透明 + sticky 沉浸
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val win = activity?.window
        val ctrl = win?.let { w ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT)
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
            @Suppress("DEPRECATION")
            w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            androidx.core.view.WindowInsetsControllerCompat(w, w.decorView).apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ctrl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            win?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, true) }
        }
    }

    // 特效层：把引擎的帧事件转成有生命周期的持续特效（飘字/粒子/闪电；对象池复用 + 硬上限）
    val floats = remember { ArrayList<FloatFx>(32) }
    val parts = remember { ArrayList<ParticleFx>(64) }
    val bolts = remember { ArrayList<BoltFx>(8) }
    val rings = remember { ArrayList<RingFx>(4) }
    val trails = remember { ArrayList<TrailFx>(8) }
    val floatPool = remember { ArrayDeque<FloatFx>() }
    val partPool = remember { ArrayDeque<ParticleFx>() }
    val textCache = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    val rngFx = remember { kotlin.random.Random(7) }
    // 暗角渐变（只在画布尺寸变化时重建，避免每帧分配）
    val vignetteHolder = remember { arrayOfNulls<androidx.compose.ui.graphics.Brush>(1) }
    val vignetteSize = remember { floatArrayOf(0f, 0f) }
    // 升级反馈：全屏白光 + 面板弹性放大
    val lvlScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val lvlFlash = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hud.phase) {
        if (hud.phase == DungeonGame.Phase.LEVELUP || hud.phase == DungeonGame.Phase.SKILL_SELECT) {
            lvlFlash.floatValue = 0.22f
            lvlScale.snapTo(0.85f)
            lvlScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 380f))
        }
    }

    // 游戏循环
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            var dt = 0f
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) { dt = ((t - last) / 1e9f).coerceIn(0f, 0.05f); game.tick(dt) }
                last = t
            }
            frame.intValue++
            animT.floatValue += dt
            if (lvlFlash.floatValue > 0f) lvlFlash.floatValue = (lvlFlash.floatValue - dt).coerceAtLeast(0f)
            if (game.engine.playerFlash > 0.13f && lastPlayerFlash <= 0.13f) {
                DungeonSfx.play(context, player.soundOn, R.raw.dg_hurt, 0.7f, 250)
            }
            lastPlayerFlash = game.engine.playerFlash
            val dtFx = dt.coerceAtLeast(1f / 120f)
            // 消费引擎帧事件
            fun obtainFloat(): FloatFx = floatPool.removeFirstOrNull() ?: FloatFx(0f, 0f, "", Color.White, false)
            fun obtainPart(): ParticleFx = partPool.removeFirstOrNull() ?: ParticleFx()
            for (ev in game.engine.events) {
                when (ev.kind) {
                    0 -> {   // 伤害飘字 + 命中点迸溅粒子
                        val c = when {
                            ev.element == com.brainquest.game.game.core.Element.FIRE -> GamePalette.ELEM_FIRE
                            ev.element == com.brainquest.game.game.core.Element.ICE -> GamePalette.ELEM_ICE
                            ev.element == com.brainquest.game.game.core.Element.THUNDER -> GamePalette.ELEM_LIGHTNING
                            ev.crit -> GamePalette.UI_GOLD
                            else -> GamePalette.UI_TEXT
                        }
                        if (floats.size < 100) {
                            floats.add(obtainFloat().also { it.set(ev.x, ev.y, ev.text, c, ev.crit) })
                        }
                        if (parts.size < 300) {
                            repeat(4) {
                                val ang = rngFx.nextFloat() * 6.283f
                                val sp = 60f + rngFx.nextFloat() * 120f
                                parts.add(obtainPart().also {
                                    it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.3f, c)
                                })
                            }
                        }
                        if (ev.crit) DungeonSfx.play(context, player.soundOn, R.raw.dg_crit, 0.65f, 140)
                        else DungeonSfx.play(context, player.soundOn, R.raw.dg_hit, 0.55f, 90)
                    }
                    1 -> {   // 死亡爆裂粒子（按敌人主色）
                        val pc = if (ev.tint != 0) Color(ev.tint) else GamePalette.UI_HP
                        if (parts.size < 300) {
                            repeat(10) {
                                val ang = rngFx.nextFloat() * 6.283f
                                val sp = 90f + rngFx.nextFloat() * 140f
                                parts.add(obtainPart().also {
                                    it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.4f, pc)
                                })
                            }
                        }
                    }
                    2 -> { if (floats.size < 100) floats.add(obtainFloat().also { it.set(ev.x, ev.y, ev.text, GamePalette.UI_ORB, false) }); DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.4f, 120) }
                    3 -> bolts.add(BoltFx(ev.x, ev.y, ev.x2, ev.y2, 0.15f))
                    4 -> { rings.add(RingFx(ev.x, ev.y, GamePalette.BOSS_GLOW, 220f, 0.6f)); DungeonSfx.play(context, player.soundOn, R.raw.dg_boss, 0.8f, 1500) }
                    8 -> rings.add(RingFx(ev.x, ev.y, Color(0xB0ECEFF1), 70f, 0.22f))
                    9 -> if (parts.size < 300) {
                        val mc = when (ev.element) {
                            com.brainquest.game.game.core.Element.FIRE -> GamePalette.ELEM_FIRE
                            com.brainquest.game.game.core.Element.ICE -> GamePalette.ELEM_ICE
                            else -> GamePalette.UI_TEXT
                        }
                        repeat(3) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 40f + rngFx.nextFloat() * 80f
                            parts.add(obtainPart().also { it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.18f, mc) })
                        }
                    }
                    5 -> if (trails.size < 12) trails.add(TrailFx(ev.x, ev.y))
                    6 -> rings.add(RingFx(ev.x, ev.y, GamePalette.UI_EXP, 90f, 0.5f))
                }
            }
            game.engine.events.clear()
            // 特效寿命推进（到期回收进池）
            floats.forEach { it.t += dtFx }
            floats.removeAll { if (it.t > 0.7f) { if (floatPool.size < 120) floatPool.addLast(it); true } else false }
            parts.forEach { it.t += dtFx; it.x += it.vx * dtFx; it.y += it.vy * dtFx }
            parts.removeAll { if (it.t > it.life) { if (partPool.size < 320) partPool.addLast(it); true } else false }
            bolts.forEach { it.t += dtFx }
            bolts.removeAll { it.t > 0.15f }
            rings.forEach { it.t += dtFx }
            rings.removeAll { it.t > it.life }
            trails.forEach { it.t += dtFx }
            trails.removeAll { it.t > 0.3f }
            val e = game.engine
            val boss = game.engine.enemies.firstOrNull { it.bossFloor > 0 && it.alive }
            val h = Hud(
                game.phase, game.floor, game.floorCleared, game.rooms.size,
                e.hp, e.maxHp, e.level, game.runTimeSec, game.totalKills,
                if (boss != null) boss.hp / boss.maxHp else 0f,
                boss?.phase ?: 0,
                e.skillCd <= 0f, e.skillCd.toInt(),
                e.killStreak,
                game.lobbyClassId,
                game.portalNear,
                game.portal != null,
                game.transition,
            )
            if (h != hud) hud = h
        }
    }

    // 攻击键按压反馈
    var attackPressed by remember { mutableStateOf(false) }

    var rewarded by remember { mutableStateOf(false) }
    LaunchedEffect(hud.phase) {
        when (hud.phase) {
            DungeonGame.Phase.LEVELUP -> DungeonSfx.play(context, player.soundOn, R.raw.dg_levelup, 0.7f, 0)
            DungeonGame.Phase.SKILL_SELECT -> DungeonSfx.play(context, player.soundOn, R.raw.dg_skill, 0.7f, 0)
            DungeonGame.Phase.VICTORY -> DungeonSfx.play(context, player.soundOn, R.raw.dg_victory, 0.85f, 0)
            DungeonGame.Phase.GAMEOVER -> DungeonSfx.play(context, player.soundOn, R.raw.dg_lose, 0.8f, 0)
            else -> {}
        }
        if ((hud.phase == DungeonGame.Phase.GAMEOVER || hud.phase == DungeonGame.Phase.VICTORY) && !rewarded) {
            rewarded = true
            vm.addDungeonResult(hud.floor, hud.kills, hud.timeSec, game.coins)
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
            .onSizeChanged { game.viewW = it.width.toFloat(); game.viewH = it.height.toFloat() },
    ) {
        // ---------- 世界绘制 ----------
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            if (off.x <= size.width * 0.45f && off.y >= size.height * 0.45f) {
                                joyOn = true
                                joyBase = Offset(96.dp.toPx(), size.height - 96.dp.toPx())   // 固定底座位置
                                game.engine.joyActive = true
                                game.engine.joyX = 0f
                                game.engine.joyY = 0f
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (!joyOn) return@detectDragGestures
                            val cur = change.position
                            var dx = cur.x - joyBase.x
                            var dy = cur.y - joyBase.y
                            val len = hypot(dx, dy)
                            if (len > joyMaxPx) { dx = dx / len * joyMaxPx; dy = dy / len * joyMaxPx }
                            game.engine.joyX = dx / joyMaxPx
                            game.engine.joyY = dy / joyMaxPx
                        },
                        onDragEnd = { joyOn = false; game.engine.joyActive = false; game.engine.joyX = 0f; game.engine.joyY = 0f },
                        onDragCancel = { joyOn = false; game.engine.joyActive = false; game.engine.joyX = 0f; game.engine.joyY = 0f },
                    )
                },
        ) {
            frame.intValue   // 订阅：每帧重绘
            val t = animT.floatValue
            // 暗角（画布尺寸变化才重建 Brush）
            if (vignetteSize[0] != size.width || vignetteSize[1] != size.height) {
                vignetteSize[0] = size.width; vignetteSize[1] = size.height
                vignetteHolder[0] = androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color.Transparent, Color(0x1A000000), Color(0x42000000)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = hypot(size.width, size.height) / 2f,
                )
            }
            // 震屏 + 相机缩放（Boss 战拉远）：世界层统一变换
            val shk = game.engine.shake
            val shx = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            val shy = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            withTransform({
                translate(shx, shy)
                scale(game.camZoom * DungeonGame.BASE_ZOOM, game.camZoom * DungeonGame.BASE_ZOOM, pivot = Offset(size.width / 2f, size.height / 2f))
            }) {
            EntityRenderer.drawWorld(this, game, t)
            EntityRenderer.drawPortal(this, game, t)
            // 靠近传送门：头顶「进入传送门」提示
            if (game.portal != null && hud.portalNear) {
                val pt = game.portal!!
                val px2 = pt.first - game.camX
                val py2 = pt.second - game.camY - 78f
                if (px2 > 8f && px2 < size.width - 8f && py2 > 8f && py2 < size.height - 8f) {
                    val key = "portal#hint"
                    var layout = textCache[key]
                    if (layout == null) {
                        layout = textMeasurer.measure("进入传送门", androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        textCache[key] = layout
                    }
                    drawText(layout, color = GamePalette.UI_COIN, topLeft = Offset(px2 - layout.size.width / 2f, py2))
                }
            }
            EntityRenderer.drawProjectiles(this, game)
            EntityRenderer.drawDrops(this, game, t)
            for (e in game.engine.enemies) EntityRenderer.drawEnemy(this, game, e, t)
            EntityRenderer.drawPlayer(this, game, t)
            // 锁定目标金色标记（头顶）
            game.engine.aimTarget?.let { at ->
                if (at.alive) {
                    val mx = at.x - game.camX
                    val my = at.y - game.camY - at.r - 22f
                    drawCircle(GamePalette.UI_GOLD.copy(alpha = 0.35f), 10f, Offset(mx, my), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    drawCircle(GamePalette.UI_GOLD, 3.5f, Offset(mx, my))
                }
            }
            // 特效：闪电 → 粒子 → 伤害飘字
            for (b in bolts) {
                val a = (1f - b.t / 0.15f).coerceIn(0f, 1f)
                val c = GamePalette.ELEM_LIGHTNING.copy(alpha = a)
                val mx = (b.x1 + b.x2) / 2 + (if ((b.x1 + b.x2).toInt() % 2 == 0) 14f else -14f)
                val my = (b.y1 + b.y2) / 2 + (if ((b.y1 - b.y2).toInt() % 2 == 0) -12f else 12f)
                drawLine(c, Offset(b.x1 - game.camX, b.y1 - game.camY), Offset(mx - game.camX, my - game.camY), 3f)
                drawLine(c, Offset(mx - game.camX, my - game.camY), Offset(b.x2 - game.camX, b.y2 - game.camY), 3f)
            }
            for (pt in parts) {
                val a = (1f - pt.t / 0.4f).coerceIn(0f, 1f)
                drawCircle(pt.color.copy(alpha = a), 3.5f, Offset(pt.x - game.camX, pt.y - game.camY))
            }
            for (rg in rings) {   // Boss 出场/治疗 冲击环
                val k = (rg.t / rg.life).coerceIn(0f, 1f)
                drawCircle(
                    rg.color.copy(alpha = (0.6f * (1f - k)).coerceIn(0f, 1f)),
                    20f + k * rg.maxR,
                    Offset(rg.x - game.camX, rg.y - game.camY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(6f * (1f - k) + 1f),
                )
            }
            for (tr in trails) {   // 冲刺残影
                val a = (1f - tr.t / 0.3f).coerceIn(0f, 1f)
                val bodyC = game.cls?.bodyColor?.let { Color(it) } ?: Color.White
                drawCircle(bodyC.copy(alpha = 0.35f * a), 14f, Offset(tr.x - game.camX, tr.y - game.camY))
            }
            for (ft in floats) {
                val a = (1f - ft.t / 0.7f).coerceIn(0f, 1f)
                // 画布外跳过（相机移动后飘字可能落在屏外，drawText 负 constraints 会崩溃）
                val tx = ft.x - game.camX
                val ty = ft.y - game.camY - ft.t * 70f
                if (tx < 8f || tx > size.width - 8f || ty < 8f || ty > size.height - 8f) continue
                // 布局缓存：同文本不重复测量（暴击/普通字号分开）
                val key = ft.text + if (ft.crit) "#C" else "#N"
                var layout = textCache[key]
                if (layout == null) {
                    if (textCache.size > 160) textCache.clear()
                    layout = textMeasurer.measure(
                        ft.text,
                        androidx.compose.ui.text.TextStyle(fontSize = if (ft.crit) 22.sp else 15.sp, fontWeight = FontWeight.Bold),
                    )
                    textCache[key] = layout
                }
                drawText(layout, color = ft.color.copy(alpha = a), topLeft = Offset(tx, ty))
            }
            // 挥砍轨迹：前摇淡显 → 挥出扇形渐扫 → 后摇淡出（由引擎 activeSlash 驱动）
            val en = game.engine
            en.activeSlash?.let { s ->
                val psx = en.px - game.camX
                val psy = en.py - game.camY
                val inSwing = s.timer >= s.windup && s.timer <= s.windup + 0.15f
                val after = s.timer > s.windup + 0.15f
                val alpha = when {
                    s.timer < s.windup -> 0.15f
                    inSwing -> 0.55f
                    else -> (0.55f * (1f - (s.timer - s.windup - 0.15f) / 0.1f)).coerceIn(0f, 0.55f)
                }
                val sweep = if (inSwing) 100f * ((s.timer - s.windup) / 0.15f).coerceIn(0f, 1f)
                            else if (after || s.fired) 100f else 0f
                if (alpha > 0.02f && sweep > 1f) {
                    // 攻击范围指示：扇形微光填充 + 亮弧边
                    drawArc(
                        Color(0x33FFFFFF).copy(alpha = alpha),
                        startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(psx - 95f, psy - 95f),
                        size = Size(190f, 190f),
                    )
                    drawArc(
                        Color(0x88FFFFFF).copy(alpha = alpha),
                        startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(psx - 95f, psy - 95f),
                        size = Size(190f, 190f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(12f),
                    )
                }
            }
            }   // withTransform（震屏+缩放）——数值条/暗角在屏幕层
            if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
                EntityRenderer.drawBars(this, game)
            }
            vignetteHolder[0]?.let { drawRect(it) }
            if (game.engine.timeScale < 1f) drawRect(Color(0x14264CCF))   // 缓时滤镜
            if (lvlFlash.floatValue > 0f) drawRect(Color.White.copy(alpha = lvlFlash.floatValue.coerceAtMost(0.5f)))   // 升级白光
            // 虚拟摇杆：固定左下底座常显（加大更醒目）
            val jb = Offset(110.dp.toPx(), size.height - 100.dp.toPx())
            drawCircle(Color(0x33FFFFFF), 78f, jb)
            drawCircle(JOY_C, 72f, jb, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            drawCircle(
                JOY_C, 32f,
                if (joyOn) Offset(jb.x + game.engine.joyX * joyMaxPx, jb.y + game.engine.joyY * joyMaxPx) else jb,
            )
        }

        // ---------- HUD（横屏布局：顶行文字 / 左上数值条+装备 / 右上小地图 / 左下摇杆 / 右下技能） ----------
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
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
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.height(36.dp),
            ) { Text("⏸") }
        }
        }   // HUD 阶段门控（READY/CLASS_SELECT 不显示）
        // 六槽装备芯片（品质色）：横屏下移到左上数值条下方，给摇杆留出整个左下区域
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Equipment.Slot.entries.forEach { slot ->
                    val it = game.slots[slot]
                    val c = it?.let { Color(it.rarityColorLong) } ?: Color(0x66888888)
                    Box(
                        Modifier
                            .width(30.dp).height(30.dp)
                            .background(c, RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(slot.label.take(1), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
        // 攻击按钮（右下大圆，按住出招）+ 技能/背包
        if (hud.phase == DungeonGame.Phase.EXPLORING) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 技能键：圆形 + 冷却环（就绪时绿环）
                Box(
                    Modifier
                        .size(64.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                if (engineRef.skillId != null && engineRef.skillCd <= 0f) {
                                    DungeonSfx.play(context, player.soundOn, R.raw.dg_skill, 0.6f, 300)
                                    engineRef.useSkill()
                                }
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        frame.intValue   // 每帧重绘订阅
                        drawCircle(Color(0xCC1A1F2E), size.minDimension / 2f - 1f)
                        drawCircle(GamePalette.UI_PANEL_EDGE, size.minDimension / 2f - 1f, style = Stroke(1.5f))
                        val cdMax = engineRef.skillCdMax
                        val cdFrac = if (cdMax > 0f) (engineRef.skillCd / cdMax).coerceIn(0f, 1f) else 0f
                        if (cdFrac > 0f) {
                            drawArc(Color(0xFF78909C).copy(alpha = 0.6f), -90f, 360f * (1f - cdFrac), useCenter = false,
                                style = Stroke(3f))
                        } else if (engineRef.skillId != null) {
                            drawArc(GamePalette.UI_EXP, -90f, 360f, useCenter = false, style = Stroke(2.5f))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (engineRef.skillId) {
                                "dash" -> "💨"; "shield" -> "🛡"; "heal" -> "💚"; "slowtime" -> "⏳"
                                "freeze" -> "❄️"; "meteor" -> "☄️"; "chain" -> "⚡"; else -> "✨"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (engineRef.skillCd > 0f) {
                            Text("${engineRef.skillCd.toInt()}s", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0BEC5))
                        }
                    }
                }
                // 背包键：小圆
                Box(
                    Modifier
                        .size(48.dp)
                        .background(GamePalette.UI_PANEL, androidx.compose.foundation.shape.CircleShape)
                        .border(1.5.dp, GamePalette.UI_PANEL_EDGE, androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showBag = !showBag }) },
                    contentAlignment = Alignment.Center,
                ) { Text("🎒", style = MaterialTheme.typography.titleMedium) }
            }
            // 攻击键：大圆，按住出招；带挥砍就绪环；靠近传送门变「进入」交互键
            if (hud.hasPortal && hud.portalNear) {
                Box(
                    Modifier
                        .size(84.dp)
                        .background(Color(0x66BA68C8), androidx.compose.foundation.shape.CircleShape)
                        .border(2.dp, GamePalette.UI_COIN, androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(Unit) { detectTapGestures(onTap = { game.enterPortal() }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("进入", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GamePalette.UI_COIN)
                        Text("传送门", style = MaterialTheme.typography.labelSmall, color = GamePalette.UI_COIN)
                    }
                }
            } else {
            Box(
                Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        val k = if (attackPressed) 0.93f else 1f; scaleX = k; scaleY = k
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                attackPressed = true
                                engineRef.attackHeld = true
                                try { awaitRelease() } finally {
                                    attackPressed = false
                                    engineRef.attackHeld = false
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.intValue
                    drawCircle(Color(0xCC1A1F2E), size.minDimension / 2f - 1f)
                    drawCircle(Color(0x88FFFFFF), size.minDimension / 2f - 1f, style = Stroke(2f))
                    // 挥砍就绪环：出手间隔走完亮一圈
                    val at = engineRef.attackTimer
                    val ai = engineRef.attackInterval
                    if (at > 0f && ai > 0f) {
                        drawArc(GamePalette.UI_GOLD.copy(alpha = 0.7f), -90f, 360f * (1f - (at / ai).coerceIn(0f, 1f)),
                            useCenter = false, style = Stroke(3f))
                    }
                }
                Text("🗡", style = MaterialTheme.typography.headlineLarge)
            }
            }
            }
        }
        if (showBag) {
            Column(
                Modifier.fillMaxSize().background(Color(0xD0101018)).padding(top = 60.dp).padding(16.dp),
            ) {
                Text("🎒 装备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Equipment.Slot.entries.forEach { slot ->
                    val it = game.slots[slot]
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(26.dp).height(26.dp)
                                .background(it?.let { c2 -> Color(c2.rarityColorLong) } ?: Color(0x66888888), RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(slot.label.take(1), style = MaterialTheme.typography.labelSmall, color = Color.White) }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(it?.name ?: "空 · ${slot.label}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(
                                it?.describe() ?: "击败敌人有概率掉落",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0BEC5),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showBag = false }, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }

        // Boss 血条（顶部中央，横屏压窄）
        if (hud.bossHp > 0f) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 44.dp).fillMaxWidth(0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "👹 第" + hud.floor + "层 Boss · 阶段 " + hud.bossPhase,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF8A80),
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier.fillMaxWidth().height(10.dp).background(Color(0x66000000), RoundedCornerShape(5.dp)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(hud.bossHp.coerceIn(0f, 1f)).height(10.dp)
                            .background(Color(0xFFE15A5A), RoundedCornerShape(5.dp)),
                    )
                }
            }
        }

        // 小地图（右上，暂停按钮下方；横屏压缩尺寸）
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            Minimap(game, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 12.dp))
        }

        // 连杀提示（顶部中央，Boss 血条下方；10+ 金色）
        if (hud.streak >= 3 && hud.phase == DungeonGame.Phase.EXPLORING) {
            Text(
                "🔥 ${hud.streak} 连杀",
                Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (hud.streak >= 10) Color(0xFFFFD54F) else Color.White,
            )
        }

        // ---------- 过场：黑幕 + 「第 X 层」 ----------
        if (hud.transition > 0f) {
            val a = (minOf(hud.transition / 0.25f, (1.2f - hud.transition) / 0.25f)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f * a)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "第 ${hud.floor + 1} 层",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = a),
                )
            }
        }

        // ---------- 游戏大厅（选职业 → 开始冒险） ----------
        if (hud.phase == DungeonGame.Phase.READY) {
            val lobbyCls = ClassDef.byId(hud.lobbyCls)
            Row(Modifier.fillMaxSize().background(Color(0xEE0B0E14)).padding(horizontal = 24.dp)) {
                // 左：人物立绘 + 切换职业
                Column(
                    Modifier.weight(1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "🏰 地牢幸存者",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GamePalette.UI_GOLD,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { game.cycleClass(-1) },
                            modifier = Modifier.size(52.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                        ) { Text("◀") }
                        androidx.compose.foundation.Canvas(
                            Modifier.width(190.dp).height(230.dp).padding(horizontal = 6.dp),
                        ) {
                            EntityRenderer.drawPortrait(this, lobbyCls, size.width / 2, size.height * 0.56f, 1.15f, animT.floatValue)
                        }
                        OutlinedButton(
                            onClick = { game.cycleClass(1) },
                            modifier = Modifier.size(52.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                        ) { Text("▶") }
                    }
                    Text(lobbyCls.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "❤️${lobbyCls.maxHp} · ⚔️${lobbyCls.attack} · ${lobbyCls.weaponName} · 被动：${lobbyCls.passiveName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0BEC5),
                    )
                }
                // 右：模式 / 开始（纪录在底部小字）
                Column(
                    Modifier.weight(1.1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 模式卡
                    Card(
                        Modifier.fillMaxWidth(0.9f).padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = GamePalette.UI_PANEL),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🗡️", style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text("标准模式 · 5 层", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("清怪开门 · 层末 Boss · 通关轮回", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0BEC5))
                            }
                            Text("可选", style = MaterialTheme.typography.labelSmall, color = GamePalette.UI_EXP)
                        }
                    }
                    Card(
                        Modifier.fillMaxWidth(0.9f).padding(top = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x55263242)),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("♾️", style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text("无尽模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
                                Text("敬请期待", style = MaterialTheme.typography.labelSmall, color = Color(0xFF546E7A))
                            }
                        }
                    }
                    // 开始冒险
                    Button(
                        onClick = { game.pendingPerks = player.dungeonPerks; game.startFromLobby() },
                        modifier = Modifier.fillMaxWidth(0.9f).padding(top = 14.dp).height(52.dp),
                    ) { Text("▶ 开始冒险", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth(0.9f).padding(top = 6.dp).height(40.dp)) { Text("返回") }
                    Text(
                        "🏆 最高纪录 ${player.bestScores["dungeon_floor"] ?: 0} 层 · 最高击杀 ${player.bestScores["dungeon_kills"] ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF78909C),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        // ---------- 职业选择 ----------
        if (hud.phase == DungeonGame.Phase.CLASS_SELECT) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xF00B0D14))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("选择职业", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "最高层数纪录：${player.bestScores["dungeon_floor"] ?: 0} 层",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFD54F),
                    modifier = Modifier.padding(top = 2.dp),
                )
                // 🧬 局外加成已并入职业基础数值（真天赋系统规划于二期）
                Row(Modifier.fillMaxWidth(0.94f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClassDef.ALL.forEach { c ->
                        Card(
                            onClick = { game.pendingPerks = player.dungeonPerks; game.selectClass(c.id) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.foundation.Canvas(Modifier.width(40.dp).height(54.dp)) {
                                    drawRoundRect(Color(c.bodyColor), Offset(size.width / 2 - 11, size.height / 2 - 10), Size(22f, 26f), CornerRadius(6f))
                                    drawCircle(Color(c.accentColor), 10f, Offset(size.width / 2, size.height / 2 - 22f))
                                }
                                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(c.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "❤️${c.maxHp} ⚔️${c.attack}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- LevelUp 三选一（读引擎候选） ----------
        if (hud.phase == DungeonGame.Phase.LEVELUP) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .graphicsLayer { scaleX = lvlScale.value; scaleY = lvlScale.value; alpha = 0.4f + 0.6f * lvlScale.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⬆️ 升级！选择一项强化", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Row(Modifier.fillMaxWidth(0.94f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    game.engine.pendingUpgrades.forEach { up ->
                        Card(
                            onClick = { game.chooseUpgrade(up.id) },
                            modifier = Modifier.weight(1f),
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
        }

        // ---------- 技能三选一（每层结束） ----------
        if (hud.phase == DungeonGame.Phase.SKILL_SELECT) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .graphicsLayer { scaleX = lvlScale.value; scaleY = lvlScale.value; alpha = 0.4f + 0.6f * lvlScale.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✨ 选择一个主动技能", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Row(Modifier.fillMaxWidth(0.94f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    game.pendingSkills.forEach { id ->
                        val pair = when (id) {
                            "dash" -> "💨 冲刺" to "朝面向瞬移 240px 并短暂无敌"
                            "shield" -> "🛡 护盾" to "8 秒内格挡 50 点伤害"
                            "heal" -> "💚 治疗" to "立即回复 40% 生命"
                            "slowtime" -> "⏳ 时间减速" to "5 秒内敌人减速 70%"
                            "freeze" -> "❄️ 全屏冰冻" to "冻结所有敌人 2.5 秒"
                            "meteor" -> "☄️ 陨石" to "最近敌人处大范围爆炸（4×攻击）"
                            else -> "⚡ 闪电链" to "从最近敌人连跳 4 次（2.5×攻击起）"
                        }
                        Card(
                            onClick = { game.chooseSkill(id) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(pair.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // ---------- GameOver ----------
        if (hud.phase == DungeonGame.Phase.GAMEOVER) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC0B0D14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("💀 你倒在了地牢里", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "第${hud.floor}层 · 存活 ${formatTime(hud.timeSec)} · 击杀 ${hud.kills} · 🪙 ${game.coins}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = { rewarded = false; game.reset(); game.toClassSelect() }, modifier = Modifier.padding(top = 16.dp).width(160.dp)) { Text("重新开始") }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp).width(160.dp)) { Text("返回应用") }
            }
        }

        // ---------- Victory ----------
        if (hud.phase == DungeonGame.Phase.VICTORY) {
            Column(
                Modifier.fillMaxSize().background(Color(0xCC0B0D14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🏆 地牢通关！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                Text(
                    "用时 ${formatTime(hud.timeSec)} · 击杀 ${hud.kills} · 等级 ${hud.level}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = { rewarded = false; game.reset(); game.toClassSelect() }, modifier = Modifier.padding(top = 16.dp).width(160.dp)) { Text("再来一局") }
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
    Canvas(modifier.width(124.dp).height(86.dp).background(Color(0x881A1F2E), RoundedCornerShape(10.dp)).border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))) {
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

        for (room in rooms) {
            for ((d, n) in room.neighbors) {
                if (d == Dir.LEFT || d == Dir.UP) continue
                if (!room.discovered && !n.discovered) continue
                drawLine(Color(0x88AAAAAA), cell(room), cell(n), 3f)
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

/** 伤害/拾取飘字（世界坐标，上浮淡出 0.7s；字段可变供对象池复用） */
private class FloatFx(var x: Float, var y: Float, var text: String, var color: Color, var crit: Boolean) {
    var t = 0f
    fun set(x: Float, y: Float, text: String, color: Color, crit: Boolean) {
        this.x = x; this.y = y; this.text = text; this.color = color; this.crit = crit; this.t = 0f
    }
}

/** 粒子（字段可变供对象池复用） */
private class ParticleFx() {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var t = 0f; var life = 0.4f
    var color: Color = Color.White
    fun set(x: Float, y: Float, vx: Float, vy: Float, life: Float, color: Color) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.t = 0f; this.life = life; this.color = color
    }
}

/** 闪电链段 */
private class BoltFx(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var t: Float)

/** Boss 出场/治疗冲击环 */
private class RingFx(val x: Float, val y: Float, val color: Color, val maxR: Float, var life: Float = 0.6f) {
    var t = 0f
}

/** 冲刺残影 */
private class TrailFx(val x: Float, val y: Float) {
    var t = 0f
}
