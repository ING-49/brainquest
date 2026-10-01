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
        val speed: Float,
        val dmg: Float,
        val kind: EnemyKind,
        val elite: Boolean = false,
        val xpValue: Int = 1,
    ) {
        var alive = true
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
    class Slash(var timer: Float, val windup: Float, val range: Float, val arcDeg: Float, var dmg: Float, val fired: Boolean = false)

    class Orb(var x: Float, var y: Float, val value: Int) {
        var alive = true
        var magnet = false
    }

    /** 帧事件（伤害飘字/粒子，渲染层消费） */
    data class FxEvent(val x: Float, val y: Float, val text: String, val crit: Boolean, val element: Element?, val kind: Int) // kind 0=伤害 1=死亡 2=拾取

    /** 强化选项 */
    data class Upgrade(val id: String, val name: String, val desc: String)

    // ---------- 玩家属性 ----------
    var px = 0f; var py = 0f
    var hp = 100; private set
    var maxHp = 100; private set
    var attack = 10; private set
    var attackInterval = 0.8f; private set
    var speed = 220f; private set
    var pickupRange = 100f; private set
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
    var attackTimer = 0f; private set
    private var timeAcc = 0f
    var elapsed = 0f; private set

    // ---------- 输入 ----------
    var joyActive = false
    var joyX = 0f; var joyY = 0f

    // ---------- 容器 ----------
    val enemies = ArrayList<Enemy>(64)
    val bullets = ArrayList<Bullet>(64)
    val orbs = ArrayList<Orb>(64)
    val events = ArrayList<FxEvent>(16)
    private val pendingSpawns = ArrayList<Pair<Float, Enemy>>(8)   // 延迟刷怪（进房分波）

    /** 场地边界（敌人不出当前房间）；null = 不限制 */
    var arenaLeft = 0f; var arenaTop = 0f
    var arenaRight = 0f; var arenaBottom = 0f

    /** 职业被动 id（knight 格挡 / mage 击杀回血 / ranger 闪避），null = 无 */
    var passiveId: String? = null

    /** 当前武器 */
    var weapon: Weapon = Weapon.RapidShot()

    /** 墙体查询（地牢注入：开放矩形并集）；null = 自由移动。轴分离应用。 */
    var canPass: ((Float, Float) -> Boolean)? = null

    /** 击杀回调（地牢用于统计/职业被动/清房判定） */
    var onEnemyKilled: ((Enemy) -> Unit)? = null

    // ---------- 常量 ----------
    val playerR = 17f
    private val invincibleSec = 0.8f

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
        phase = Phase.IDLE
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        events.clear()
        if (phase != Phase.PLAYING) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        elapsed += dt
        if (invincible > 0f) invincible -= dt

        // 玩家移动
        val jx = if (joyActive) joyX else 0f
        val jy = if (joyActive) joyY else 0f
        moving = jx != 0f || jy != 0f
        if (moving) {
            val len = hypot(jx, jy)
            val nx = jx / len; val ny = jy / len
            facing = atan2(ny, nx)
            walkPhase += dt * 10f
            val wall = canPass
            val stepX = nx * speed * dt
            val stepY = ny * speed * dt
            if (wall == null) { px += stepX; py += stepY }
            else {
                if (wall(px + stepX, py)) px += stepX
                if (wall(px, py + stepY)) py += stepY
            }
        }

        // 自动攻击（武器产生弹道/近战判定）
        attackTimer -= dt
        if (attackTimer <= 0f) {
            if (weapon.attack(this)) attackTimer = attackInterval
        }

        // 延迟刷怪（进房分波）
        tickEnemies(dt)
        tickBullets(dt)
        tickOrbs(dt)
        tickPendingSpawns(dt)

        // 清理
        enemies.removeAll { !it.alive }
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
        for (e in enemies) {
            if (!e.alive) continue
            if (e.hitFlash > 0f) e.hitFlash -= dt
            // 元素状态
            ElementSystem.tickStatus(e, dt, this)

            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d < 1f) continue
            var sp = e.speed
            // 冰减速 / 冻结
            if (e.frozen > 0f) sp = 0f
            else if (e.slowStacks > 0) sp *= (1f - 0.22f * e.slowStacks).coerceAtLeast(0.3f)

            when (e.kind) {
                EnemyKind.SLIME -> {
                    // 弹跳：跳-停节奏
                    e.aiTimer -= dt
                    val hopping = (e.aiTimer % 1.1f) < 0.55f
                    if (hopping && sp > 0f) {
                        e.x += dx / d * sp * 1.6f * dt
                        e.y += dy / d * sp * 1.6f * dt
                    }
                }
                EnemyKind.BAT -> {
                    // 高速乱飞：朝向玩家 + 正弦横向摆动
                    e.aiTimer += dt
                    val wobble = sin(e.aiTimer * 6f + e.wobbleSeed) * 0.7f
                    val mx = dx / d + (-dy / d) * wobble
                    val my = dy / d + (dx / d) * wobble
                    val ml = hypot(mx, my)
                    e.x += mx / ml * sp * 1.5f * dt
                    e.y += my / ml * sp * 1.5f * dt
                }
                EnemyKind.CASTER -> {
                    // 远程：保持 260~420 距离，超时放弹幕
                    e.aiTimer -= dt
                    if (d > 420f) { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                    else if (d < 260f) { e.x -= dx / d * sp * 0.8f * dt; e.y -= dy / d * sp * 0.8f * dt }
                    if (e.aiTimer <= 0f && e.frozen <= 0f) {
                        e.aiTimer = 2.4f
                        bullets.add(Bullet(e.x, e.y, dx / d * 230f, dy / d * 230f, 6f, e.dmg, null, fromEnemy = true, life = 3f))
                    }
                }
                else -> {   // SKELETON / DUMMY：直追
                    e.x += dx / d * sp * dt
                    e.y += dy / d * sp * dt
                }
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
        for (b in bullets) {
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
            // 玩家子弹 × 敌人
            for (e in enemies) {
                if (!e.alive) continue
                val dx = e.x - b.x; val dy = e.y - b.y
                val rr = e.r + b.r
                if (dx * dx + dy * dy <= rr * rr) {
                    hitEnemy(e, b.dmg, b.element)
                    if (b.pierce > 0) b.pierce-- else { b.alive = false }
                    break
                }
            }
        }
    }

    private fun tickOrbs(dt: Float) {
        for (o in orbs) {
            if (!o.alive) continue
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
    fun hitEnemy(e: Enemy, baseDmg: Float, element: Element?) {
        var dmg = baseDmg
        val crit = Random.nextFloat() < critChance
        if (crit) dmg *= 2f
        if (element != null) dmg *= elemPower
        e.hp -= dmg
        e.hitFlash = 0.12f
        events.add(FxEvent(e.x, e.y - e.r, "${dmg.toInt()}", crit, element, 0))
        if (element != null) ElementSystem.onHit(e, element, this)
        if (e.hp <= 0f && e.alive) {
            e.alive = false
            events.add(FxEvent(e.x, e.y, "", false, null, 1))
            if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, e.xpValue))
            onEnemyKilled?.invoke(e)
        }
    }

    fun hurtPlayer(raw: Float) {
        var dmg = raw
        when (passiveId) {
            "knight" -> dmg *= 0.85f                          // 格挡
            "ranger" -> if (Random.nextFloat() < 0.2f) {      // 闪避
                events.add(FxEvent(px, py - 30f, "闪避", false, null, 0))
                invincible = invincibleSec
                return
            }
        }
        dmg = (dmg.toInt().coerceAtLeast(1)).toFloat()
        hp -= dmg.toInt()
        invincible = invincibleSec
        events.add(FxEvent(px, py - 30f, "-${dmg.toInt()}", false, null, 0))
        if (hp <= 0) { hp = 0; phase = Phase.GAMEOVER }
    }

    fun heal(n: Int) { if (phase != Phase.GAMEOVER) hp = min(hp + n, maxHp) }

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
        Upgrade("atk", "⚔️ 攻击力", "伤害 +25%"),
        Upgrade("aspd", "⚡ 攻速", "攻击间隔 −15%"),
        Upgrade("spd", "👟 移速", "移动速度 +10%"),
        Upgrade("hp", "❤️ 生命", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "🧲 拾取", "拾取范围 +30%"),
        Upgrade("elem", "🔥 元素", "元素伤害 +25%"),
    ).shuffled(Random).take(3)

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        when (id) {
            "atk" -> attack = (attack * 1.25f).toInt().coerceAtLeast(attack + 1)
            "aspd" -> attackInterval *= 0.85f
            "spd" -> speed *= 1.10f
            "hp" -> { maxHp += 25; hp = min(hp + 25, maxHp) }
            "pickup" -> pickupRange *= 1.30f
            "elem" -> elemPower *= 1.25f
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
        }
    }

    /** 最近敌人（武器索敌用） */
    fun nearestEnemy(maxDist: Float = 460f): Enemy? {
        var best: Enemy? = null
        var bestD = maxDist * maxDist
        for (e in enemies) {
            if (!e.alive) continue
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
            if (!e.alive) continue
            val dx = e.x - x; val dy = e.y - y
            if (dx * dx + dy * dy <= r2) hitEnemy(e, dmg, element)
        }
    }

    /** 近战扇形判定（挥砍用）：朝向 facing、范围 range、张角 arc */
    fun meleeArc(range: Float, arcRad: Float, dmg: Float, element: Element?) {
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy)
            if (d > range + e.r) continue
            val ang = abs(angleDiff(atan2(dy, dx), facing))
            if (ang <= arcRad / 2f) hitEnemy(e, dmg, element)
        }
    }

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

    /** 近战挥砍：前摇后对朝向扇形判定（剑士） */
    class MeleeSlash(val range: Float = 95f, val arcDeg: Float = 100f, val windup: Float = 0.2f) : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            val t = engine.nearestEnemy(range + 30f) ?: return false
            // 有目标才出刀：前摇后判定（用引擎事件近似：直接立即判定 + 事件标记）
            engine.meleeArc(range, Math.toRadians(arcDeg.toDouble()).toFloat(), engine.attack.toFloat(), Element.PHYSICAL)
            return true
        }
    }

    /** 火球：单发火元素弹（法师） */
    class Fireball : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            val t = engine.nearestEnemy() ?: return false
            val dx = t.x - engine.px; val dy = t.y - engine.py
            val d = hypot(dx, dy)
            engine.fire(engine.px, engine.py, dx / d, dy / d, 380f, 7f, engine.attack.toFloat(), Element.FIRE)
            return true
        }
    }

    /** 连射：无元素快速直线弹（游侠） */
    class RapidShot : Weapon {
        override fun attack(engine: CombatEngine): Boolean {
            val t = engine.nearestEnemy() ?: return false
            val dx = t.x - engine.px; val dy = t.y - engine.py
            val d = hypot(dx, dy)
            engine.fire(engine.px, engine.py, dx / d, dy / d, 460f, 5f, engine.attack.toFloat(), Element.PHYSICAL)
            return true
        }
    }
}
