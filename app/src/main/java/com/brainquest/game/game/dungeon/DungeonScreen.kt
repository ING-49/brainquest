package com.brainquest.game.game.dungeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.Equipment
import androidx.compose.foundation.border
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
)

/** 地牢幸存者：选职业 → 探索地牢（清怪开门选房间）→ 层末 Boss → 5 层通关 */
@Composable
fun DungeonScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()

    // 游戏实例全局唯一：重开走 reset()，防止摇杆 pointerInput(Unit) 闭包绑旧实例
    val game = remember { DungeonGame() }
    val engineRef = game.engine
    // DEBUG：自动化验收的自动驾驶（intent extra 打开）
    LaunchedEffect(Unit) {
        game.autopilot = com.brainquest.game.util.DebugFlags.autopilot
    }
    var hud by remember {
        mutableStateOf(Hud(DungeonGame.Phase.READY, 1, 1, 0, 100, 100, 1, 0, 0, 0f, 0, true, 0))
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
    // 同步进入沉浸模式（隐藏状态栏/导航栏），退出时恢复
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val win = activity?.window
        val ctrl = win?.let { w ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
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

    // 特效层：把引擎的帧事件转成有生命周期的持续特效（飘字/粒子/闪电）
    val floats = remember { ArrayList<FloatFx>(32) }
    val parts = remember { ArrayList<ParticleFx>(64) }
    val bolts = remember { ArrayList<BoltFx>(8) }
    val rings = remember { ArrayList<RingFx>(4) }
    val rngFx = remember { kotlin.random.Random(7) }

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
            val dtFx = dt.coerceAtLeast(1f / 120f)
            // 消费引擎帧事件
            for (ev in game.engine.events) {
                when (ev.kind) {
                    0 -> floats.add(FloatFx(ev.x, ev.y, ev.text,
                        when {
                            ev.element == com.brainquest.game.game.core.Element.FIRE -> GamePalette.ELEM_FIRE
                            ev.element == com.brainquest.game.game.core.Element.ICE -> GamePalette.ELEM_ICE
                            ev.element == com.brainquest.game.game.core.Element.THUNDER -> GamePalette.ELEM_LIGHTNING
                            ev.crit -> GamePalette.UI_GOLD
                            else -> GamePalette.UI_TEXT
                        }, ev.crit))
                    1 -> {   // 死亡爆裂粒子（按敌人主色）
                        val pc = if (ev.tint != 0) Color(ev.tint) else GamePalette.UI_HP
                        repeat(10) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 90f + rngFx.nextFloat() * 140f
                            parts.add(ParticleFx(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp,
                                0.4f, pc))
                        }
                    }
                    2 -> floats.add(FloatFx(ev.x, ev.y, ev.text, GamePalette.UI_ORB, false))
                    3 -> bolts.add(BoltFx(ev.x, ev.y, ev.x2, ev.y2, 0.15f))
                    4 -> rings.add(RingFx(ev.x, ev.y))
                }
            }
            game.engine.events.clear()
            // 特效寿命推进
            floats.forEach { it.t += dtFx }
            floats.removeAll { it.t > 0.7f }
            parts.forEach { it.t += dtFx; it.x += it.vx * dtFx; it.y += it.vy * dtFx }
            parts.removeAll { it.t > 0.4f }
            bolts.forEach { it.t += dtFx }
            bolts.removeAll { it.t > 0.15f }
            rings.forEach { it.t += dtFx }
            rings.removeAll { it.t > 0.6f }
            val e = game.engine
            val boss = game.engine.enemies.firstOrNull { it.bossFloor > 0 && it.alive }
            val h = Hud(
                game.phase, game.floor, game.floorCleared, game.rooms.size,
                e.hp, e.maxHp, e.level, game.runTimeSec, game.totalKills,
                if (boss != null) boss.hp / boss.maxHp else 0f,
                boss?.phase ?: 0,
                e.skillCd <= 0f, e.skillCd.toInt(),
            )
            if (h != hud) hud = h
        }
    }

    var rewarded by remember { mutableStateOf(false) }
    LaunchedEffect(hud.phase) {
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
                            if (off.x <= size.width / 2f) {
                                joyOn = true
                                joyBase = off
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
            // 震屏：引擎 shake 衰减期间相机随机抖动
            val shk = game.engine.shake
            val shx = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            val shy = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            withTransform({ translate(shx, shy) }) {
            EntityRenderer.drawWorld(this, game, t)
            EntityRenderer.drawProjectiles(this, game)
            EntityRenderer.drawDrops(this, game, t)
            for (e in game.engine.enemies) EntityRenderer.drawEnemy(this, game, e, t)
            EntityRenderer.drawPlayer(this, game, t)
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
            for (rg in rings) {   // Boss 出场冲击环
                val k = rg.t / 0.6f
                drawCircle(
                    GamePalette.BOSS_GLOW.copy(alpha = (0.6f * (1f - k)).coerceIn(0f, 1f)),
                    20f + k * 220f,
                    Offset(rg.x - game.camX, rg.y - game.camY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(6f * (1f - k) + 1f),
                )
            }
            for (ft in floats) {
                val a = (1f - ft.t / 0.7f).coerceIn(0f, 1f)
                // 画布外跳过（相机移动后飘字可能落在屏外，drawText 负 constraints 会崩溃）
                val tx = ft.x - game.camX
                val ty = ft.y - game.camY - ft.t * 70f
                if (tx < 8f || tx > size.width - 8f || ty < 8f || ty > size.height - 8f) continue
                drawText(
                    textMeasurer, ft.text,
                    Offset(tx, ty),
                    style = androidx.compose.ui.text.TextStyle(
                        color = ft.color.copy(alpha = a),
                        fontSize = if (ft.crit) 22.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            // 挥砍弧光（剑士出刀瞬间）
            val en = game.engine
            if (en.attackTimer > en.attackInterval - 0.16f && en.attackInterval > 0f) {
                val a = (en.attackTimer - (en.attackInterval - 0.16f)) / 0.16f
                val psx = en.px - game.camX
                val psy = en.py - game.camY
                drawArc(
                    Color(0x88FFFFFF).copy(alpha = 0.5f * a),
                    startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(psx - 95f, psy - 95f),
                    size = Size(190f, 190f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(8f),
                )
            }
            if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
                EntityRenderer.drawBars(this, game)
            }
            }   // withTransform 震屏
            // 虚拟摇杆
            if (joyOn) {
                drawCircle(JOY_C, 56f, joyBase, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(
                    JOY_C, 26f,
                    Offset(joyBase.x + game.engine.joyX * joyMaxPx, joyBase.y + game.engine.joyY * joyMaxPx),
                )
            }
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
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 74.dp),
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
        // 背包按钮 + 主动技能按钮
        if (hud.phase == DungeonGame.Phase.EXPLORING) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { engineRef.useSkill() },
                    enabled = engineRef.skillId != null && engineRef.skillCd <= 0f,
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(
                        when (engineRef.skillId) {
                            "dash" -> "💨 冲刺"
                            "shield" -> "🛡 护盾"
                            "heal" -> "💚 治疗"
                            "slowtime" -> "⏳ 缓时"
                            "freeze" -> "❄️ 冰冻"
                            "meteor" -> "☄️ 陨石"
                            "chain" -> "⚡ 闪电"
                            else -> "技能"
                        } + if (engineRef.skillCd > 0f) " " + engineRef.skillCd.toInt() + "s" else "",
                        color = if (engineRef.skillCd <= 0f) Color(0xFF7EE38A) else Color(0xFF78909C),
                    )
                }
                OutlinedButton(
                    onClick = { showBag = !showBag },
                    modifier = Modifier.height(44.dp),
                ) { Text("🎒") }
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
                // 🧬 永久升级（局外成长，花费大厅同款金币）
                val perks = player.dungeonPerks
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("hp", "❤️", 15), Triple("atk", "⚔️", 2), Triple("spd", "👟", 8)).forEach { (id, icon, gain) ->
                        val n = perks[id] ?: 0
                        val cost = vm.dungeonPerkCost(id)
                        OutlinedButton(onClick = { vm.buyDungeonPerk(id) }, modifier = Modifier.height(56.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$icon+$gain Lv.$n", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                Text("$cost🪙", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD54F))
                            }
                        }
                    }
                }
                ClassDef.ALL.forEach { c ->
                    Card(
                        onClick = { game.pendingPerks = player.dungeonPerks; game.selectClass(c.id) },
                        modifier = Modifier.fillMaxWidth(0.85f).padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

        // ---------- LevelUp 三选一（读引擎候选） ----------
        if (hud.phase == DungeonGame.Phase.LEVELUP) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⬆️ 升级！选择一项强化", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                game.engine.pendingUpgrades.forEach { up ->
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

        // ---------- 技能三选一（每层结束） ----------
        if (hud.phase == DungeonGame.Phase.SKILL_SELECT) {
            Column(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✨ 选择一个主动技能", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
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
                        modifier = Modifier.fillMaxWidth(0.8f).padding(top = 10.dp),
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
    Canvas(modifier.width(120.dp).height(84.dp).background(Color(0x66000000), RoundedCornerShape(8.dp))) {
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

/** 伤害/拾取飘字（世界坐标，上浮淡出 0.7s） */
private class FloatFx(var x: Float, var y: Float, val text: String, val color: Color, val crit: Boolean) {
    var t = 0f
}

/** 击杀粒子 */
private class ParticleFx(var x: Float, var y: Float, val vx: Float, val vy: Float, var t: Float, val color: Color)

/** 闪电链段 */
private class BoltFx(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var t: Float)

/** Boss 出场冲击环 */
private class RingFx(val x: Float, val y: Float) {
    var t = 0f
}
