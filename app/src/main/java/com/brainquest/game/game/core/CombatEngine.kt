package com.brainquest.game.game.core

import com.brainquest.game.game.dungeon.model.EnemyKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 通用实时战斗引擎（迷你幸存者验证过的骨架的泛化版，供地牢幸存者使用）：
 * 玩家属性面板 / 实体容器与 alive 清理 / 圆形碰撞两趟扫描 / 经验球吸附 /
 * 升级三选一 / dt 钳制 / 敌人弹幕 / 元素状态挂接。
 * 开火动作由 [Weapon] 实现产生（弹道、近战扇形……），引擎只负责"场上的东西怎么飞、怎么撞"。
 * 零 Compose 依赖。
 */
class CombatEngine {

    enum class Phase { IDLE, PLAYING, LEVELUP, GAMEOVER }

    // ---------- 实体 ----------
    class Enemy(
        var x: Float, var y: Float,
        val r: Float,
        var hp: Float, val maxHp: Float,
        var speed: Float,
        val dmg: Float,
        val kind: EnemyKind,
        val elite: Boolean = false,
        val xpValue: Int = 1,
    ) {
        var alive = true
        var dying = false          // 死亡动画中（0.3s 缩放淡出），不再参与逻辑
        var deathTimer = 0f
        var bossFloor = 0          // >0 = 该层的 Boss（多阶段）
        var phase = 1              // Boss 阶段（血量 <30% 进入狂暴 = 阶段 2）
        var specialTimer = 4f      // Boss 特殊技计时
        var shieldHp = 0f          // 精英「护盾」词缀 / Boss 护盾：先扣护盾
        var reflect = false        // 反伤词缀
        var affix: Affix? = null   // 精英词缀
        // 元素状态（由 ElementSystem 维护）
        var burnStacks = 0; var burnTimer = 0f
        var slowStacks = 0; var slowTimer = 0f
        var frozen = 0f
        // 受击闪白 / 死亡动画（渲染用）
        var hitFlash = 0f
        // AI 辅助（各 kind 自用；灼烧节拍单独用 burnTick，避免互踩）
        var aiTimer = 0f
        var burnTick = 0f
        var wobbleSeed = Random.nextFloat() * 6.28f
        // 打击感：击退速度/剩余时间、硬直
        var kbVX = 0f; var kbVY = 0f; var kbT = 0f
        var stun = 0f
        var dormant = false   // 待机（预刷新可见但未激活：不动/不伤人/不可被击）
        // 攻击状态机：0=普通 1=前摇（可预判） 2=出手/恢复
        var atkState = 0
        var atkTimer = 0f
        var atkDirX = 0f; var atkDirY = 0f
        var atkCd = 0f
    }

    class Bullet(
        var x: Float, var y: Float,
        val dx: Float, val dy: Float,
        val r: Float,
        var dmg: Float,
        val element: Element?,
        val fromEnemy: Boolean,
        val life: Float = 2.2f,
        var pierce: Int = 0,
    ) {
        var alive = true
        var age = 0f
    }

    /** 近战挥砍的前摇→判定（阶段 2 武器） */
    class Slash(var timer: Float, val windup: Float, val range: Float, val arcDeg: Float, var dmg: Float, var fired: Boolean = false)

    class Orb(var x: Float, var y: Float, val value: Int) {
        var alive = true
        var magnet = false
        var vx = 0f; var vy = 0f   // 掉落弹出初速（阻尼衰减）
    }

    /** 帧事件（伤害飘字/粒子/闪电，渲染层消费后转成持续特效） */
    data class FxEvent(
        val x: Float, val y: Float, val text: String, val crit: Boolean,
        val element: Element?, val kind: Int,   // kind 0=伤害 1=死亡 2=拾取 3=闪电段 4=冲击环
        val x2: Float = 0f, val y2: Float = 0f, // 闪电段终点
        val tint: Int = 0,                      // 死亡粒子着色（ARGB，0=默认）
    )

    /** 精英词缀 */
    enum class Affix { RAGE, SHIELD, SPLIT, VAMPIRE, REGEN, FROST }

    /** 强化选项 */
    data class Upgrade(val id: String, val name: String, val desc: String)

    // ---------- 玩家属性 ----------
    var px = 0f; var py = 0f
    var hp = 100; private set
    var maxHp = 100; private set
    var attack = 10; private set
    var attackInterval = 0.8f; private set
    var speed = 220f; private set
    var pickupRange = 55f; private set
    var elemPower = 1f; private set        // 元素伤害倍率
    var critChance = 0.05f; private set
    var facing = 0f; private set
    var walkPhase = 0f; private set
    var moving = false; private set

    // ---------- 状态 ----------
    var phase = Phase.IDLE; private set
    var level = 1; private set
    var xp = 0; private set
    var xpNext = 6; private set
    var invincible = 0f; private set
    /** 霜环减速剩余时间（精英「霜环」词缀靠近时刷新） */
    var chill = 0f; private set
    var attackTimer = 0f; private set
    private var timeAcc = 0f
    var elapsed = 0f; private set
    // 打击感：命中停顿 / 玩家受击闪白 / 移动惯性
    var hitStop = 0f; private set
    var playerFlash = 0f; private set
    private var velX = 0f; private var velY = 0f
    // 连杀：3 秒内连续击杀；受伤断连
    var killStreak = 0; private set
    var killStreakTimer = 0f; private set
    /** 进行中的近战挥砍（前摇→判定→后摇），渲染层读来画轨迹 */
    var activeSlash: Slash? = null; private set

    // ---------- 输入 ----------
    var joyActive = false
    var joyX = 0f; var joyY = 0f
    var attackHeld = false        // 攻击键按住（手动操作）
    var autoAttack = false        // 自动驾驶/回归测试用：不按键也持续攻击
    var aimTarget: Enemy? = null; private set   // 锁定目标（可视范围内最近敌人）

    // ---------- 容器 ----------
    val enemies = ArrayList<Enemy>(64)
    val bullets = ArrayList<Bullet>(64)
    val orbs = ArrayList<Orb>(64)
    val events = ArrayList<FxEvent>(16)
    private val pendingSpawns = ArrayList<Pair<Float, Enemy>>(8)   // 延迟刷怪（进房分波）
    private val spawnNow = ArrayList<Enemy>(4)   // 迭代中的安全生成队列（Boss召唤/精英分裂）

    /** 场地边界（敌人不出当前房间）；null = 不限制 */
    var arenaLeft = 0f; var arenaTop = 0f
    var arenaRight = 0f; var arenaBottom = 0f

    /** 职业被动 id（knight 格挡 / mage 击杀回血 / ranger 闪避），null = 无 */
    var passiveId: String? = null

    // ---------- 主动技能（职业固定专属，开局由 DungeonGame 注入，无过层选取） ----------
    var skillId: String? = null; private set
    var skillCd = 0f; private set
    var skillCdMax = 14f
    var timeScale = 1f; private set      // 时间减速/冰冻全局倍率（只作用于敌人）
    private var timeScaleTimer = 0f
    var shake = 0f; private set          // 震屏强度（渲染层读，随 tick 衰减）
    var shieldTime = 0f; private set     // 护盾剩余时间
    private var shieldLeft = 0f          // 护盾剩余吸收量

    fun setSkill(id: String) { skillId = id; skillCd = 0f }

    fun useSkill(): Boolean {
        val id = skillId ?: return false
        if (phase != Phase.PLAYING || skillCd > 0f) return false
        skillCd = skillCdMax
        when (id) {
            "dash" -> {   // 冲刺：朝面向位移 240px + 短无敌 + 残影
                repeat(4) { i ->
                    events.add(FxEvent(px - cos(facing) * 60f * (i + 1), py - sin(facing) * 60f * (i + 1), "", false, null, 5))
                }
                px += cos(facing) * 240f; py += sin(facing) * 240f
                invincible = invincibleSec
                shake = 6f
            }
            "shield" -> { shieldTime = 8f; shieldLeft = 50f }
            "heal" -> { heal((maxHp * 0.4f).toInt()); events.add(FxEvent(px, py, "", false, null, 6)) }
            "slowtime" -> { timeScaleTimer = 5f; timeScale = 0.3f }
            "freeze" -> enemies.forEach { if (!it.dormant) it.frozen = maxOf(it.frozen, 2.5f) }
            "meteor" -> nearestEnemy(700f)?.let { t ->
                aoe(t.x, t.y, 150f, attack * 4f, Element.FIRE)
                shake = 10f
            }
            "chain" -> nearestEnemy(600f)?.let { t ->
                var src = t; val hitSet = mutableSetOf(t); var dmg = attack * 2.5f
                t.hitFlash = 0.15f; t.hp -= dmg
                if (t.hp <= 0f && t.alive) killEnemy(t)
                repeat(4) {
                    val nx2 = queryNeighbors(src, 260f, hitSet).firstOrNull() ?: return@repeat
                    hitSet.add(nx2)
                    events.add(FxEvent(src.x, src.y, "", false, Element.THUNDER, 3, nx2.x, nx2.y))
                    nx2.hitFlash = 0.15f; nx2.hp -= dmg
                    if (nx2.hp <= 0f && nx2.alive) killEnemy(nx2)
                    src = nx2; dmg *= 0.75f
                }
            }
            "whirlwind" -> {   // 旋风斩（剑士）：以自身为圆心的环形斩 + 小段击退
                aoe(px, py, 135f, attack * 2.2f, Element.PHYSICAL)
                events.add(FxEvent(px, py, "", false, null, 11))
                shake = 8f
            }
            "blizzard" -> {    // 暴风雪（法师）：视野内至多 5 敌落冰锥，附带减速
                val targets = enemies.filter {
                    it.alive && !it.dormant && hypot(it.x - px, it.y - py) < 520f
                }.shuffled(Random).take(5)
                for (t in targets) {
                    hitEnemy(t, attack * 1.6f, Element.ICE)
                    t.slowStacks = maxOf(t.slowStacks, 2)
                    t.slowTimer = maxOf(t.slowTimer, 3f)
                }
                events.add(FxEvent(px, py, "", false, null, 11))
                shake = 6f
            }
            "arrowrain" -> {   // 箭雨（游侠）：朝面向 ±28° 扇形齐射 8 箭
                repeat(8) { i ->
                    val a = facing + (i - 3.5f) * 0.14f
                    fire(px + cos(a) * 20f, py + sin(a) * 20f, cos(a), sin(a), 460f, 5f, attack * 1.4f, Element.PHYSICAL)
                }
            }
        }
        return true
    }

    /** 当前武器 */
    var weapon: Weapon = Weapon.RapidShot()

    /** 墙体查询（地牢注入：开放矩形并集）；null = 自由移动。轴分离应用。 */
    var canPass: ((Float, Float) -> Boolean)? = null

    /** 击杀回调（地牢用于统计/职业被动/清房判定） */
    var onEnemyKilled: ((Enemy) -> Unit)? = null

    // ---------- 常量 ----------
    val playerR = 17f
    private val invincibleSec = 0.9f

    fun setStats(maxHp: Int, attack: Int, attackInterval: Float, speed: Float) {
        this.maxHp = maxHp; this.hp = maxHp
        this.attack = attack; this.attackInterval = attackInterval; this.speed = speed
    }

    fun begin() {
        phase = Phase.PLAYING
        elapsed = 0f
    }

    fun reset() {
        enemies.clear(); bullets.clear(); orbs.clear(); events.clear(); pendingSpawns.clear()
        hp = maxHp
        level = 1; xp = 0; xpNext = 6
        invincible = 0f; attackTimer = 0f; timeAcc = 0f; elapsed = 0f
        joyActive = false; joyX = 0f; joyY = 0f
        attackHeld = false; aimTarget = null
        skillCd = 0f; shieldTime = 0f; shieldLeft = 0f
        chill = 0f
        timeScale = 1f; timeScaleTimer = 0f; shake = 0f
        hitStop = 0f; playerFlash = 0f; velX = 0f; velY = 0f; activeSlash = null
        killStreak = 0; killStreakTimer = 0f
        phase = Phase.IDLE
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        events.clear()
        if (phase != Phase.PLAYING) return
        val dt0 = dtRaw.coerceIn(0f, 0.05f)
        if (hitStop > 0f) hitStop -= dt0
        // 连杀窗口
        if (killStreakTimer > 0f) { killStreakTimer -= dt0; if (killStreakTimer <= 0f) killStreak = 0 }
        // 命中停顿：世界以 5% 速度推进（打击感核心）
        val dt = dt0 * if (hitStop > 0f) 0.05f else 1f
        // 锁定：可视范围内且存活（同房间无遮挡物，按同房间处理）的最近敌人
        aimTarget = nearestEnemy(460f)
        elapsed += dt
        if (invincible > 0f) invincible -= dt
        if (playerFlash > 0f) playerFlash -= dt
        if (chill > 0f) chill -= dt
        if (skillCd > 0f) skillCd -= dt
        if (shieldTime > 0f) shieldTime -= dt
        if (timeScaleTimer > 0f) { timeScaleTimer -= dt; if (timeScaleTimer <= 0f) timeScale = 1f }
        if (shake > 0f) shake -= dt

        // 玩家移动：摇杆 → 速度直接映射（往哪是哪、松手即停）；挥砍期间移速减半
        var jx = if (joyActive) joyX else 0f
        var jy = if (joyActive) joyY else 0f
        val inLen = hypot(jx, jy)
        if (inLen > 1f) { jx /= inLen; jy /= inLen }
        val slowK = if (activeSlash != null) 0.5f else 1f
        val chillK = if (chill > 0f) 0.72f else 1f   // 精英「霜环」：靠近被冻慢
        velX = jx * speed * slowK * chillK
        velY = jy * speed * slowK * chillK
        val vx = hypot(velX, velY)
        moving = vx > 20f
        if (inLen > 0.01f) facing = atan2(velY, velX)   // facing 只跟随真实输入；惯性滑行不抢朝向（faceTo 锁敌不被覆盖）
        // 锁定目标存在 → 面向敌人（未锁定保持移动方向，可空A）
        aimTarget?.let { at -> facing = atan2(at.y - py, at.x - px) }
        if (moving) {
            walkPhase += dt * 10f
            val wall = canPass
            val stepX = velX * dt
            val stepY = velY * dt
            if (wall == null) { px += stepX; py += stepY }
            else {
                if (wall(px + stepX, py)) px += stepX
                if (wall(px, py + stepY)) py += stepY
            }
        }

        // 攻击 CD 恒走表（武器内置 CD）：点按=CD 好了立即出手；长按=每完成 CD 自动下一发
        attackTimer -= dt
        if (attackTimer < -attackInterval) attackTimer = -attackInterval   // 防久置漂移，点按永远即时
        if ((attackHeld || autoAttack) && attackTimer <= 0f) {
            if (weapon.attack(this)) attackTimer = attackInterval
        }
        // 近战挥砍推进：前摇结束瞬间判定，播完后摇收刀
        activeSlash?.let { s ->
            s.timer += dt
            if (!s.fired && s.timer >= s.windup) {
                s.fired = true
                meleeArc(s.range, Math.toRadians(s.arcDeg.toDouble()).toFloat(), s.dmg, Element.PHYSICAL)
            }
            if (s.timer >= s.windup + 0.25f) activeSlash = null
        }

        // 延迟刷怪（进房分波）
        tickEnemies(dt)
        tickBullets(dt)
        tickOrbs(dt)
        tickPendingSpawns(dt)

        // 清理：非死亡动画的尸体立即回收；死亡动画播完（deathTimer≤0）再回收
        enemies.removeAll { !it.alive && (!it.dying || it.deathTimer <= 0f) }
        bullets.removeAll { !it.alive }
        orbs.removeAll { !it.alive }
    }

    private fun tickPendingSpawns(dt: Float) {
        if (pendingSpawns.isEmpty()) return
        val it = pendingSpawns.listIterator()
        while (it.hasNext()) {
            val p = it.next()
            val left = p.first - dt
            if (left <= 0f) { enemies.add(p.second); it.remove() } else it.set(left to p.second)
        }
    }

    private fun tickEnemies(dt: Float) {
        if ((elapsed * 5).toInt() != lastEnemyLog) {
            lastEnemyLog = (elapsed * 5).toInt()
            enemies.firstOrNull()?.let {
                android.util.Log.d("ENGN2", "e0=%.0f,%.0f hp=%.0f sp=%.0f dt=%.4f alive=%s px=%.0f"
                    .format(it.x, it.y, it.hp, it.speed, dt, it.alive, px))
            }
        }
        // 索引循环：Boss召唤/精英分裂会在迭代中向 enemies 追加（indices 固定，新增者下一帧处理，免疫 CME）
        for (ei in enemies.indices) {
            val e = enemies[ei]
            if (e.dying) {
                e.deathTimer -= dt
                continue
            }
            if (!e.alive) continue
            if (e.dormant) continue   // 待机敌人：不 AI/不接触伤害（渲染层照常显示）
            if (e.hitFlash > 0f) e.hitFlash -= dt
            // 元素状态
            ElementSystem.tickStatus(e, dt, this)
            // 精英「再生」：每秒回 3% 最大生命（鼓励集火）
            if (e.affix == Affix.REGEN && e.hp < e.maxHp) e.hp = minOf(e.hp + e.maxHp * 0.03f * dt, e.maxHp)

            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            // 精英「霜环」：靠近时刷新玩家的冻慢时间
            if (e.affix == Affix.FROST && d < 230f) chill = maxOf(chill, 0.2f)
            if (d < 1f) continue
            var sp = e.speed * timeScale
            // 冰减速 / 冻结
            if (e.frozen > 0f) sp = 0f
            else if (e.slowStacks > 0) sp *= (1f - 0.22f * e.slowStacks).coerceAtLeast(0.3f)

            if (e.stun > 0f) { e.stun -= dt } else when (e.kind) {
                EnemyKind.SLIME -> {
                    // 弹跳接近 + 蓄力压扁→短距突进（前摇 0.4s 可预判）
                    if (e.atkState == 0) {
                        e.aiTimer -= dt
                        val hopping = (e.aiTimer % 1.1f) < 0.55f
                        if (hopping && sp > 0f) {
                            e.x += dx / d * sp * 1.6f * dt
                            e.y += dy / d * sp * 1.6f * dt
                        }
                        e.atkCd -= dt
                        if (e.atkCd <= 0f && d < 260f && d > 46f) {
                            e.atkState = 1; e.atkTimer = 0.4f; e.atkCd = 3f
                            e.atkDirX = dx / d; e.atkDirY = dy / d   // 前摇开始锁定方向：侧移即可躲
                        }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) { e.atkState = 2; e.atkTimer = 0.28f }
                    } else {
                        e.x += e.atkDirX * sp * 3.4f * dt
                        e.y += e.atkDirY * sp * 3.4f * dt
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) e.atkState = 0
                    }
                }
                EnemyKind.BAT -> {
                    // 绕飞接近 → 悬停拉高 → 俯冲穿过玩家位置
                    if (e.atkState == 0) {
                        e.aiTimer += dt
                        val wobble = sin(e.aiTimer * 6f + e.wobbleSeed) * 0.7f
                        val mx = dx / d + (-dy / d) * wobble
                        val my = dy / d + (dx / d) * wobble
                        val ml = hypot(mx, my)
                        e.x += mx / ml * sp * 1.5f * dt
                        e.y += my / ml * sp * 1.5f * dt
                        e.atkCd -= dt
                        if (e.atkCd <= 0f && d > 120f && d < 420f) {
                            e.atkState = 1; e.atkTimer = 0.35f; e.atkCd = 2.8f
                            e.atkDirX = dx / d; e.atkDirY = dy / d   // 前摇开始锁定俯冲方向
                        }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) { e.atkState = 2; e.atkTimer = 0.45f }
                    } else {
                        e.x += e.atkDirX * sp * 4f * dt
                        e.y += e.atkDirY * sp * 4f * dt
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) e.atkState = 0
                    }
                }
                EnemyKind.CASTER -> {
                    // 远程：保持 260~420 距离；蓄力 0.5s → 三连扇形弹幕
                    if (e.atkState == 0) {
                        e.aiTimer -= dt
                        if (d > 420f) { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                        else if (d < 260f) { e.x -= dx / d * sp * 0.8f * dt; e.y -= dy / d * sp * 0.8f * dt }
                        if (e.aiTimer <= 0f && e.frozen <= 0f && d < 520f) { e.atkState = 1; e.atkTimer = 0.5f }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) {
                            if (bullets.size < MAX_BULLETS - 4) {
                                val base = atan2(dy, dx)
                                for (i in -1..1) {
                                    val a = base + i * 0.21f
                                    bullets.add(Bullet(e.x, e.y, cos(a) * 230f, sin(a) * 230f, 6f, e.dmg, null, fromEnemy = true, life = 3f))
                                }
                            }
                            e.aiTimer = 2.4f
                            e.atkState = 0
                        }
                    }
                }
                EnemyKind.SKELETON -> {
                    // 接近 → 近身抬臂 0.3s → 横扫（范围 95 内判定）
                    if (e.atkState == 0) {
                        if (d > 70f) { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                        else {
                            e.atkCd -= dt
                            if (e.atkCd <= 0f) { e.atkState = 1; e.atkTimer = 0.3f }
                        }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) {
                            if (d < 95f) hurtPlayer(e.dmg)
                            events.add(FxEvent(e.x, e.y, "", false, null, 8))   // 挥砍白弧
                            e.atkState = 2; e.atkTimer = 0.5f; e.atkCd = 1.6f
                        }
                    } else {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) e.atkState = 0
                    }
                }
                EnemyKind.BONE_ARCHER -> {
                    // 远程单发：保持 380~560 距离；前摇 0.45s 锁定方向（侧移可躲）→ 快箭
                    if (e.atkState == 0) {
                        e.aiTimer -= dt
                        if (d > 560f) { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                        else if (d < 380f) { e.x -= dx / d * sp * 0.9f * dt; e.y -= dy / d * sp * 0.9f * dt }
                        e.atkCd -= dt
                        if (e.atkCd <= 0f && e.frozen <= 0f && d < 640f) {
                            e.atkState = 1; e.atkTimer = 0.45f; e.atkCd = 2.6f
                            e.atkDirX = dx / d; e.atkDirY = dy / d   // 前摇开始锁定方向
                        }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) {
                            if (bullets.size < MAX_BULLETS - 2) {
                                bullets.add(Bullet(e.x, e.y, e.atkDirX * 430f, e.atkDirY * 430f, 5f, e.dmg, null, fromEnemy = true, life = 2.4f))
                            }
                            e.atkState = 0
                        }
                    }
                }
                EnemyKind.SHIELD_GUARD -> {
                    // 重装盾卫：慢速逼近 → 近身蓄力 0.5s → 盾击（120 内判定，正面伤害被格挡见 hitEnemy）
                    if (e.atkState == 0) {
                        if (d > 75f) { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                        else {
                            e.atkCd -= dt
                            if (e.atkCd <= 0f) { e.atkState = 1; e.atkTimer = 0.5f }
                        }
                    } else if (e.atkState == 1) {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) {
                            if (d < 120f) hurtPlayer(e.dmg)
                            events.add(FxEvent(e.x, e.y, "", false, null, 8))
                            e.atkState = 2; e.atkTimer = 0.6f; e.atkCd = 2.4f
                        }
                    } else {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) e.atkState = 0
                    }
                }
                EnemyKind.BOOM_SLIME -> {
                    // 自爆史莱姆：高速贴近 → 130 内起爆倒计时 0.6s（闪红预警）→ 爆炸 AoE 后消失
                    if (e.atkState == 0) {
                        e.x += dx / d * sp * 1.35f * dt
                        e.y += dy / d * sp * 1.35f * dt
                        if (d < 130f) { e.atkState = 1; e.atkTimer = 0.6f }
                    } else {
                        e.atkTimer -= dt
                        if (e.atkTimer <= 0f) {
                            if (d < 170f) hurtPlayer(e.dmg * 1.6f)
                            events.add(FxEvent(e.x, e.y, "", false, Element.FIRE, 1, tint = 0xFFFF7043.toInt()))
                            shake = maxOf(shake, 5f)
                            killEnemy(e)
                        }
                    }
                }
                else -> {   // DUMMY：直追（Boss 走专属 AI）
                    if (e.bossFloor > 0) bossAI(e, dx, dy, d, sp, dt)
                    else { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                }
            }
            // 击退推进（衰减到 0 停）
            if (e.kbT > 0f) {
                e.x += e.kbVX * dt; e.y += e.kbVY * dt
                e.kbT -= dt
            } else { e.kbVX = 0f; e.kbVY = 0f }
            // 不沉入玩家圆心：贴到接触环即止（环径收在接触判定内 2px，贴脸必掉血）
            val minD = e.r + playerR - 2f
            if (d < minD) {
                e.x = px - dx / d * minD
                e.y = py - dy / d * minD
            }
            // 场地边界
            if (arenaRight > arenaLeft) {
                e.x = e.x.coerceIn(arenaLeft, arenaRight)
                e.y = e.y.coerceIn(arenaTop, arenaBottom)
            }
            // 接触伤害（无敌帧）
            if (invincible <= 0f) {
                val rr = e.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    hurtPlayer(e.dmg)
                }
            }
        }
    }

    private fun tickBullets(dt: Float) {
        for (bi in bullets.indices) {
            val b = bullets[bi]
            if (!b.alive) continue
            b.x += b.dx * dt; b.y += b.dy * dt
            b.age += dt
            if (b.age >= b.life) { b.alive = false; continue }

            if (b.fromEnemy) {
                val dx = px - b.x; val dy = py - b.y
                val rr = b.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    b.alive = false
                    hurtPlayer(b.dmg)
                }
                continue
            }
            // 玩家子弹 × 敌人（索引循环：命中触发的 killEnemy→分裂 只进 spawnNow，防迭代中变更）
            for (ei in enemies.indices) {
                val e = enemies[ei]
                if (!e.alive || e.dormant) continue
                val dx = e.x - b.x; val dy = e.y - b.y
                val rr = e.r + b.r
                if (dx * dx + dy * dy <= rr * rr) {
                    val bl = hypot(b.dx, b.dy)
                    val knx = if (bl > 1f) b.dx / bl else 0f
                    val kny = if (bl > 1f) b.dy / bl else 0f
                    hitEnemy(e, b.dmg, b.element, knx, kny)
                    if (b.pierce > 0) b.pierce-- else { b.alive = false }
                    break
                }
            }
        }
    }

    private fun tickOrbs(dt: Float) {
        for (oi in orbs.indices) {
            val o = orbs[oi]
            if (!o.alive) continue
            // 掉落弹出初速（阻尼衰减），并夹在场内防止卡进墙缝
            if (o.vx != 0f || o.vy != 0f) {
                o.x += o.vx * dt; o.y += o.vy * dt
                val k = kotlin.math.exp(-6f * dt)
                o.vx *= k; o.vy *= k
                if (o.vx * o.vx + o.vy * o.vy < 100f) { o.vx = 0f; o.vy = 0f }
            }
            if (arenaRight > arenaLeft) {
                o.x = o.x.coerceIn(arenaLeft + 8f, arenaRight - 8f)
                o.y = o.y.coerceIn(arenaTop + 8f, arenaBottom - 8f)
            }
            val dx = px - o.x; val dy = py - o.y
            val d2 = dx * dx + dy * dy
            val rr = playerR + 6f
            if (d2 <= rr * rr) {
                o.alive = false
                gainXp(o.value)
                events.add(FxEvent(o.x, o.y, "+" + o.value, false, null, 2))
                if (phase != Phase.PLAYING) return
            } else {
                if (!o.magnet && d2 <= pickupRange * pickupRange) o.magnet = true
                if (o.magnet) {
                    val d = hypot(dx, dy)
                    if (d > 1f) { o.x += dx / d * 520f * dt; o.y += dy / d * 520f * dt }
                }
            }
        }
    }

    // ---------- 伤害与状态 ----------
    fun hitEnemy(e: Enemy, baseDmg: Float, element: Element?, kx: Float = 0f, ky: Float = 0f) {
        var dmg = baseDmg
        val crit = Random.nextFloat() < critChance
        if (crit) dmg *= 2f
        if (element != null) dmg *= elemPower
        // 盾卫正面格挡：攻击来向与「盾卫→玩家」相反（即从玩家侧打来）→ 伤害 ×0.25，绕后全额
        if (e.kind == EnemyKind.SHIELD_GUARD && e.bossFloor == 0 && e.alive) {
            val fx = px - e.x; val fy = py - e.y
            val fl = hypot(fx, fy).coerceAtLeast(1f)
            val dot = if (kx == 0f && ky == 0f) -1f else (kx * fx + ky * fy) / fl
            if (dot < -0.45f) {
                dmg *= 0.25f
                events.add(FxEvent(e.x, e.y - e.r - 6f, "格挡", false, null, 0))
            }
        }
        if (e.shieldHp > 0f) {
            val absorbed = minOf(e.shieldHp, dmg)
            e.shieldHp -= absorbed
            dmg -= absorbed
        }
        e.hp -= dmg
        // 精英「吸血」：命中回血
        if (e.affix == Affix.VAMPIRE && dmg > 0f) e.hp = minOf(e.hp + dmg * 0.2f, e.maxHp)
        if (e.reflect && e.bossFloor == 0 && dmg > 0f) hurtPlayer(dmg * 0.15f)
        e.hitFlash = 0.12f
        // 打击感：击退（Boss/精英减半）+ 硬直 + 命中停顿（暴击更长）
        if (kx != 0f || ky != 0f) {
            val power = if (e.bossFloor > 0 || e.elite) 130f else 260f
            e.kbVX = kx * power; e.kbVY = ky * power
            e.kbT = 0.12f
        }
        e.stun = maxOf(e.stun, if (crit) 0.15f else 0.1f)
        hitStop = maxOf(hitStop, if (crit) 0.06f else 0.025f)
        events.add(FxEvent(e.x, e.y - e.r, "${dmg.toInt()}", crit, element, 0))
        if (element != null) ElementSystem.onHit(e, element, this)
        if (e.hp <= 0f && e.alive) killEnemy(e)
    }

    /** 击杀：逻辑立即结算（掉落/计数/回调），尸体进入 0.3s 死亡动画由渲染淡出 */
    fun killEnemy(e: Enemy) {
        if (e.bossFloor > 0) android.util.Log.d("DGROOM", "BOSS KILLED at %.0f,%.0f hp=%.0f".format(e.x, e.y, e.hp))
        e.alive = false
        e.dying = true
        e.deathTimer = 0.3f
        val tint = when (e.kind) {
            EnemyKind.SLIME -> 0xFF66BB6A
            EnemyKind.SKELETON -> 0xFFECEFF1
            EnemyKind.BAT -> 0xFF7E57C2
            EnemyKind.CASTER -> 0xFFEF5350
            EnemyKind.BONE_ARCHER -> 0xFFECEFF1
            EnemyKind.SHIELD_GUARD -> 0xFF78909C
            EnemyKind.BOOM_SLIME -> 0xFFFF7043
            else -> if (e.bossFloor > 0) 0xFFFFD54F else 0xFFB0885A
        }.toInt()
        events.add(FxEvent(e.x, e.y, "", false, null, 1, tint = tint))
        killStreak++; killStreakTimer = 3f
        if (orbs.size < MAX_ORBS) {
            val a = Random.nextFloat() * 6.283f
            val sp = 100f + Random.nextFloat() * 160f
            val o = Orb(e.x, e.y, e.xpValue)
            o.vx = cos(a) * sp; o.vy = sin(a) * sp
            orbs.add(o)
        }
        // 精英「分裂」词缀：死亡分裂成两只小怪
        if (e.affix == Affix.SPLIT && e.r > 12f && enemies.size + spawnNow.size < MAX_ENEMIES) {
            repeat(2) { idx ->
                spawnNow.add(Enemy(e.x + (idx * 2 - 1) * 20f, e.y, e.r * 0.55f, e.maxHp * 0.25f,
                    e.maxHp * 0.25f, e.speed * 1.2f, e.dmg * 0.5f, e.kind, elite = false, xpValue = 1))
            }
        }
        onEnemyKilled?.invoke(e)
    }

    fun hurtPlayer(raw: Float) {
        if (com.brainquest.game.util.DebugFlags.god) { hp = maxHp; return }   // 上帝模式：不掉血且回满（防 bot 低血撤退模式卡死）
        var dmg = raw
        when (passiveId) {
            "knight" -> dmg *= 0.85f                          // 格挡
            "ranger" -> if (Random.nextFloat() < 0.2f) {      // 闪避
                events.add(FxEvent(px, py - 30f, "闪避", false, null, 0))
                invincible = invincibleSec
                return
            }
        }
        if (shieldTime > 0f && shieldLeft > 0f) {
            val absorbed = minOf(shieldLeft, dmg)
            shieldLeft -= absorbed
            dmg -= absorbed
            if (dmg <= 0f) { events.add(FxEvent(px, py - 30f, "格挡", false, null, 0)); return }
        }
        dmg = (dmg.toInt().coerceAtLeast(1)).toFloat()
        hp -= dmg.toInt()
        invincible = invincibleSec
        playerFlash = 0.15f
        killStreak = 0   // 受伤断连击
        events.add(FxEvent(px, py - 30f, "-${dmg.toInt()}", false, null, 0))
        if (hp <= 0) { hp = 0; phase = Phase.GAMEOVER }
    }

    fun heal(n: Int) { if (phase != Phase.GAMEOVER) hp = min(hp + n, maxHp) }

    // ---------- 装备增量（换装差量应用） ----------
    private var lastEnemyLog = -1
    fun setMaxHp(n: Int) { maxHp = n.coerceAtLeast(1); if (hp > maxHp) hp = maxHp }
    /** DEBUG 兜底：自动驾驶用（升级空队列时恢复探索） */
    fun forcePlaying() { phase = Phase.PLAYING }
    /** 武器发起近战挥砍（前摇→判定→后摇由 tick 推进）；已有挥砍进行中则忽略 */
    fun beginSlash(s: Slash) { if (activeSlash == null) activeSlash = s }
    /** 原地转向（不移动；自动驾驶近战站定输出用——facing 平时只随移动更新） */
    fun faceTo(x: Float, y: Float) { facing = atan2(y - py, x - px) }
    fun addShake(v: Float) { shake = maxOf(shake, v) }
    fun buffAttack(delta: Int) { attack = (attack + delta).coerceAtLeast(1) }
    fun buffSpeed(delta: Float) { speed = (speed + delta).coerceAtLeast(60f) }
    fun buffCrit(delta: Float) { critChance = (critChance + delta).coerceIn(0f, 0.8f) }
    fun buffPickupMult(mult: Float) { pickupRange = (pickupRange * mult).coerceIn(40f, 600f) }
    fun buffElem(delta: Float) { elemPower += delta }

    private fun gainXp(v: Int) {
        xp += v
        if (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 4 + (level - 1) * 3
            pendingUpgrades = rollUpgrades()
            phase = Phase.LEVELUP
        }
    }

    var pendingUpgrades: List<Upgrade> = emptyList(); private set

    private fun rollUpgrades(): List<Upgrade> = listOf(
        Upgrade("atk", "⚔️ 攻击力", "伤害 +15%"),
        Upgrade("aspd", "⚡ 攻速", "攻击间隔 −15%"),
        Upgrade("spd", "👟 移速", "移动速度 +10%"),
        Upgrade("hp", "❤️ 生命", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "🧲 拾取", "拾取范围 +30%"),
        Upgrade("elem", "🔥 元素", "元素伤害 +25%"),
    ).shuffled(Random).take(3)

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        when (id) {
            "atk" -> attack = (attack * 1.15f).toInt().coerceAtLeast(attack + 1)
            "aspd" -> attackInterval *= 0.90f
            "spd" -> speed *= 1.10f
            "hp" -> { maxHp += 25; hp = min(hp + 25, maxHp) }
            "pickup" -> pickupRange *= 1.30f
            "elem" -> elemPower *= 1.18f
        }
        pendingUpgrades = emptyList()
        phase = Phase.PLAYING
    }

    // ---------- 刷怪（供地牢按房型布置） ----------
    fun spawnLater(delaySec: Float, e: Enemy) = pendingSpawns.add(delaySec to e)

    /** 还有未落地的延迟刷怪（清房判定需等待） */
    fun hasPendingSpawns() = pendingSpawns.isNotEmpty()

    /** 射出玩家子弹（武器用） */
    fun fire(x: Float, y: Float, dirX: Float, dirY: Float, speed: Float, r: Float, dmg: Float, element: Element?, pierce: Int = 0) {
        if (bullets.size < MAX_BULLETS) {
            bullets.add(Bullet(x, y, dirX * speed, dirY * speed, r, dmg, element, fromEnemy = false, pierce = pierce))
            events.add(FxEvent(x, y, "", false, element, 9))   // 枪口火光
        }
    }

    /** Boss 专属 AI：每层不同机制，血量 <30% 进入狂暴（阶段 2） */
    private fun bossAI(e: Enemy, dx: Float, dy: Float, d: Float, sp: Float, dt: Float) {
        val rage = e.phase >= 2
        val mult = if (rage) 1.5f else 1f
        if (e.phase == 1 && e.hp < e.maxHp * 0.3f) {
            e.phase = 2
            shake = 12f
            events.add(FxEvent(e.x, e.y - e.r - 20f, "狂暴！", true, null, 0))
        }
        if (d > 1f && e.frozen <= 0f) {
            e.x += dx / d * sp * mult * dt
            e.y += dy / d * sp * mult * dt
        }
        e.specialTimer -= dt * mult
        if (e.specialTimer > 0f) return
        val floor = e.bossFloor
        when (((floor - 1) % 4) + 1) {
            1 -> {   // 冲刺 + 召唤小怪
                e.specialTimer = if (rage) 3f else 5f
                if (d > 120f) { e.x += dx / d * 300f; e.y += dy / d * 300f; shake = 6f }
                repeat(2) { if (enemies.size < MAX_ENEMIES) spawnMinion(e, EnemyKind.SLIME) }
            }
            2 -> {   // 弹幕环 + 瞬移
                e.specialTimer = if (rage) 2.5f else 4f
                repeat(8) { i ->
                    val ang = i * 0.785f
                    if (bullets.size < MAX_BULLETS) bullets.add(Bullet(e.x, e.y, cos(ang) * 200f, sin(ang) * 200f, 6f, e.dmg * 0.6f, null, fromEnemy = true, life = 3f))
                }
                if (d > 200f) { e.x = px + (Random.nextFloat() - 0.5f) * 300f; e.y = py + (Random.nextFloat() - 0.5f) * 300f }
            }
            3 -> {   // 护盾 + 反伤
                e.specialTimer = if (rage) 4f else 6f
                e.shieldHp = e.maxHp * 0.15f
                e.reflect = true
            }
            4 -> {   // 分身 + 环形弹幕
                e.specialTimer = if (rage) 3f else 5f
                repeat(2) { if (enemies.size < MAX_ENEMIES) spawnMinion(e, EnemyKind.BAT) }
                repeat(6) { i ->
                    val ang = i * 1.047f + elapsed
                    if (bullets.size < MAX_BULLETS) bullets.add(Bullet(e.x, e.y, cos(ang) * 160f, sin(ang) * 160f, 7f, e.dmg * 0.5f, null, fromEnemy = true, life = 2.5f))
                }
            }
        }
        // 第 5 层 Boss 狂暴期：额外全屏弹幕
        if (floor == 5 && rage) {
            repeat(12) { i ->
                val ang = i * 0.524f
                if (bullets.size < MAX_BULLETS) bullets.add(Bullet(e.x, e.y, cos(ang) * 180f, sin(ang) * 180f, 6f, e.dmg * 0.5f, null, fromEnemy = true, life = 3f))
            }
        }
    }

    private fun spawnMinion(near: Enemy, kind: EnemyKind) {
        val ang = Random.nextFloat() * 6.283f
        enemies.add(Enemy(near.x + cos(ang) * 60f, near.y + sin(ang) * 60f, 13f,
            18f, 18f, 80f, 6f, kind, xpValue = 1))
    }

    /** 最近敌人（武器索敌用） */
    fun nearestEnemy(maxDist: Float = 460f): Enemy? {
        var best: Enemy? = null
        var bestD = maxDist * maxDist
        for (e in enemies) {
            if (!e.alive || e.dormant) continue
            val dx = e.x - px; val dy = e.y - py
            val d2 = dx * dx + dy * dy
            if (d2 <= bestD) { bestD = d2; best = e }
        }
        return best
    }

    /** 链电/爆炸等范围查询（ElementSystem 用） */
    fun queryNeighbors(from: Enemy, radius: Float, exclude: Set<Enemy>): List<Enemy> {
        val out = ArrayList<Enemy>(4)
        val r2 = radius * radius
        for (e in enemies) {
            if (!e.alive || e in exclude) continue
            val dx = e.x - from.x; val dy = e.y - from.y
            if (dx * dx + dy * dy <= r2) out.add(e)
        }
        return out
    }

    /** 圆形 AOE（火球爆炸等，武器用） */
    fun aoe(x: Float, y: Float, radius: Float, dmg: Float, element: Element?) {
        val r2 = radius * radius
        for (e in enemies) {
            if (!e.alive || e.dormant) continue
            val dx = e.x - x; val dy = e.y - y
            if (dx * dx + dy * dy <= r2) {
                val d = hypot(dx, dy)
                hitEnemy(e, dmg, element, if (d > 1f) dx / d else 0f, if (d > 1f) dy / d else 0f)
            }
        }
    }

    /** 近战扇形判定（挥砍用）：朝向 facing、范围 range、张角 arc */
    fun meleeArc(range: Float, arcRad: Float, dmg: Float, element: Element?) {
        var hits = 0
        for (e in enemies) {
            if (!e.alive || e.dormant) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy)
            if (d > range + e.r) continue
            val ang = abs(angleDiff(atan2(dy, dx), facing))
            if (ang <= arcRad / 2f) {
                val knx = if (d > 1f) dx / d else 0f
                val kny = if (d > 1f) dy / d else 0f
                hitEnemy(e, dmg, element, knx, kny); hits++
            }
        }
        if (android.os.SystemClock.elapsedRealtime() - lastSlashLog > 2000) {
            lastSlashLog = android.os.SystemClock.elapsedRealtime()
            val detail = enemies.filter { it.alive }.take(4).joinToString(";") { e ->
                val dx = e.x - px; val dy = e.y - py
                "%.0f@%.2f".format(hypot(dx, dy), atan2(dy, dx))
            }
            android.util.Log.d("DGAIP", "slash facing=%.2f hits=%d px=%.0f,py=%.0f arena=(%.0f,%.0f,%.0f,%.0f) eng=%s enemies=[%s]"
                .format(facing, hits, px, py, arenaLeft, arenaTop, arenaRight, arenaBottom,
                    Integer.toHexString(System.identityHashCode(this)), detail))
        }
    }
    private var lastSlashLog = 0L

    private fun angleDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return d
    }

    companion object {
        const val MAX_BULLETS = 300
        const val MAX_ENEMIES = 200
        const val MAX_ORBS = 100
    }
}

/** 武器：产生攻击动作（弹道/近战/AOE），引擎负责飞行与碰撞 */
interface Weapon {
    /** 尝试攻击；返回是否真的出手（出手才重置攻击计时） */
    fun attack(engine: CombatEngine): Boolean

    /** 近战挥砍：前摇后对朝向扇形判定（剑士）；手动模式空 A 也出手（挥空不空手） */
    class MeleeSlash(val range: Float = 95f, val arcDeg: Float = 100f, val windup: Float = 0.1f) : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            if (engine.activeSlash != null) return false   // 上一刀没收完不连挥
            if (engine.autoAttack && engine.nearestEnemy(range + 30f) == null) return false   // 自动模式有目标才出刀
            engine.beginSlash(CombatEngine.Slash(0f, windup, range, arcDeg, engine.attack.toFloat()))
            return true
        }
    }

    /** 火球：单发火元素弹（法师）；手动模式朝 facing（空A） */
    class Fireball : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            val dx: Float; val dy: Float
            val t = engine.nearestEnemy()
            if (engine.autoAttack && t != null) { dx = t.x - engine.px; dy = t.y - engine.py }
            else { dx = cos(engine.facing); dy = sin(engine.facing) }
            val d = hypot(dx, dy)
            if (d < 1f) return false
            engine.fire(engine.px, engine.py, dx / d, dy / d, 380f, 7f, engine.attack.toFloat(), Element.FIRE)
            return true
        }
    }

    /** 连射：无元素快速直线弹（游侠）；手动模式朝 facing（空A） */
    class RapidShot : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            val dx: Float; val dy: Float
            val t = engine.nearestEnemy()
            if (engine.autoAttack && t != null) { dx = t.x - engine.px; dy = t.y - engine.py }
            else { dx = cos(engine.facing); dy = sin(engine.facing) }
            val d = hypot(dx, dy)
            if (d < 1f) return false
            engine.fire(engine.px, engine.py, dx / d, dy / d, 460f, 5f, engine.attack.toFloat(), Element.PHYSICAL)
            return true
        }
    }
}
