package com.brainquest.game.game.dungeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.RoomType
import com.brainquest.game.game.dungeon.render.EntityRenderer
import com.brainquest.game.game.dungeon.render.SpriteAnims
import com.brainquest.game.game.dungeon.render.SpriteSheet
import com.brainquest.game.game.dungeon.render.GamePalette
import com.brainquest.game.game.dungeon.render.GameUi
import com.brainquest.game.game.dungeon.render.GamePanel
import com.brainquest.game.game.dungeon.render.PanelTitle
import com.brainquest.game.game.dungeon.render.HudStatChip
import com.brainquest.game.game.dungeon.render.HudHpBar
import com.brainquest.game.game.dungeon.render.HudXpBar
import com.brainquest.game.game.dungeon.render.RoundIconButton
import com.brainquest.game.game.dungeon.render.GameActionButton
import com.brainquest.game.game.dungeon.render.SkillKey
import com.brainquest.game.game.dungeon.render.AttackKey
import com.brainquest.game.game.dungeon.render.ModeCard
import com.brainquest.game.game.dungeon.render.LobbyNavItem
import com.brainquest.game.game.dungeon.render.GameDialog
import com.brainquest.game.game.dungeon.render.ResultStat
import com.brainquest.game.game.dungeon.render.ChoicePill
import com.brainquest.game.game.dungeon.render.ToggleSwitchRow
import com.brainquest.game.game.dungeon.render.GameBackdrop
import com.brainquest.game.game.dungeon.render.GameVignette
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
    val xpFrac: Float,      // 当前等级经验进度（0..1，HUD 经验条）
    val timeSec: Int,
    val kills: Int,
    val coins: Int,
    val bossHp: Float,      // 0..1，<=0 = 无 Boss
    val bossPhase: Int,
    val skillReady: Boolean,
    val skillCd: Int,
    val streak: Int,
    val combo: Int,
    val lobbyCls: String,
    val endless: Boolean,
    val mode: String,
    val hasEvent: Boolean,
    val eventNear: Boolean,
    val eventKind: Int,
    val portalNear: Boolean,
    val hasPortal: Boolean,
    val chestNear: Boolean,
    val hasChest: Boolean,
    val shopNear: Boolean,
    val hasShop: Boolean,
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
        mutableStateOf(Hud(DungeonGame.Phase.READY, 1, 1, 0, 100, 100, 1, 0f, 0, 0, 0, 0f, 0, true, 0, 0, 0, "knight", false, "standard", false, false, 0, false, false, false, false, false, false, 0f))
    }
    var apShopOpenT by remember { mutableFloatStateOf(0f) }   // 自动驾驶开店兜底计时
    // Boss 血条 Hades 化：白色残影缓落 / 阶段切换白闪
    var bossGhost by remember { mutableFloatStateOf(1f) }
    var bossPhasePrev by remember { mutableIntStateOf(0) }
    var bossFlash by remember { mutableFloatStateOf(0f) }
    // 新手引导（一次性）：SharedPreferences 落盘，首次 EXPLORING 弹出
    val prefsEdit = remember { context.getSharedPreferences("bg_dungeon", android.content.Context.MODE_PRIVATE) }
    var showTutorial by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }   // ⚙ 地牢设置面板（批12）
    var showEvent by remember { mutableStateOf(false) }      // 🎲 事件房赌桌弹窗（批20-C）
    var showIndex by remember { mutableStateOf(false) }      // 📖 怪物图鉴（批20-D）
    var eventResult by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(hud.phase) {
        if (hud.phase == DungeonGame.Phase.EXPLORING &&
            !prefsEdit.getBoolean("dg_tutorial_shown", false)) showTutorial = true
    }
    var confirmExit by remember { mutableStateOf(false) }
    var showBag by remember { mutableStateOf(false) }
    var rerollPick by remember { mutableStateOf(false) }   // 商店重铸第二步（选槽位）；无条件 hoist（坑 21）
    var joyBase by remember { mutableStateOf(Offset.Zero) }
    var joyOn by remember { mutableStateOf(false) }
    val frame = remember { mutableIntStateOf(0) }
    val fpsAvg = remember { mutableFloatStateOf(0f) }   // FPS 滚动平均（批16）
    val lowFpsT = remember { mutableFloatStateOf(0f) }   // 低帧持续时间（批20-E 自适应降档）
    val animT = remember { mutableFloatStateOf(0f) }   // 统一动画时间源（真实 dt 累计，秒）
    val joyMaxPx = with(LocalDensity.current) { 54.dp.toPx() }   // 批13：48→54
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    // 方向/沉浸由 AppRoot 按路由统一管理（批14：页面自管方向会被新页组合后的 onDispose 覆盖——天赋页竖屏根因）
    DisposableEffect(Unit) {
        SpriteSheet.load(context.assets)     // 静态精灵（批16）
        SpriteAnims.load(context.assets)     // 帧动画（批17：⚡此钩子曾遗漏——帧动画/图鉴精灵整体静默失效）
        onDispose { DungeonBgm.stop() }   // BGM 必须随界面销毁停止（批11 体验层）
    }

    // 特效层：把引擎的帧事件转成有生命周期的持续特效（飘字/粒子/闪电；对象池复用 + 硬上限）
    val floats = remember { ArrayList<FloatFx>(32) }
    val parts = remember { ArrayList<ParticleFx>(64) }
    val bolts = remember { ArrayList<BoltFx>(8) }
    val rings = remember { ArrayList<RingFx>(4) }
    val banners = remember { ArrayList<BannerFx>(2) }
    val slashGhosts = remember { ArrayList<SlashGhost>(6) }
    val slashPrevFired = remember { booleanArrayOf(false) }   // fired 跳变检测（上一帧状态）
    val trails = remember { ArrayList<TrailFx>(8) }
    val whirls = remember { ArrayList<WhirlFx>(2) }
    val spikes = remember { ArrayList<SpikeFx>(6) }
    val rains = remember { ArrayList<RainFx>(10) }
    val frosts = remember { ArrayList<FrostFx>(8) }
    val souls = remember { ArrayList<SoulFx>(12) }
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
        if (hud.phase == DungeonGame.Phase.LEVELUP) {
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
            // FPS 滚动平均（批16 帧率量化：指数平滑，dt>0 防除零）
            if (dt > 0f) {
                val inst = 1f / dt
                fpsAvg.floatValue = if (fpsAvg.floatValue <= 0f) inst else fpsAvg.floatValue * 0.95f + inst * 0.05f
                // 帧率自适应降档（批20-E）：低于 40fps 持续 5s → 自动降一档粒子密度（不逆升级，本局生效）
                if (game.phase == DungeonGame.Phase.EXPLORING && DungeonSettings.particleDensity(context) > 0) {
                    if (fpsAvg.floatValue < 40f) {
                        lowFpsT.floatValue += dt
                        if (lowFpsT.floatValue >= 5f) {
                            lowFpsT.floatValue = 0f
                            val nv = DungeonSettings.particleDensity(context) - 1
                            DungeonSettings.setParticleDensity(context, nv)
                            vm.toast("⚡ 检测到帧率偏低，已自动降低粒子密度")
                        }
                    } else lowFpsT.floatValue = 0f
                } else lowFpsT.floatValue = 0f
            }
            animT.floatValue += dt
            // 自动驾驶开店 3 秒自动关（防无人值守挂死；真人操作不受影响）
            if (game.autopilot && hud.phase == DungeonGame.Phase.SHOP) {
                apShopOpenT += dt
                if (apShopOpenT > 3f) { game.closeShop(); apShopOpenT = 0f }
            } else apShopOpenT = 0f
            if (lvlFlash.floatValue > 0f) lvlFlash.floatValue = (lvlFlash.floatValue - dt).coerceAtLeast(0f)
            // Boss 血条残影缓落（白色残影显示刚掉的血）+ 阶段切换白闪
            if (hud.bossHp <= 0f) { bossGhost = 1f; bossPhasePrev = 0 } else {
                if (hud.bossHp > bossGhost) bossGhost = hud.bossHp   // 新 Boss 直接贴上
                else bossGhost = (bossGhost - dt * 0.35f).coerceAtLeast(hud.bossHp)
                if (hud.bossPhase > bossPhasePrev && bossPhasePrev > 0) bossFlash = 0.3f
                bossPhasePrev = hud.bossPhase
            }
            if (bossFlash > 0f) bossFlash = (bossFlash - dt).coerceAtLeast(0f)
            // BGM 三态（批11 体验层）：Boss 在场=boss / 锁门战斗=combat / 其余=explore / 非局内=off
            val bgmState: Pair<String, Int?> = when {
                hud.phase == DungeonGame.Phase.GAMEOVER || hud.phase == DungeonGame.Phase.VICTORY ||
                    hud.phase == DungeonGame.Phase.READY || hud.phase == DungeonGame.Phase.CLASS_SELECT -> "off" to null
                hud.bossHp > 0f -> "boss" to R.raw.dg_bgm_boss
                game.combatActive -> "combat" to R.raw.dg_bgm_combat
                else -> "explore" to R.raw.dg_bgm_explore
            }
            DungeonBgm.update(context, player.soundOn, bgmState.first, bgmState.second, DungeonSettings.bgmVolume(context) / 100f)   // BGM 音量设置（批12）
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
                        if (floats.size < 100 && DungeonSettings.damageNumbers(context)) {   // 伤害数字设置（批12）
                            floats.add(obtainFloat().also { it.set(ev.x, ev.y, ev.text, c, ev.crit) })
                        }
                        // 攻击来向（hitEnemy 经 x2/y2 传入）：火花沿来向锥形喷溅，更有「被这一刀打中」的方向感
                        val dirBase = if (ev.x2 != 0f || ev.y2 != 0f)
                            kotlin.math.atan2(ev.y2 - ev.y, ev.x2 - ev.x) else null
                        if (parts.size < DungeonSettings.particleCap(context)) when (ev.element) {
                            com.brainquest.game.game.core.Element.FIRE -> repeat(5) {   // 火：余焰上飘重力回落
                                val spread = if (dirBase != null) dirBase + (rngFx.nextFloat() - 0.5f) * 1.2f else rngFx.nextFloat() * 6.283f
                                val fsp = 50f + rngFx.nextFloat() * 90f
                                parts.add(obtainPart().also {
                                    it.set(ev.x, ev.y, kotlin.math.cos(spread) * fsp, kotlin.math.sin(spread) * fsp - 40f, 0.55f, GamePalette.ELEM_FIRE)
                                    it.g = 260f; it.drag = 1.6f
                                })
                            }
                            com.brainquest.game.game.core.Element.ICE -> repeat(6) {   // 冰：冰晶碎裂四散强阻尼
                                val ang = rngFx.nextFloat() * 6.283f
                                val sp = 120f + rngFx.nextFloat() * 140f
                                parts.add(obtainPart().also {
                                    it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.45f, GamePalette.ELEM_ICE)
                                    it.shape = 1f; it.rot = rngFx.nextFloat() * 360f; it.vr = (rngFx.nextFloat() - 0.5f) * 720f; it.drag = 3.2f
                                })
                            }
                            com.brainquest.game.game.core.Element.THUNDER -> if (bolts.size < 12) repeat(2) {   // 雷：短电弧段
                                bolts.add(BoltFx(ev.x, ev.y, ev.x + (rngFx.nextFloat() - 0.5f) * 140f, ev.y + (rngFx.nextFloat() - 0.5f) * 140f, 0.15f))
                            }
                            else -> repeat(4) {   // 物理：白火花（沿攻击来向锥形喷溅）
                                val ang = if (dirBase != null) dirBase + (rngFx.nextFloat() - 0.5f) * 1.2f
                                          else rngFx.nextFloat() * 6.283f
                                val sp = 60f + rngFx.nextFloat() * 120f
                                parts.add(obtainPart().also {
                                    it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.3f, c)
                                })
                            }
                        }
                        if (ev.crit) DungeonSfx.play(context, player.soundOn, R.raw.dg_crit, 0.65f, 120L + rngFx.nextInt(60))
                        else DungeonSfx.play(context, player.soundOn, R.raw.dg_hit, 0.55f, 70L + rngFx.nextInt(50))
                    }
                    1 -> {   // 死亡碎片化：三角碎片旋转飞散（按敌人主色，带重力）+ 灵魂光点飞向玩家
                        val pc = if (ev.tint != 0) Color(ev.tint) else GamePalette.UI_HP
                        if (parts.size < DungeonSettings.particleCap(context)) repeat(9) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 80f + rngFx.nextFloat() * 150f
                            parts.add(obtainPart().also {
                                it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp - 30f, 0.55f, pc)
                                it.shape = 1f; it.rot = rngFx.nextFloat() * 360f; it.vr = (rngFx.nextFloat() - 0.5f) * 640f
                                it.g = 300f; it.drag = 1.2f
                            })
                        }
                        if (souls.size < 12) repeat(2) {
                            souls.add(SoulFx(ev.x + (rngFx.nextFloat() - 0.5f) * 24f, ev.y + (rngFx.nextFloat() - 0.5f) * 24f))
                        }
                    }
                    2 -> { if (floats.size < 100) floats.add(obtainFloat().also { it.set(ev.x, ev.y, ev.text, GamePalette.UI_ORB, false) }); DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.4f, 120) }
                    3 -> bolts.add(BoltFx(ev.x, ev.y, ev.x2, ev.y2, 0.15f))
                    4 -> { rings.add(RingFx(ev.x, ev.y, GamePalette.BOSS_GLOW, 220f, 0.6f)); DungeonSfx.play(context, player.soundOn, R.raw.dg_boss, 0.8f, 1500) }
                    8 -> rings.add(RingFx(ev.x, ev.y, Color(0xB0ECEFF1), 70f, 0.22f))
                    9 -> if (parts.size < DungeonSettings.particleCap(context)) {
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
                    7 -> if (banners.size < 2) banners.add(BannerFx(ev.text))   // Boss 出场横幅
                    10 -> {   // 宝箱开启：金色冲击环 + 开箱音
                        rings.add(RingFx(ev.x, ev.y, GamePalette.UI_GOLD, 130f, 0.5f))
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_levelup, 0.55f, 400)
                    }
                    11 -> rings.add(RingFx(ev.x, ev.y, Color(0xB0FFFFFF), 150f, 0.35f))   // 技能施放白环
                    12 -> {   // 旋风斩：双弧对转 + 环形风压粒子 + 金环收尾（三段式：白环闪→过程→残留）
                        if (whirls.size < 3) whirls.add(WhirlFx(ev.x, ev.y))
                        if (rings.size < 8) rings.add(RingFx(ev.x, ev.y, GamePalette.UI_GOLD, 120f, 0.6f))
                        if (parts.size < DungeonSettings.particleCap(context)) repeat(12) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 90f + rngFx.nextFloat() * 110f
                            parts.add(obtainPart().also {
                                it.set(ev.x + kotlin.math.cos(ang) * 30f, ev.y + kotlin.math.sin(ang) * 30f,
                                    kotlin.math.cos(ang + 1.57f) * sp, kotlin.math.sin(ang + 1.57f) * sp - 20f, 0.5f, Color(0xFFE0E0E0))
                                it.g = 60f; it.drag = 2.4f
                            })
                        }
                    }
                    13 -> if (spikes.size < 8) {   // 暴风雪：冰锥下落 + 落地冰晶迸溅（延迟粒子）+ 地面霜圈残留
                        spikes.add(SpikeFx(ev.x, ev.y))
                        if (frosts.size < 8) frosts.add(FrostFx(ev.x, ev.y))
                        if (parts.size < DungeonSettings.particleCap(context)) repeat(6) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 90f + rngFx.nextFloat() * 130f
                            parts.add(obtainPart().also {
                                it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp - 40f, 0.45f, GamePalette.ELEM_ICE)
                                it.t = -0.18f   // 延迟 0.18s（冰锥落地时刻才迸溅）
                                it.shape = 1f; it.rot = rngFx.nextFloat() * 360f; it.vr = (rngFx.nextFloat() - 0.5f) * 720f
                                it.g = 260f; it.drag = 2.6f
                            })
                        }
                    }
                    14 -> repeat(6) {   // 箭雨：雨线粒子束加倍（沿面向扇形下落，更高更快）
                        if (rains.size < 28) {
                            val a = game.engine.facing + (rngFx.nextFloat() - 0.5f) * 1.0f
                            rains.add(RainFx(ev.x + kotlin.math.cos(a) * (20f + rngFx.nextFloat() * 70f),
                                ev.y + kotlin.math.sin(a) * (20f + rngFx.nextFloat() * 70f) - 160f,
                                kotlin.math.cos(a) * 180f, kotlin.math.sin(a) * 180f + 340f))
                        }
                    }
                    15 -> DungeonSfx.play(context, player.soundOn, R.raw.dg_whoosh, 0.35f, 100L + rngFx.nextInt(40))   // 空挥 whoosh
                    19 -> {   // 开门碎裂（批20-A）：屏障碎块四散
                        if (parts.size < DungeonSettings.particleCap(context)) repeat(10) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 90f + rngFx.nextFloat() * 150f
                            parts.add(obtainPart().also {
                                it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.5f,
                                    if (rngFx.nextBoolean()) Color(0xFFE15A5A) else Color(0xFFBCAAA4))
                                it.shape = 1f; it.rot = rngFx.nextFloat() * 360f; it.vr = (rngFx.nextFloat() - 0.5f) * 720f
                                it.g = 340f; it.drag = 1.6f
                            })
                        }
                    }
                    16 -> {   // 清房开门：金环 + 金字 + 拾取音（Hades 式段落反馈）
                        rings.add(RingFx(ev.x, ev.y, GamePalette.UI_GOLD, 140f, 0.5f))
                        if (floats.size < 100) floats.add(obtainFloat().also { it.set(ev.x, ev.y, ev.text, GamePalette.UI_GOLD, true) })
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.5f, 500)
                    }
                    17 -> {   // 觉醒爆发：巨型双环 + 金字 + 升级音（白闪由 lvlFlash 承担）
                        rings.add(RingFx(ev.x, ev.y, GamePalette.UI_GOLD, 500f, 0.7f))
                        rings.add(RingFx(ev.x, ev.y, Color.White, 320f, 0.5f))
                        if (floats.size < 100) floats.add(obtainFloat().also { it.set(ev.x, ev.y - 50f, ev.text, GamePalette.UI_GOLD, true) })
                        lvlFlash.floatValue = 0.45f
                    }
                    18 -> {   // 爆裂连锁：火环 + 火花
                        rings.add(RingFx(ev.x, ev.y, GamePalette.ELEM_FIRE, 90f, 0.35f))
                        if (parts.size < DungeonSettings.particleCap(context)) repeat(4) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 70f + rngFx.nextFloat() * 110f
                            parts.add(obtainPart().also {
                                it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.35f, GamePalette.ELEM_FIRE)
                                it.g = 160f
                            })
                        }
                    }
                }
            }
            game.engine.events.clear()
            // 特效寿命推进（到期回收进池）
            floats.forEach { it.t += dtFx }
            floats.removeAll { if (it.t > 0.7f) { if (floatPool.size < 120) floatPool.addLast(it); true } else false }
            // delay 粒子（t<0）：只计时不动不画（暴风雪冰锥落地迸溅用）
            parts.forEach { it.t += dtFx; if (it.t >= 0f) { it.x += it.vx * dtFx; it.y += it.vy * dtFx } }
            parts.removeAll { if (it.t > it.life) { if (partPool.size < 320) partPool.addLast(it); true } else false }
            frosts.forEach { it.t += dtFx }
            frosts.removeAll { it.t > 1.2f }
            // 灵魂光点：每帧朝玩家追踪加速（吸血鬼幸存者式击杀反馈，纯视觉）
            souls.forEach { s ->
                s.t += dtFx
                val tx = game.engine.px - s.x; val ty = game.engine.py - s.y
                val dl = kotlin.math.hypot(tx, ty).coerceAtLeast(1f)
                val sp = 260f + s.t * 900f
                s.x += tx / dl * sp * dtFx; s.y += ty / dl * sp * dtFx
            }
            souls.removeAll { it.t > 0.45f }
            bolts.forEach { it.t += dtFx }
            bolts.removeAll { it.t > 0.15f }
            rings.forEach { it.t += dtFx }
            rings.removeAll { it.t > it.life }
            trails.forEach { it.t += dtFx }
            trails.removeAll { it.t > 0.3f }
            whirls.forEach { it.t += dtFx }
            whirls.removeAll { it.t > 0.6f }
            spikes.forEach { it.t += dtFx }
            spikes.removeAll { it.t > 0.18f }
            rains.forEach { it.t += dtFx; it.x += it.dx * dtFx; it.y += it.dy * dtFx }
            rains.removeAll { it.t > 0.5f }
            banners.forEach { it.t += dtFx }
            banners.removeAll { it.t > 2.4f }
            slashGhosts.forEach { it.t += dtFx }
            slashGhosts.removeAll { it.t > 0.22f }
            val e = game.engine
            val boss = game.engine.enemies.firstOrNull { it.bossFloor > 0 && it.alive && !it.dormant }   // 待机的预刷新 Boss 不顶血条
            val h = Hud(
                game.phase, game.floor, game.floorCleared, game.rooms.size,
                e.hp, e.maxHp, e.level, e.xp.toFloat() / e.xpNext.coerceAtLeast(1), game.runTimeSec, game.totalKills,
                game.coins,
                if (boss != null) boss.hp / boss.maxHp else 0f,
                boss?.phase ?: 0,
                e.skillCd <= 0f, e.skillCd.toInt(),
                e.killStreak,
                e.hitCombo,
                game.lobbyClassId,
                game.endless,
                game.runMode,
                game.eventSpot != null,
                game.eventNear,
                game.eventKind,
                game.portalNear,
                game.portal != null,
                game.chestNear,
                game.chest != null,
                game.shopNear,
                game.shop != null,
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
                DungeonGame.Phase.SHOP -> DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.6f, 0)
                DungeonGame.Phase.VICTORY -> DungeonSfx.play(context, player.soundOn, R.raw.dg_victory, 0.85f, 0)
                DungeonGame.Phase.GAMEOVER -> DungeonSfx.play(context, player.soundOn, R.raw.dg_lose, 0.8f, 0)
                else -> {}
            }
        if ((hud.phase == DungeonGame.Phase.GAMEOVER || hud.phase == DungeonGame.Phase.VICTORY) && !rewarded) {
            if (game.runMode == "arena") vm.reportBest("arena_wave", hud.floor)   // 角斗场最佳波数（批15）
            vm.mergeDungeonSeen(game.seenMonsters.toList())   // 图鉴合并（批20-D）
            rewarded = true
            vm.addDungeonResult(hud.floor, hud.kills, hud.timeSec, game.coins, hud.phase == DungeonGame.Phase.VICTORY)
        }
    }

    BackHandler(enabled = hud.phase != DungeonGame.Phase.READY) {
        if (hud.phase == DungeonGame.Phase.SHOP) {
            game.closeShop()   // 商店页返回=关商店，不弹退出确认
        } else {
            if (hud.phase == DungeonGame.Phase.EXPLORING) game.pause()
            confirmExit = true
        }
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
                            // 触控范围 = 可视底座圈（白圈即可移动操作区）
                            val base = Offset(86.dp.toPx(), size.height - 86.dp.toPx())
                            val r = 96.dp.toPx()   // 批13：不可见大热区（可视底座仍 58dp），降误触
                            val ddx = off.x - base.x; val ddy = off.y - base.y
                            if (ddx * ddx + ddy * ddy <= r * r) {
                                joyOn = true
                                joyBase = base
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
            val shk = game.engine.shake * DungeonSettings.shakeFactor(context)   // 震屏强度设置（批12）
            val shx = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            val shy = if (shk > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * shk * 2f else 0f
            withTransform({
                translate(shx, shy)
                val pz = if (DungeonSettings.critZoom(context)) 1f + game.engine.punchZoom else 1f   // 暴击 punch-zoom（Dead Cells）：只影响世界层，HUD 在变换外（坑35）；设置可关（批12）
                scale(game.camZoom * DungeonGame.BASE_ZOOM * pz, game.camZoom * DungeonGame.BASE_ZOOM * pz, pivot = Offset(size.width / 2f, size.height / 2f))
            }) {
            EntityRenderer.drawWorld(this, game, t)
            EntityRenderer.drawFixtures(this, game, t)
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
            EntityRenderer.drawCoins(this, game, t)
            val spritesOn = DungeonSettings.useSprites(context)
            for (e in game.engine.enemies) EntityRenderer.drawEnemy(this, game, e, t, spritesOn)
            EntityRenderer.drawPlayer(this, game, t, DungeonSettings.useSprites(context))
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
                if (pt.t < 0f) continue   // 延迟粒子：尚未激活
                val a = (1f - pt.t / pt.life.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                val po = Offset(pt.x - game.camX, pt.y - game.camY)
                if (pt.shape >= 1f) {
                    // 三角碎片（旋转飞散；批16 零分配：三线段拼三角，不 new Path）
                    val sz = 7f
                    val ca = kotlin.math.cos(pt.rot); val sa = kotlin.math.sin(pt.rot)
                    fun tp(ax: Float, ay: Float) = Offset(po.x + ax * ca - ay * sa, po.y + ax * sa + ay * ca)
                    val p1 = tp(0f, -sz); val p2 = tp(sz * 0.9f, sz * 0.7f); val p3 = tp(-sz * 0.9f, sz * 0.7f)
                    val col = pt.color.copy(alpha = a)
                    drawLine(col, p1, p2, 3f); drawLine(col, p2, p3, 3f); drawLine(col, p3, p1, 3f)
                } else {
                    drawCircle(pt.color.copy(alpha = a), 3.5f, po)
                }
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
            for (wh in whirls) {   // 旋风斩：双弧对转（顺/逆时针各一道，0.6s，透明度先扬后抑）
                val k = (wh.t / 0.6f).coerceIn(0f, 1f)
                val a = (if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f)
                val rot = wh.t * 900f   // 每秒 2.5 圈
                val r = 70f + k * 80f
                val arcTL = Offset(wh.x - game.camX - r, wh.y - game.camY - r)
                for (s in 0..1) {
                    drawArc(
                        Color(0xFFE0E0E0).copy(alpha = 0.75f * a),
                        startAngle = rot * if (s == 0) 1f else -1f, sweepAngle = 150f, useCenter = false,
                        topLeft = arcTL, size = Size(r * 2f, r * 2f), style = Stroke(5f * (1f - k) + 1.5f),
                    )
                }
            }
            for (sp in spikes) {   // 暴风雪：冰锥从上方 140px 落至地面（0.18s，三角，淡入）
                val k = (sp.t / 0.18f).coerceIn(0f, 1f)
                val sy = sp.y - 140f * (1f - k)
                val a = (k * 4f).coerceIn(0f, 1f) * (1f - (k - 0.75f).coerceAtLeast(0f) * 4f * 0.5f)
                val po = Offset(sp.x - game.camX, sy - game.camY)
                val t1 = Offset(po.x, po.y + 10f); val t2 = Offset(po.x - 5f, po.y - 6f); val t3 = Offset(po.x + 5f, po.y - 6f)
                drawLine(GamePalette.ELEM_ICE.copy(alpha = a), t1, t2, 4f)
                drawLine(GamePalette.ELEM_ICE.copy(alpha = a), t1, t3, 4f)
                drawLine(GamePalette.ELEM_ICE.copy(alpha = a), t2, t3, 3f)
                drawLine(Color.White.copy(alpha = a * 0.7f), t2, t3, 1.5f)
            }
            for (fr in frosts) {   // 暴风雪地面霜圈：淡入 0.2s → 保持 → 淡出 0.4s（残留 1.2s）
                val k = (fr.t / 1.2f).coerceIn(0f, 1f)
                val aIn = (fr.t / 0.2f).coerceIn(0f, 1f)
                val aOut = ((1.2f - fr.t) / 0.4f).coerceIn(0f, 1f)
                val fo = Offset(fr.x - game.camX, fr.y - game.camY)
                drawOval(
                    GamePalette.ELEM_ICE.copy(alpha = 0.28f * aIn * aOut),
                    topLeft = Offset(fo.x - 44f, fo.y - 18f), size = Size(88f, 36f),
                )
                drawOval(
                    Color.White.copy(alpha = 0.20f * aIn * aOut),
                    topLeft = Offset(fo.x - 30f, fo.y - 12f), size = Size(60f, 24f),
                )
            }
            for (rn in rains) {   // 箭雨：雨线（沿速度方向的短线段，淡出）
                val k = (rn.t / 0.5f).coerceIn(0f, 1f)
                val a = (1f - k) * 0.8f
                val ln = 18f
                val nl = kotlin.math.hypot(rn.dx, rn.dy).coerceAtLeast(1f)
                val ux = rn.dx / nl * ln; val uy = rn.dy / nl * ln
                drawLine(
                    Color(0xFFCFD8DC).copy(alpha = a),
                    Offset(rn.x - game.camX, rn.y - game.camY),
                    Offset(rn.x - game.camX + ux, rn.y - game.camY + uy),
                    2f,
                )
            }
            for (sl in souls) {   // 灵魂光点：青白发光小点飞向玩家
                val a = (1f - sl.t / 0.45f).coerceIn(0f, 1f)
                val so = Offset(sl.x - game.camX, sl.y - game.camY)
                drawCircle(Color(0xFF80DEEA).copy(alpha = 0.5f * a), 6f, so)
                drawCircle(Color.White.copy(alpha = 0.9f * a), 2.5f, so)
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
                if (ft.crit) {
                    // 暴击数字弹跳：1.5 倍平滑弹回 1.0
                    val k2 = ft.t / 0.7f
                    val pop = 1.5f - 0.5f * k2 * k2
                    withTransform({ translate(tx, ty); scale(pop, pop, pivot = Offset.Zero); translate(-tx, -ty) }) {
                        drawText(layout, color = ft.color.copy(alpha = a), topLeft = Offset(tx, ty))
                    }
                } else {
                    drawText(layout, color = ft.color.copy(alpha = a), topLeft = Offset(tx, ty))
                }
            }
            // 挥砍轨迹：前摇淡显 → 挥出扇形渐扫 → 后摇淡出（由引擎 activeSlash 驱动）
            val en = game.engine
            // B2 残影：fired false→true 跳变 → 留一道 0.22s 渐隐弧
            val curFired = en.activeSlash?.fired ?: false
            if (curFired && !slashPrevFired[0] && en.activeSlash != null) {
                if (slashGhosts.size < 6) slashGhosts.add(SlashGhost(en.facing, en.activeSlash!!.range, en.activeSlash!!.flip))
            }
            slashPrevFired[0] = curFired
            for (gh in slashGhosts) {   // 残影弧（先画，被当前弧覆盖）
                val ga = ((1f - gh.t / 0.22f) * 0.30f).coerceIn(0f, 0.30f)
                val grr = gh.range
                val gdir = if (gh.flip) -1f else 1f
                drawArc(
                    Color(0x66FFFFFF).copy(alpha = ga),
                    startAngle = Math.toDegrees(gh.facing.toDouble()).toFloat() - 50f * gdir,
                    sweepAngle = 100f * gdir,
                    useCenter = false,
                    topLeft = Offset(en.px - game.camX - grr, en.py - game.camY - grr),
                    size = Size(grr * 2f, grr * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(9f),
                )
            }
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
                val sweepMag = if (inSwing) 100f * ((s.timer - s.windup) / 0.15f).coerceIn(0f, 1f)
                            else if (after || s.fired) 100f else 0f
                val sweep = sweepMag * (if (s.flip) -1f else 1f)
                if (alpha > 0.02f && sweep > 1f) {
                    val rr = s.range
                    val tl = Offset(psx - rr, psy - rr)
                    val sz = Size(rr * 2f, rr * 2f)
                    val dirSign = if (s.flip) -1f else 1f   // 正反手交替：反手镜像弧光
                    val startDeg = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f * dirSign
                    // 扇形微光：sweep 渐变（沿挥扫方向由透明到亮，替代死白填充）
                    drawArc(
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            0f to Color(0x00FFFFFF), 0.5f to Color(0x55FFFFFF).copy(alpha = alpha), 1f to Color(0x00FFFFFF),
                            center = Offset(psx, psy),
                        ),
                        startAngle = startDeg,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = tl, size = sz,
                    )
                    // 亮弧边：外亮内淡双描边
                    drawArc(
                        Color(0xAAFFFFFF).copy(alpha = alpha),
                        startAngle = startDeg, sweepAngle = sweep, useCenter = false,
                        topLeft = tl, size = sz,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(7f),
                    )
                    drawArc(
                        Color(0x44FFFFFF).copy(alpha = alpha),
                        startAngle = startDeg, sweepAngle = sweep, useCenter = false,
                        topLeft = tl, size = sz,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(13f),
                    )
                    // 风压粒子：挥出瞬间沿弧前缘甩出（本地粒子池）
                    if (inSwing && parts.size < 300 && rngFx.nextFloat() < 0.9f) {
                        val mid = startDeg + sweep
                        val rad = Math.toRadians(mid.toDouble()).toFloat()
                        parts.add((partPool.removeFirstOrNull() ?: ParticleFx()).also {   // Canvas 作用域直用对象池（批16）
                            it.set(en.px + kotlin.math.cos(rad) * rr * 0.8f, en.py + kotlin.math.sin(rad) * rr * 0.8f,
                                   kotlin.math.cos(rad) * 220f, kotlin.math.sin(rad) * 220f, 0.22f, Color(0xCCFFFFFF))
                            it.drag = 4f
                        })
                    }
                }
            }
            }   // withTransform（震屏+缩放）——数值条/暗角在屏幕层
            // 批24：玩家血条/经验条已并入顶部 Compose 玩家面板，旧 Canvas 数值条退场（避免双份 HUD）
            vignetteHolder[0]?.let { drawRect(it) }
            if (game.engine.timeScale < 1f) drawRect(Color(0x14264CCF))   // 缓时滤镜
            if (lvlFlash.floatValue > 0f) drawRect(Color.White.copy(alpha = lvlFlash.floatValue.coerceAtMost(0.5f)))   // 升级白光
            if (banners.isNotEmpty()) {   // Boss 出场横幅（屏幕层，Hades 式）+ 暗角脉冲
                val b = banners[0]
                val k = (b.t / 2.4f).coerceIn(0f, 1f)
                val a = ((b.t / 0.25f).coerceIn(0f, 1f)) * (((2.4f - b.t) / 0.4f).coerceIn(0f, 1f))
                drawRect(Color(0x50E15A5A).copy(alpha = 0.4f * a * (1f - k * 0.6f)))   // 暗角脉冲（全屏红晕）
                val midY = size.height * 0.40f
                drawRect(Color(0xB0120A0A).copy(alpha = a), Offset(0f, midY - 46f), Size(size.width, 92f))
                drawLine(GamePalette.UI_GOLD.copy(alpha = a), Offset(0f, midY - 46f), Offset(size.width, midY - 46f), 2f)
                drawLine(GamePalette.UI_GOLD.copy(alpha = a), Offset(0f, midY + 46f), Offset(size.width, midY + 46f), 2f)
                val bl = textCache.getOrPut("banner#" + b.text) {   // 批16：横幅 2.4s 期间不再每帧 measure
                    textMeasurer.measure(b.text, androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold))
                }
                drawText(bl, color = GamePalette.UI_GOLD.copy(alpha = a), topLeft = Offset((size.width - bl.size.width) / 2f, midY - bl.size.height / 2f))
            }
            // 虚拟摇杆（批24：暗底 + 金环 + 四向刻度，与整体 HUD 语言统一）
            val jb = Offset(86.dp.toPx(), size.height - 86.dp.toPx())
            val jr = 58.dp.toPx()
            drawCircle(Color(0x7A070A12), jr, jb)
            drawCircle(Color(0x12FFFFFF), jr, jb)
            drawCircle(GamePalette.UI_GOLD.copy(alpha = 0.30f), jr, jb, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawCircle(JOY_C.copy(alpha = 0.35f), jr - 9f, jb, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            for (i in 0 until 12) {   // 外圈刻度环
                val a = i * 0.5236f
                val c1 = Offset(jb.x + kotlin.math.cos(a) * (jr + 7f), jb.y + kotlin.math.sin(a) * (jr + 7f))
                val c2 = Offset(jb.x + kotlin.math.cos(a) * (jr + 13f), jb.y + kotlin.math.sin(a) * (jr + 13f))
                drawLine(JOY_C.copy(alpha = 0.5f), c1, c2, 2.5f)
            }
            // 四向小箭头：操作感更明确
            for (d in 0..3) {
                val a = d * 1.5708f - 1.5708f
                val tip = Offset(jb.x + kotlin.math.cos(a) * (jr - 16f), jb.y + kotlin.math.sin(a) * (jr - 16f))
                val l = Offset(jb.x + kotlin.math.cos(a - 0.16f) * (jr - 25f), jb.y + kotlin.math.sin(a - 0.16f) * (jr - 25f))
                val r = Offset(jb.x + kotlin.math.cos(a + 0.16f) * (jr - 25f), jb.y + kotlin.math.sin(a + 0.16f) * (jr - 25f))
                drawLine(JOY_C.copy(alpha = 0.45f), l, tip, 2f)
                drawLine(JOY_C.copy(alpha = 0.45f), r, tip, 2f)
            }
            val knob = if (joyOn) Offset(jb.x + game.engine.joyX * joyMaxPx, jb.y + game.engine.joyY * joyMaxPx) else jb
            drawCircle(Color(0x66000000), 34f, Offset(knob.x, knob.y + 3f))
            drawCircle(Color(0xD9DDE6F2), 30f, knob)
            drawCircle(Color.White.copy(alpha = 0.85f), 22f, knob)
            drawCircle(GamePalette.UI_GOLD.copy(alpha = 0.5f), 11f, knob, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }

        // ---------- HUD（横屏：左上玩家面板 / 顶部目标芯片 / 右上资源与暂停 / 右下操作簇） ----------
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // 左上：等级徽章 + 分段血条 + 经验条（王者式玩家信息簇）
                GamePanel(
                    modifier = Modifier.width(216.dp),
                    accent = if (hud.hp.toFloat() / hud.maxHp.coerceAtLeast(1) < 0.3f) GameUi.Hp else null,
                    contentPadding = 9.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0xFF2C3A56), Color(0xFF121826)),
                                    ),
                                    androidx.compose.foundation.shape.CircleShape,
                                )
                                .border(1.5.dp, GamePalette.UI_GOLD.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Lv.${hud.level}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GamePalette.UI_GOLD,
                            )
                        }
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("生命", fontSize = 10.sp, color = GameUi.TextDim)
                                Text("${hud.hp} / ${hud.maxHp}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            HudHpBar(
                                frac = hud.hp.toFloat() / hud.maxHp.coerceAtLeast(1),
                                text = "",
                                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                                height = 13.dp,
                            )
                        }
                    }
                    HudXpBar(hud.xpFrac, Modifier.fillMaxWidth().padding(top = 5.dp), height = 4.dp)
                }
                Spacer(Modifier.width(10.dp))
                // 顶部中央：层数 / 房间进度 / 计时，三枚独立芯片
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudStatChip("🏰", "第${hud.floor}层" + if (hud.endless) " · 无尽" else "")
                    HudStatChip("🚪", "${hud.cleared}/${hud.roomsTotal}", tint = GamePalette.UI_EXP)
                    HudStatChip("⏱", formatTime(hud.timeSec), tint = GameUi.Cyan)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x8C0B1018))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.brainquest.game.ui.CoinIcon(14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("${hud.coins}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)
                    }
                    RoundIconButton("Ⅱ", onClick = { game.pause() }, size = 42.dp, enabled = hud.phase == DungeonGame.Phase.EXPLORING)
                }
            }
        }   // HUD 阶段门控（READY/CLASS_SELECT 不显示）
        // 六槽装备栏（品质色边框 + 槽位图标；空槽暗显）：收纳进小组件面板，不再裸放在画面
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            GamePanel(
                modifier = Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, top = 92.dp),
                contentPadding = 6.dp,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Equipment.Slot.entries.forEach { slot ->
                        val it = game.slots[slot]
                        val rarity = it?.let { c2 -> Color(c2.rarityColorLong) } ?: Color(0x33FFFFFF)
                        Box(
                            Modifier.size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        if (it != null) listOf(rarity.copy(alpha = 0.45f), Color(0xFF0E131D))
                                        else listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)),
                                    ),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(1.2.dp, if (it != null) rarity else Color(0x26FFFFFF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (it != null) slotEmoji(slot) else "·",
                                fontSize = 14.sp,
                                color = if (it != null) Color.White else GameUi.TextFaint,
                            )
                        }
                    }
                }
            }
        }
        // 右下操作簇（王者式弧形布局）：背包 → 觉醒爆发 → 职业技能 → 攻击；交互提示浮在簇上方
        if (hud.phase == DungeonGame.Phase.EXPLORING) {
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(end = 18.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // 靠近可交互物：浮出金色操作提示（不占用攻击键，攻击/交互可同时保留）
                val interact: Triple<String, String, () -> Unit>? = when {
                    hud.hasChest && hud.chestNear -> Triple("🎁", "开启宝箱", {
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.6f, 200)
                        game.openChest()
                    })
                    hud.hasShop && hud.shopNear -> Triple("🏪", "浏览商店", {
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_pickup, 0.6f, 200)
                        game.openShop()
                    })
                    hud.hasPortal && hud.portalNear -> Triple("🌀", "进入传送门", { game.enterPortal() })
                    hud.hasEvent && hud.eventNear -> Triple("🎲", "神秘赌桌", {
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_skill, 0.6f, 500)
                        showEvent = true
                    })
                    else -> null
                }
                if (interact != null) {
                    Row(
                        Modifier.padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0xFFFFE082), Color(0xFFFFC107)),
                                ),
                            )
                            .border(1.5.dp, Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                                indication = null,
                                onClick = { interact.third() },
                            )
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(interact.first, fontSize = 16.sp)
                        Text(
                            interact.second,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF231A00),
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 背包
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RoundIconButton(
                            "🎒",
                            onClick = {
                                showBag = !showBag
                                if (showBag && hud.phase == DungeonGame.Phase.EXPLORING) game.pause()
                                else if (hud.phase == DungeonGame.Phase.PAUSED && !showEvent) game.resume()
                            },
                            size = 44.dp,
                        )
                        Text("背包", fontSize = 9.sp, color = GameUi.TextDim)
                    }
                    // 觉醒爆发：能量环进度，满时金环呼吸
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkillKey(
                            icon = "💥",
                            onClick = {
                                if (engineRef.useBurst()) {
                                    DungeonSfx.play(context, player.soundOn, R.raw.dg_boss, 0.7f, 400)
                                }
                            },
                            size = 54.dp,
                            cdFrac = 1f - (engineRef.burst / 100f).coerceIn(0f, 1f),
                            ready = engineRef.burstReady,
                            ringColor = GamePalette.UI_GOLD,
                            caption = if (engineRef.burstReady) "就绪" else "${engineRef.burst.toInt()}%",
                        )
                        Text("爆发", fontSize = 9.sp, color = GameUi.TextDim)
                    }
                    // 职业技能：冷却扇形
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkillKey(
                            icon = when (engineRef.skillId) {
                                "dash" -> "💨"; "shield" -> "🛡"; "heal" -> "💚"; "slowtime" -> "⏳"
                                "freeze" -> "❄️"; "meteor" -> "☄️"; "chain" -> "⚡"
                                "whirlwind" -> "🌀"; "blizzard" -> "🌨"; "arrowrain" -> "🏹"
                                else -> "✨"
                            },
                            onClick = {
                                if (engineRef.skillId != null && engineRef.skillCd <= 0f) {
                                    DungeonSfx.play(context, player.soundOn, R.raw.dg_skill, 0.6f, 300)
                                    engineRef.useSkill()
                                }
                            },
                            size = 64.dp,
                            cdFrac = if (engineRef.skillCdMax > 0f)
                                (engineRef.skillCd / engineRef.skillCdMax).coerceIn(0f, 1f) else 0f,
                            ready = engineRef.skillCd <= 0f,
                            ringColor = GamePalette.UI_EXP,
                            caption = if (engineRef.skillCd > 0f) "${engineRef.skillCd.toInt()}s" else null,
                        )
                        Text("技能", fontSize = 9.sp, color = GameUi.TextDim)
                    }
                    // 普攻：大圆按住连击
                    AttackKey(
                        icon = "🗡",
                        label = "攻击",
                        progress = if (engineRef.attackInterval > 0f)
                            (engineRef.attackTimer / engineRef.attackInterval).coerceIn(0f, 1f) else 0f,
                        onPress = {
                            attackPressed = true
                            engineRef.attackHeld = true
                        },
                        onRelease = {
                            attackPressed = false
                            engineRef.attackHeld = false
                        },
                        size = 88.dp,
                    )
                }
            }
        }
        if (showBag) {
            GameDialog(
                title = "🎒 装备与词条",
                accent = GameUi.PrimaryLight,
                onDismiss = {
                    showBag = false
                    if (hud.phase == DungeonGame.Phase.PAUSED && !showEvent) game.resume()
                },
                width = 600.dp,
            ) {
                // 两列 × 三行：横屏下一屏放得下，不再纵向溢出
                Equipment.Slot.entries.chunked(2).forEach { rowSlots ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowSlots.forEach { slot ->
                            val it = game.slots[slot]
                            val rColor = it?.let { c2 -> Color(c2.rarityColorLong) } ?: Color(0x33FFFFFF)
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33000000))
                                    .border(1.dp, if (it != null) rColor.copy(alpha = 0.5f) else GameUi.Border, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                                            .background(if (it != null) rColor.copy(alpha = 0.22f) else Color(0x14FFFFFF)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(slotEmoji(slot), fontSize = 13.sp) }
                                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                        Text(
                                            it?.name ?: "空 · ${slot.label}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (it != null) FontWeight.Bold else FontWeight.Normal,
                                            color = if (it != null) Color.White else GameUi.TextFaint,
                                            maxLines = 1,
                                        )
                                        Text(
                                            if (it != null) "${it.rarity.label} · ${it.describe()}"
                                            else "未装备 · 击杀敌人有概率掉落",
                                            fontSize = 9.sp,
                                            color = if (it != null) rColor else GameUi.TextFaint,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GameActionButton(
                    "关闭",
                    onClick = {
                        showBag = false
                        if (hud.phase == DungeonGame.Phase.PAUSED && !showEvent) game.resume()
                    },
                    primary = false,
                    height = 38.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Boss 血条（顶部中央，横屏压窄）：名牌 + 三阶段刻度 + 掉血残影
        if (hud.bossHp > 0f) {
            Column(
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 66.dp)
                    .widthIn(min = 320.dp, max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👹", fontSize = 13.sp)
                    Text(
                        "第${hud.floor}层 · 深渊领主",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(start = 5.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 阶段指示点：当前阶段数亮红，其余暗
                    repeat(3) { i ->
                        Box(
                            Modifier.padding(horizontal = 2.dp).size(7.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (i < hud.bossPhase) Color(0xFFFF5252) else Color(0x33FFFFFF),
                                )
                                .border(
                                    1.dp,
                                    if (i < hud.bossPhase) Color(0xFFFF8A80) else Color(0x26FFFFFF),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp).height(13.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xCC0A0508))
                        .border(1.dp, Color(0x66FF8A80), RoundedCornerShape(7.dp)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(bossGhost.coerceIn(0f, 1f)).height(13.dp)
                            .background(Color(0xAAFFFFFF), RoundedCornerShape(7.dp)),   // 白色残影：刚掉的血缓落
                    )
                    Box(
                        Modifier.fillMaxWidth(hud.bossHp.coerceIn(0f, 1f)).height(13.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0xFF8E1B1B), Color(0xFFE15A5A), Color(0xFFFF8A80)),
                                ),
                                RoundedCornerShape(7.dp),
                            ),
                    )
                    Canvas(Modifier.matchParentSize()) {   // 阶段刻度 33%/66% + 阶段切换白闪
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.5f)
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.5f)
                        if (bossFlash > 0f) drawRect(Color.White.copy(alpha = (bossFlash / 0.3f).coerceIn(0f, 1f) * 0.7f), size = size)
                    }
                }
            }
        }

        // FPS 显示（批16：设置开关，真机性能量化）
        if (hud.phase == DungeonGame.Phase.EXPLORING && DungeonSettings.fpsVisible(context) && fpsAvg.floatValue > 0f) {
            Text(
                "FPS ${fpsAvg.floatValue.toInt()}",
                Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, top = 142.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (fpsAvg.floatValue >= 55f) Color(0xFF7EE38A) else if (fpsAvg.floatValue >= 40f) Color(0xFFFFD54F) else Color(0xFFFF6E6E),
            )
        }

        // 小地图（右上，暂停按钮下方；横屏压缩尺寸）
        if (hud.phase != DungeonGame.Phase.READY && hud.phase != DungeonGame.Phase.CLASS_SELECT) {
            Minimap(
                game,
                Modifier.align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 62.dp, end = 12.dp),
            )
        }

        // 连杀/连击播报：金色渐变胶囊，Boss 在场时下移避开血条
        if (hud.phase == DungeonGame.Phase.EXPLORING && (hud.streak >= 3 || hud.combo >= 5)) {
            Row(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = if (hud.bossHp > 0f) 118.dp else 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            if (hud.streak >= 10)
                                listOf(Color(0x00FFD54F), Color(0xB3FFB300), Color(0x00FFD54F))
                            else listOf(Color(0x00000000), Color(0x66000000), Color(0x00000000)),
                        ),
                    )
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        if (hud.streak >= 3) append("🔥 ${hud.streak} 连杀")
                        if (hud.streak >= 3 && hud.combo >= 5) append("    ")
                        if (hud.combo >= 5) append("⚡ ${hud.combo} 连击")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hud.streak >= 10) Color(0xFFFFD54F) else Color.White,
                )
            }
        }

        // ---------- 过场：黑幕 + 「第 X 层」 + 层引言（空洞骑士/Hades 式碎片叙事） ----------
        if (hud.transition > 0f) {
            val a = (minOf(hud.transition / 0.25f, (1.2f - hud.transition) / 0.25f)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f * a)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "第 ${hud.floor + 1} 层",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = a),
                    )
                    Text(
                        FLOOR_QUOTES[(hud.floor + 1).mod(FLOOR_QUOTES.size)],
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE).copy(alpha = a),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // ---------- 游戏大厅（批24 王者式：场景背景 + 顶部资源条 + 左导航 + 立绘展台 + 行动面板） ----------
        if (hud.phase == DungeonGame.Phase.READY) {
            val lobbyCls = ClassDef.byId(hud.lobbyCls)
            // 图鉴只统计真实怪物键（答题事件会写入 QUIZ_* 标记，不能算进收藏）
            val seenCount = player.seenMonsters.count { !it.startsWith("QUIZ_") }.coerceAtMost(11)
            val seenTotal = 11
            GameBackdrop()
            // 顶栏：返回 / 标题 / 资源 / 设置
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton("←", onClick = { nav.popBackStack() }, size = 40.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏰", fontSize = 20.sp)
                        Text(
                            "地牢幸存者",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GamePalette.UI_GOLD,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Text("ROGUELIKE · 每一次冒险，都是新的地牢", fontSize = 10.sp, color = GameUi.TextDim)
                }
                // 金币
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xB30B1018))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(11.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.brainquest.game.ui.CoinIcon(15.dp)
                    Text(
                        " ${player.coins}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GamePalette.UI_GOLD,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 图鉴进度（可点直达图鉴页；批26 左导航移除后这里是唯一入口）
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xB30B1018))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(11.dp))
                        .clickable { showIndex = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📖", fontSize = 13.sp)
                    Text(
                        " $seenCount/$seenTotal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE1BEE7),
                    )
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton("⚙", onClick = { showSettings = true })
            }
            // 主体三栏：左功能导航 | 角色展台 | 模式行动面板
            Row(
                Modifier.fillMaxSize().align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 20.dp, end = 20.dp, top = 66.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 角色展台：光环 + 立绘 + 悬浮切换 + 底部信息卡
                Box(Modifier.weight(0.44f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height * 0.42f
                        val t = animT.floatValue
                        val breath = 0.5f + 0.5f * kotlin.math.sin(t * 1.6f)
                        // 地面光环（落在立绘脚下、信息卡上方的空档）
                        drawOval(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(Color(lobbyCls.accentColor).copy(alpha = 0.22f + 0.10f * breath), Color.Transparent),
                                center = Offset(cx, cy + size.height * 0.18f),
                                radius = size.width * 0.36f,
                            ),
                            topLeft = Offset(cx - size.width * 0.36f, cy + size.height * 0.10f),
                            size = Size(size.width * 0.72f, size.height * 0.16f),
                        )
                        // 上下两道职业色环，构成立绘展台
                        drawOval(
                            Color(lobbyCls.accentColor).copy(alpha = 0.30f),
                            topLeft = Offset(cx - size.width * 0.26f, cy + size.height * 0.13f),
                            size = Size(size.width * 0.52f, size.height * 0.12f),
                            style = Stroke(2f),
                        )
                        drawOval(
                            Color(lobbyCls.accentColor).copy(alpha = 0.14f),
                            topLeft = Offset(cx - size.width * 0.32f, cy + size.height * 0.10f),
                            size = Size(size.width * 0.64f, size.height * 0.17f),
                            style = Stroke(4f),
                        )
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        EntityRenderer.drawPortrait(
                            this, lobbyCls,
                            size.width / 2f, size.height * 0.40f, 1.5f, animT.floatValue,
                        )
                    }
                    RoundIconButton(
                        "◀",
                        onClick = { game.cycleClass(-1) },
                        size = 42.dp,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
                    )
                    RoundIconButton(
                        "▶",
                        onClick = { game.cycleClass(1) },
                        size = 42.dp,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                    )
                    GamePanel(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.96f),
                        contentPadding = 12.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lobbyCls.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "基础职业",
                                fontSize = 9.sp,
                                color = GameUi.TextDim,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x1FFFFFFF)).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            lobbyCls.desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = GameUi.TextDim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniStat("❤", "${lobbyCls.maxHp}", GameUi.Hp)
                            MiniStat("⚔", "${lobbyCls.attack}", GamePalette.UI_GOLD)
                            MiniStat("👟", "${lobbyCls.speed.toInt()}", GameUi.Cyan)
                            MiniStat("⚡", "${lobbyCls.attackInterval}s", GameUi.Xp)
                        }
                        Text(
                            "${lobbyCls.passiveName} · 技能：${lobbyCls.skillName}",
                            fontSize = 10.sp,
                            color = Color(0xFF78909C),
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                // 右栏行动面板
                Column(Modifier.weight(0.56f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    GamePanel(Modifier.fillMaxWidth(), accent = GamePalette.UI_GOLD, contentPadding = 14.dp) {
                        PanelTitle("选择模式", "四种玩法 · 随时切换")
                        Spacer(Modifier.height(9.dp))
                        val modes = listOf(
                            Triple("🗡️", "标准 · 5 层", "经典闯关，层层深入") to "standard",
                            Triple("♾️", "无尽 · 无限", "看你能撑到第几层") to "endless",
                            Triple("👑", "魔王连战", "全程 Boss 车轮战") to "bossrush",
                            Triple("⚔️", "角斗场", "无尽波次生存挑战") to "arena",
                        )
                        modes.chunked(2).forEach { rowModes ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowModes.forEach { (info, id) ->
                                    val (icon, name, desc) = info
                                    ModeCard(
                                        icon = icon,
                                        title = name,
                                        desc = desc,
                                        selected = hud.mode == id,
                                        onClick = { game.setRunMode(id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowModes.size < 2) Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GameActionButton(
                            "⭐ 天赋养成",
                            onClick = { nav.navigate(com.brainquest.game.ui.Routes.TALENTS) },
                            primary = false,
                            height = 42.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        GameActionButton(
                            "▶  开始冒险",
                            onClick = {
                                game.pendingPerks = player.dungeonPerks
                                game.startFromLobby()
                            },
                            accent = Color(0xFF7C4DFF),
                            height = 56.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🏆 通关 ${player.dungeonWins} 次", fontSize = 10.sp, color = GameUi.TextDim)
                            Text("最高 ${player.bestScores["dungeon_floor"] ?: 0} 层", fontSize = 10.sp, color = GameUi.TextDim)
                            Text("击杀 ${player.totalDungeonKills}", fontSize = 10.sp, color = GameUi.TextDim)
                        }
                    }
                }
            }
            GameVignette()
        }
        // ---------- 职业选择（批24：三张职业卡 + 场景背景，替换默认 M3 卡片） ----------
        if (hud.phase == DungeonGame.Phase.CLASS_SELECT) {
            Box(Modifier.fillMaxSize()) {
                GameBackdrop(accent = GamePalette.UI_EXP)
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "选择你的职业",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "最高层数纪录：${player.bestScores["dungeon_floor"] ?: 0} 层 · 选好职业即可开始冒险",
                        style = MaterialTheme.typography.labelMedium,
                        color = GamePalette.UI_GOLD,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 1020.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ClassDef.ALL.forEach { c ->
                            val accent = Color(c.accentColor)
                            GamePanel(
                                modifier = Modifier.weight(1f),
                                accent = accent,
                                contentPadding = 16.dp,
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(74.dp)) {
                                        drawCircle(accent.copy(alpha = 0.14f), size.minDimension / 2f)
                                        drawRoundRect(
                                            Color(c.bodyColor),
                                            Offset(size.width / 2f - 12f, size.height / 2f - 8f),
                                            Size(24f, 30f), CornerRadius(7f),
                                        )
                                        drawCircle(Color(c.accentColor), 11f, Offset(size.width / 2f, size.height / 2f - 24f))
                                        drawCircle(Color(0xFF0E1016), 2f, Offset(size.width / 2f - 4f, size.height / 2f - 25f))
                                        drawCircle(Color(0xFF0E1016), 2f, Offset(size.width / 2f + 4f, size.height / 2f - 25f))
                                    }
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                    Text(
                                        c.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GameUi.TextDim,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Row(
                                        Modifier.padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        MiniStat("❤", "${c.maxHp}", GameUi.Hp)
                                        MiniStat("⚔", "${c.attack}", GamePalette.UI_GOLD)
                                        MiniStat("👟", "${c.speed.toInt()}", GameUi.Cyan)
                                    }
                                    Text(
                                        c.passiveName,
                                        fontSize = 10.sp,
                                        color = Color(0xFF78909C),
                                        modifier = Modifier.padding(top = 8.dp),
                                        maxLines = 1,
                                    )
                                    Text(
                                        "主动技能 · ${c.skillName}",
                                        fontSize = 10.sp,
                                        color = GameUi.Cyan,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    GameActionButton(
                                        "选择 ${c.name.substringAfterLast(" ")}",
                                        onClick = {
                                            game.pendingPerks = player.dungeonPerks
                                            game.selectClass(c.id)
                                        },
                                        accent = accent,
                                        height = 38.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- LevelUp 三选一（读引擎候选；批24 游戏化卡牌） ----------
        if (hud.phase == DungeonGame.Phase.LEVELUP) {
            Box(
                Modifier.fillMaxSize().background(Color(0xB305070C)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .graphicsLayer {
                            scaleX = lvlScale.value; scaleY = lvlScale.value
                            alpha = 0.4f + 0.6f * lvlScale.value
                        }
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
                            Text("Lv.${hud.level} · 强化会持续到本局结束", fontSize = 10.sp, color = GameUi.TextDim)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 780.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        game.engine.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(
                                modifier = Modifier.weight(1f),
                                accent = GamePalette.UI_EXP,
                                contentPadding = 16.dp,
                            ) {
                                Text(
                                    "强化 0${idx + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GamePalette.UI_GOLD,
                                )
                                Text(
                                    up.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    up.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GameUi.TextDim,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                GameActionButton(
                                    "选择",
                                    onClick = { game.chooseUpgrade(up.id) },
                                    accent = Color(0xFF7C4DFF),
                                    height = 38.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 商店（批24：面板化货架 + 品质卡 + 统一按钮） ----------
        if (hud.phase == DungeonGame.Phase.SHOP) {
            LaunchedEffect(hud.phase) { rerollPick = false }
            Box(
                Modifier.fillMaxSize().background(Color(0xC705070C)),
                contentAlignment = Alignment.Center,
            ) {
                GamePanel(
                    modifier = Modifier.widthIn(max = 880.dp)
                        .graphicsLayer { scaleX = lvlScale.value; scaleY = lvlScale.value },
                    accent = GamePalette.UI_GOLD,
                    contentPadding = 18.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PanelTitle(
                            "🏪 地牢商店",
                            if (rerollPick) "选择要重铸的装备（保留品质，重随词条）" else "用本局金币强化这一趟冒险",
                            accent = GamePalette.UI_GOLD,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x8C0B1018))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.brainquest.game.ui.CoinIcon(15.dp)
                            Text(
                                " ${hud.coins}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GamePalette.UI_GOLD,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (rerollPick) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Equipment.Slot.entries.forEach { slot ->
                                val cur = game.slots[slot]
                                val has = cur != null
                                val rColor = cur?.let { Color(it.rarityColorLong) } ?: Color(0x33FFFFFF)
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x66000000))
                                        .border(1.dp, if (has) rColor else Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                                        .clickable(enabled = has) { game.rerollSlot(slot); rerollPick = false }
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(slotEmoji(slot), fontSize = 18.sp)
                                    Text(
                                        slot.label,
                                        fontSize = 9.sp,
                                        color = if (has) GameUi.TextDim else GameUi.TextFaint,
                                    )
                                    Text(
                                        cur?.name?.take(4) ?: "空",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (has) rColor else GameUi.TextFaint,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        GameActionButton(
                            "返回货架",
                            onClick = { rerollPick = false },
                            primary = false,
                            height = 40.dp,
                            modifier = Modifier.width(180.dp).align(Alignment.CenterHorizontally),
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            game.shopGoods.forEachIndexed { idx, g ->
                                val afford = hud.coins >= g.cost
                                val accent = when {
                                    g.sold -> GameUi.TextFaint
                                    afford -> GamePalette.UI_GOLD
                                    else -> GameUi.TextDim
                                }
                                GamePanel(
                                    modifier = Modifier.weight(1f),
                                    accent = accent,
                                    contentPadding = 12.dp,
                                ) {
                                    Text(
                                        if (g.sold) "已售出" else g.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (g.sold) GameUi.TextFaint else Color.White,
                                        maxLines = 1,
                                    )
                                    Text(
                                        g.desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GameUi.TextDim,
                                        modifier = Modifier.padding(top = 2.dp),
                                        minLines = 2,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.brainquest.game.ui.CoinIcon(13.dp)
                                        Text(
                                            " ${g.cost}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    GameActionButton(
                                        if (g.sold) "已售出" else "购买",
                                        onClick = {
                                            if (g.id == "reroll") {
                                                if (!g.sold && hud.coins >= g.cost) rerollPick = true
                                            } else game.buyGood(idx)
                                        },
                                        enabled = !g.sold && afford,
                                        accent = Color(0xFF7C4DFF),
                                        height = 36.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        GameActionButton(
                            "离开商店",
                            onClick = { game.closeShop() },
                            primary = false,
                            height = 40.dp,
                            modifier = Modifier.width(180.dp).align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }

        // ---------- Paused（批24 统一游戏弹窗；背包打开时让位给背包） ----------
        if (hud.phase == DungeonGame.Phase.PAUSED && !showBag) {
            GameDialog(title = "已暂停", accent = GameUi.Cyan, width = 380.dp) {
                Text(
                    "地牢暂时安静下来。喝口水，想想下一步怎么走。",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameUi.TextDim,
                )
                Spacer(Modifier.height(14.dp))
                GameActionButton(
                    "继续冒险",
                    onClick = { game.resume() },
                    accent = Color(0xFF7C4DFF),
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GameActionButton(
                    "返回游戏大厅",
                    onClick = { confirmExit = true },
                    primary = false,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---------- GameOver（批24：结算统计格） ----------
        if (hud.phase == DungeonGame.Phase.GAMEOVER) {
            GameDialog(title = "💀 你倒在了地牢里", accent = GameUi.Hp, width = 500.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "第${hud.floor}层 · ${if (hud.endless) "无尽模式" else "标准模式"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GameUi.TextDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultStat("🏰", "${hud.floor}", "到达层数", Modifier.weight(1f), GamePalette.UI_GOLD)
                        ResultStat("💀", "${hud.kills}", "击杀", Modifier.weight(1f))
                        ResultStat("⏱", formatTime(hud.timeSec), "存活", Modifier.weight(1f), GameUi.Cyan)
                        ResultStat("💰", "${hud.coins}", "金币", Modifier.weight(1f), GamePalette.UI_GOLD)
                    }
                    Spacer(Modifier.height(16.dp))
                    GameActionButton(
                        "返回大厅",
                        onClick = { rewarded = false; game.exitToLobby() },
                        accent = Color(0xFF7C4DFF),
                        height = 46.dp,
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

        // ---------- Victory（批24：结算统计格） ----------
        if (hud.phase == DungeonGame.Phase.VICTORY) {
            GameDialog(title = "🏆 地牢通关！", accent = GamePalette.UI_GOLD, width = 500.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "你穿过了五层地牢，带着战利品凯旋。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GameUi.TextDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultStat("⏱", formatTime(hud.timeSec), "用时", Modifier.weight(1f), GameUi.Cyan)
                        ResultStat("💀", "${hud.kills}", "击杀", Modifier.weight(1f))
                        ResultStat("⬆️", "Lv.${hud.level}", "等级", Modifier.weight(1f), GameUi.Xp)
                        ResultStat("💰", "${hud.coins}", "金币", Modifier.weight(1f), GamePalette.UI_GOLD)
                    }
                    Spacer(Modifier.height(16.dp))
                    GameActionButton(
                        "返回大厅",
                        onClick = { rewarded = false; game.exitToLobby() },
                        accent = GamePalette.UI_GOLD,
                        textColor = Color(0xFF1A1200),
                        height = 46.dp,
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

        // ---------- 返回确认（批24 统一游戏弹窗） ----------
        if (confirmExit) {
            GameDialog(title = "退出本局？", accent = GameUi.Hp, width = 400.dp) {
                Text(
                    "探索进度不会保存，是否返回游戏大厅？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GameUi.TextDim,
                )
                Spacer(Modifier.height(14.dp))
                GameActionButton(
                    "返回游戏大厅",
                    onClick = { confirmExit = false; game.exitToLobby() },
                    accent = GameUi.Hp,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GameActionButton(
                    "继续冒险",
                    onClick = {
                        confirmExit = false
                        if (hud.phase == DungeonGame.Phase.PAUSED) game.resume()
                    },
                    primary = false,
                    height = 40.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // ---------- 📖 怪物图鉴（批24：场景背景 + 4 列收藏卡） ----------
        if (showIndex) {
            val entries = listOf(
                Triple("swampy", "沼泽史莱姆", "SLIME"),
                Triple("skelet", "骷髅兵", "SKELETON"),
                Triple("skelet", "骷髅弓手", "BONE_ARCHER"),
                Triple("imp", "小恶魔蝠", "BAT"),
                Triple("necromancer", "邪教术士", "CASTER"),
                Triple("ogre", "巨型盾卫", "SHIELD_GUARD"),
                Triple("wogol", "自爆犬魔", "BOOM_SLIME"),
                Triple("big_demon", "魔王 · 大恶魔", "BOSS_big_demon"),
                Triple("chort", "魔王 · 短角恶魔", "BOSS_chort"),
                Triple("big_zombie", "魔王 · 巨尸", "BOSS_big_zombie"),
                Triple("ogre", "魔王 · 巨魔", "BOSS_ogre"),
            )
            val seenCount = entries.count { (_, _, key) ->
                player.seenMonsters.contains(key) || player.seenMonsters.contains(key.removePrefix("BOSS_"))
            }
            Box(Modifier.fillMaxSize()) {
                GameBackdrop(accent = Color(0xFFBA68C8))
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("←", onClick = { showIndex = false }, size = 38.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                "📖 怪物图鉴",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = GamePalette.UI_GOLD,
                            )
                            Text(
                                "已遭遇 ${seenCount} / ${entries.size} · 遭遇过的怪物会在这里留下记录",
                                fontSize = 10.sp,
                                color = GameUi.TextDim,
                            )
                        }
                        HudXpBar(
                            seenCount.toFloat() / entries.size,
                            Modifier.width(140.dp),
                            height = 6.dp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        entries.chunked(4).forEach { rowEntries ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowEntries.forEach { (sprite, cname, key) ->
                                    val seen = player.seenMonsters.contains(key) || player.seenMonsters.contains(key.removePrefix("BOSS_"))
                                    GamePanel(
                                        modifier = Modifier.weight(1f),
                                        accent = if (seen) Color(0xFFBA68C8) else null,
                                        contentPadding = 8.dp,
                                    ) {
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Canvas(Modifier.size(46.dp)) {
                                                if (seen && SpriteAnims.ready) {
                                                    val fr = SpriteAnims.frames(sprite, run = false)
                                                    if (fr.isNotEmpty()) {
                                                        val bmp = fr[0]
                                                        val k = (size.width / bmp.width).coerceAtMost(size.height / bmp.height)
                                                        drawImage(
                                                            bmp,
                                                            srcOffset = IntOffset(0, 0), srcSize = IntSize(bmp.width, bmp.height),
                                                            dstOffset = IntOffset(((size.width - bmp.width * k) / 2).toInt(), ((size.height - bmp.height * k) / 2).toInt()),
                                                            dstSize = IntSize((bmp.width * k).toInt(), (bmp.height * k).toInt()),
                                                            filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                                                        )
                                                    }
                                                } else {
                                                    drawRect(Color(0xFF0A0D14), size = size)
                                                    drawRect(Color(0x33FFFFFF), size = size, style = Stroke(1.5f))
                                                    drawCircle(Color(0x22FFFFFF), size.minDimension * 0.22f)
                                                }
                                            }
                                            Text(
                                                if (seen) cname else "？？？",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (seen) Color.White else GameUi.TextFaint,
                                                maxLines = 1,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                            Text(
                                                if (seen) "已收录" else "未遭遇",
                                                fontSize = 9.sp,
                                                color = if (seen) Color(0xFFBA68C8) else GameUi.TextFaint,
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ---------- 🎲/🧠 事件房（批24：统一弹窗 + 主题色） ----------
        if (showEvent) {
            val isQuiz = hud.eventKind == 1
            val quizQ = remember(showEvent, isQuiz) {
                if (isQuiz) vm.bank.pick(
                    com.brainquest.game.data.question.Subjects.curated.random(),
                    (hud.floor + 1).coerceIn(1, 5),
                ) else null
            }
            var quizChosen by remember(showEvent, isQuiz) { mutableStateOf<Int?>(null) }
            val eventAccent = if (isQuiz) Color(0xFF82B1FF) else Color(0xFFE1BEE7)
            GameDialog(
                title = if (isQuiz) "🧠 智慧试炼" else "🎲 神秘赌桌",
                accent = eventAccent,
                width = 520.dp,
            ) {
                Text(
                    if (isQuiz) "古老的法阵在你脚下亮起，考验你的智慧…"
                    else "一位兜帽商人朝你招了招手，桌上摆着三枚骰子…",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameUi.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                val res = eventResult
                if (isQuiz && quizQ != null && quizChosen == null) {
                    Text(
                        quizQ.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    quizQ.options.forEachIndexed { i, opt ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x66202A3C))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                                    indication = null,
                                    onClick = { quizChosen = i },
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(eventAccent.copy(alpha = 0.18f))
                                    .border(1.dp, eventAccent.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${'A' + i}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = eventAccent)
                            }
                            Text(
                                opt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                    Text(
                        "答对有赏 · 答错会惊动附近的怪物",
                        fontSize = 10.sp,
                        color = GameUi.TextFaint,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (isQuiz && quizQ != null) {
                    val correct = quizChosen == quizQ.answer
                    val msg = remember(quizChosen) {
                        if (quizChosen != null) game.quizOutcome(correct) else ""
                    }
                    LaunchedEffect(quizChosen) { if (quizChosen != null) vm.mergeDungeonSeen(listOf("QUIZ_${if (correct) "win" else "lose"}")) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (correct) "✅" else "❌", fontSize = 24.sp)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                if (correct) "答对了！" else "答错了（正确：${'A' + quizQ.answer}）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (correct) GameUi.Xp else GameUi.Hp,
                            )
                            Text("试炼的结果已经生效", fontSize = 10.sp, color = GameUi.TextDim)
                        }
                    }
                    Text(
                        msg,
                        style = MaterialTheme.typography.titleSmall,
                        color = GamePalette.UI_GOLD,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    GameActionButton(
                        "离开",
                        onClick = { showEvent = false; eventResult = null },
                        accent = Color(0xFF7C4DFF),
                        height = 42.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    if (res != null) {
                        Text(
                            res,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GamePalette.UI_GOLD,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        GameActionButton(
                            "离开赌桌",
                            onClick = { showEvent = false; eventResult = null },
                            accent = Color(0xFF7C4DFF),
                            height = 42.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        listOf(
                            Triple("🎲 掷骰", "50% 金币翻倍 / 50% 金币减半", 0),
                            Triple("🩸 血祭", "失去 30% 当前生命 → 获得史诗装备", 1),
                            Triple("⚒ 淬火", "花一笔金币 → 本局攻击永久提升", 2),
                        ).forEach { (title, desc, opt) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0x66241B36))
                                    .border(1.dp, Color(0x33E1BEE7), RoundedCornerShape(11.dp))
                                    .clickable(
                                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                                        indication = null,
                                        onClick = { eventResult = game.eventChoose(opt) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0AEC5))
                                }
                                Text("›", fontSize = 20.sp, color = eventAccent)
                            }
                        }
                        Text(
                            "✋ 离开（赌桌不会收过路费）",
                            style = MaterialTheme.typography.labelMedium,
                            color = GameUi.TextDim,
                            modifier = Modifier.clickable { showEvent = false }.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        // ---------- ⚙ 地牢设置面板（批24：三列紧凑布局，横屏一屏放下） ----------
        if (showSettings) {
            val ctx = context
            // 批26：这些设置存 SharedPreferences，直读不触发重组——本地状态镜像，点击立即刷新
            var dmgNum by remember { mutableStateOf(DungeonSettings.damageNumbers(ctx)) }
            var fpsOn by remember { mutableStateOf(DungeonSettings.fpsVisible(ctx)) }
            var spritesOn by remember { mutableStateOf(DungeonSettings.useSprites(ctx)) }
            var critZoomOn by remember { mutableStateOf(DungeonSettings.critZoom(ctx)) }
            var shakeLvl by remember { mutableIntStateOf(DungeonSettings.shakeLevel(ctx)) }
            var particleLvl by remember { mutableIntStateOf(DungeonSettings.particleDensity(ctx)) }
            GameDialog(
                title = "⚙ 地牢设置",
                accent = GameUi.Cyan,
                onDismiss = { showSettings = false },
                width = 800.dp,
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 第一列：声音
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("声音", fontSize = 10.sp, color = GamePalette.UI_GOLD, fontWeight = FontWeight.Bold)
                        ToggleSwitchRow("🔊 音效", player.soundOn, onToggle = { vm.setSettings(sound = it) })
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, GameUi.Border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎵 BGM 音量", style = MaterialTheme.typography.labelLarge, color = GameUi.Text, modifier = Modifier.weight(1f))
                                Text("${DungeonSettings.bgmVolume(ctx)}%", fontSize = 10.sp, color = GameUi.Cyan, fontWeight = FontWeight.Bold)
                            }
                            var bv by remember { mutableFloatStateOf(DungeonSettings.bgmVolume(ctx).toFloat()) }
                            Slider(
                                value = bv,
                                onValueChange = { bv = it; DungeonSettings.setBgmVolume(ctx, it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = GamePalette.UI_GOLD,
                                    activeTrackColor = GamePalette.UI_GOLD,
                                ),
                            )
                        }
                    }
                    // 第二列：画面反馈
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("画面反馈", fontSize = 10.sp, color = GamePalette.UI_GOLD, fontWeight = FontWeight.Bold)
                        ToggleSwitchRow("🔢 伤害数字", dmgNum, onToggle = {
                            DungeonSettings.setDamageNumbers(ctx, it); dmgNum = it
                        })
                        ToggleSwitchRow("📈 显示帧率", fpsOn, onToggle = {
                            DungeonSettings.setFpsVisible(ctx, it); fpsOn = it
                        })
                        ToggleSwitchRow("🧩 精灵图渲染", spritesOn, onToggle = {
                            DungeonSettings.setUseSprites(ctx, it); spritesOn = it
                        })
                        ToggleSwitchRow("🔍 暴击画面缩放", critZoomOn, onToggle = {
                            DungeonSettings.setCritZoom(ctx, it); critZoomOn = it
                        })
                    }
                    // 第三列：手感 / 性能
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("手感 / 性能", fontSize = 10.sp, color = GamePalette.UI_GOLD, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, GameUi.Border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📳 震屏", style = MaterialTheme.typography.labelLarge, color = GameUi.Text, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("关" to 0, "弱" to 1, "强" to 2).forEach { (label, v) ->
                                    ChoicePill(
                                        label,
                                        selected = shakeLvl == v,
                                        onClick = { DungeonSettings.setShakeLevel(ctx, v); shakeLvl = v },
                                        accent = GamePalette.UI_GOLD,
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, GameUi.Border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✨ 粒子", style = MaterialTheme.typography.labelLarge, color = GameUi.Text, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("低" to 0, "中" to 1, "高" to 2).forEach { (label, v) ->
                                    ChoicePill(
                                        label,
                                        selected = particleLvl == v,
                                        onClick = { DungeonSettings.setParticleDensity(ctx, v); particleLvl = v },
                                        accent = GameUi.Xp,
                                    )
                                }
                            }
                        }
                        GameActionButton(
                            "📖 重看新手引导",
                            onClick = {
                                DungeonSettings.resetTutorial(ctx)
                                showTutorial = true
                            },
                            primary = false,
                            height = 36.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "低帧率时自动降低粒子密度；也可在这里手动调低。",
                            fontSize = 9.sp,
                            color = GameUi.TextFaint,
                        )
                    }
                }
                }
            }
        }

        // ---------- 首次进地牢新手引导（一次性，点任意处消失；批24 面板化） ----------
        if (showTutorial) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            showTutorial = false
                            prefsEdit.edit().putBoolean("dg_tutorial_shown", true).apply()
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                GamePanel(
                    Modifier.widthIn(max = 560.dp),
                    accent = GamePalette.UI_GOLD,
                    contentPadding = 20.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📜", fontSize = 24.sp)
                        Text(
                            "地牢生存指南",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GamePalette.UI_GOLD,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "🕹" to "左侧摇杆移动，贴近敌人自动挥砍",
                        "🌀" to "右下技能键好了就放，各职业各有绝活",
                        "💥" to "击杀攒爆发能量，满了放全屏清场",
                        "🚪" to "清空房间撕开屏障，门后徽章预告房型",
                    ).forEach { (icon, text) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33000000))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(icon, fontSize = 16.sp)
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GameUi.Text,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "—— 点击任意处开始探索 ——",
                        style = MaterialTheme.typography.labelMedium,
                        color = GameUi.TextDim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

    }
}



/** 大厅立绘下方的小数值徽章 */
@Composable
private fun MiniStat(icon: String, value: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x66000000))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 9.sp)
        Text(
            value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/** 槽位图标：装备栏 6 格用图形区分，不再用首个汉字 */
private fun slotEmoji(slot: Equipment.Slot): String = when (slot) {
    Equipment.Slot.WEAPON -> "⚔"
    Equipment.Slot.HELMET -> "⛑"
    Equipment.Slot.ARMOR -> "🛡"
    Equipment.Slot.BOOTS -> "👟"
    Equipment.Slot.TRINKET1, Equipment.Slot.TRINKET2 -> "💍"
}

/** 小地图：房间缩略矩形，已探索亮起、当前高亮、连线表门 */
@Composable
private fun Minimap(game: DungeonGame, modifier: Modifier) {
    Canvas(modifier.width(104.dp).height(70.dp).background(Color(0x881A1F2E), RoundedCornerShape(10.dp)).border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))) {
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
            // 房间矩形按实际尺寸比例画（Boss 房大块、宝箱/商店小块）
            val rw = (cellW * 0.72f * (room.w / 2400f)).coerceAtLeast(cellW * 0.3f)
            val rh = (cellH * 0.72f * (room.h / 1700f)).coerceAtLeast(cellH * 0.3f)
            drawRoundRect(fill, Offset(c.x - rw / 2, c.y - rh / 2), Size(rw, rh), CornerRadius(4f))
            if (current) drawRoundRect(Color.White, Offset(c.x - rw / 2, c.y - rh / 2), Size(rw, rh), CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            // 房型图标：宝箱=金箱 商店=感叹号 Boss=红王冠 精英=紫菱形；普通怪房无标记
            val s = minOf(rw, rh) * 0.22f
            when (room.type) {
                RoomType.BOSS -> {
                    val red = Color(0xFFFF6E6E)
                    drawLine(red, Offset(c.x - s, c.y + s * 0.3f), Offset(c.x + s, c.y + s * 0.3f), 2.5f)
                    drawLine(red, Offset(c.x - s, c.y + s * 0.3f), Offset(c.x - s, c.y - s * 0.5f), 2.5f)
                    drawLine(red, Offset(c.x, c.y + s * 0.3f), Offset(c.x, c.y - s * 0.8f), 2.5f)
                    drawLine(red, Offset(c.x + s, c.y + s * 0.3f), Offset(c.x + s, c.y - s * 0.5f), 2.5f)
                }
                RoomType.CHEST -> {
                    drawRoundRect(Color(0xFFFFD54F), Offset(c.x - s, c.y - s * 0.7f), Size(s * 2, s * 1.4f), CornerRadius(2f))
                    drawRect(Color(0xFF6D4C41), Offset(c.x - s, c.y - s * 0.15f), Size(s * 2, s * 0.25f))
                }
                RoomType.SHOP -> {
                    drawRect(Color(0xFF7EE38A), Offset(c.x - s * 0.18f, c.y - s), Size(s * 0.36f, s * 1.15f))
                    drawCircle(Color(0xFF7EE38A), s * 0.24f, Offset(c.x, c.y + s * 0.65f))
                }
                RoomType.ELITE -> {
                    val p = Color(0xFFB388FF)
                    drawLine(p, Offset(c.x, c.y - s), Offset(c.x + s, c.y), 2.5f)
                    drawLine(p, Offset(c.x + s, c.y), Offset(c.x, c.y + s), 2.5f)
                    drawLine(p, Offset(c.x, c.y + s), Offset(c.x - s, c.y), 2.5f)
                    drawLine(p, Offset(c.x - s, c.y), Offset(c.x, c.y - s), 2.5f)
                }
                else -> {}
            }
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

/** 粒子（字段可变供对象池复用；shape 0=圆点 1=三角碎片；g/drag=重力/阻尼） */
private class ParticleFx() {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var t = 0f; var life = 0.4f
    var color: Color = Color.White
    var shape = 0f
    var rot = 0f; var vr = 0f
    var g = 0f; var drag = 0f
    fun set(x: Float, y: Float, vx: Float, vy: Float, life: Float, color: Color) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.t = 0f; this.life = life; this.color = color
        this.shape = 0f; this.rot = 0f; this.vr = 0f; this.g = 0f; this.drag = 0f
    }
}

/** Boss 出场横幅（屏幕层渲染，不受震屏影响） */
private class BannerFx(val text: String) {
    var t = 0f
}

/** 挥砍残影（出刀瞬间的弧光拖尾，0.22s 渐隐——连击时形成连贯扇面） */
private class SlashGhost(val facing: Float, val range: Float, val flip: Boolean, var t: Float = 0f)

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

/** 旋风斩双弧对转（0.4s） */
private class WhirlFx(val x: Float, val y: Float, var t: Float = 0f)

/** 暴风雪冰锥下落（0.25s，从上方 140px 落至落点） */
private class SpikeFx(val x: Float, val y: Float, var t: Float = 0f)

/** 箭雨雨线（0.5s，沿 dx/dy 方向运动的短线段） */
private class RainFx(var x: Float, var y: Float, val dx: Float, val dy: Float, var t: Float = 0f)

/** 暴风雪地面霜圈（残留 1.2s） */
private class FrostFx(val x: Float, val y: Float, var t: Float = 0f)

/** 击杀灵魂光点（0.45s，追踪玩家） */
private class SoulFx(var x: Float, var y: Float, var t: Float = 0f)

/** 每层引言（空洞骑士/Hades 式碎片叙事，按层数轮换） */
private val FLOOR_QUOTES = listOf(
    "越深的黑暗，越接近真相。",
    "火把会熄灭，勇气不会。",
    "骷髅也曾是冒险者——别走他们的老路。",
    "魔王在第五层等你，如果那时你还活着。",
    "金币叮当作响的地方，陷阱也在低语。",
    "精英怪带着词缀，也带着更肥的掉落。",
    "能量满了就放，犹豫的爆发不如不放。",
    "门后的徽章，是你唯一靠谱的地图。",
    "盾卫的正面是墙，绕后是门。",
    "自爆的史莱姆跑得欢——离它远点。",
    "有人在这里找到了传世神装，也有人只找到了骨头。",
    "真正的地牢没有回头的路，只有更深的路。",
)


/** 设置面板行：独立圆角小组件卡（批18 组件化：卡底+描边+间隔，不再是通栏平铺行） */
@Composable
private fun SettingRow(label: String, value: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A2030))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
            color = if (value == "开") GamePalette.UI_EXP else Color(0xFF90A4AE),
        )
    }
}
