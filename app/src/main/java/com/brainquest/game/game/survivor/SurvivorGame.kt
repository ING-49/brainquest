package com.brainquest.game.game.survivor

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 迷你幸存者：俯视角弹幕求生（《吸血鬼幸存者》极简版）。
 * 纯 Kotlin 逻辑类，不依赖 Compose——游戏循环每帧调 tick(dt)，由 Canvas 重绘。
 * 坐标系：世界坐标；相机 = 玩家位置 − 视口一半（玩家居中，网格随相机平移）。
 * 速度单位为逻辑像素/秒，全部由 dt 驱动，适配任意屏幕。
 */
class SurvivorGame {

    enum class Phase { READY, PLAYING, LEVELUP, PAUSED, GAMEOVER }

    // ---------- 实体 ----------
    class Enemy(
        var x: Float, var y: Float, val r: Float, var hp: Float, val maxHp: Float, val speed: Float, val dmg: Float,
        // 批42：数学实体——普通怪=字形（数字/运算符/希腊字母），大怪=几何线框边数（3/4/5）
        val glyph: String = "1",
        val sides: Int = 0,
        // 批48：怪种类——0=数字崽(直追) 1=运算符蜂(正弦绕行) 2=几何狂(周期冲刺) 3=∑求和体(精英)
        val kind: Int = 0,
    ) {
        var alive = true
        var wobbleSeed = Random.nextFloat() * 6.2831853f   // 批48：运算符蜂的相位
        var dashCd = 0f                                    // 批48：几何狂的冲刺冷却
        var dashT = 0f                                     // 批48：冲刺剩余时间
        var hitFlash = 0f                                  // 批50：命中白闪计时
    }

    class Bullet(var x: Float, var y: Float, val dx: Float, val dy: Float) {
        var alive = true
        var life = 1.5f
    }

    class Orb(var x: Float, var y: Float, val value: Int) {
        var alive = true
        var magnet = false
    }

    /** 强化选项（升级三选一）；批45：rare=稀有强化（金色卡，效果更强） */
    data class Upgrade(val id: String, val name: String, val desc: String, val rare: Boolean = false)

    // ---------- 常量 ----------
    val playerR = 16f
    val bulletR = 5f
    val orbR = 6f
    private val bulletSpeed = 460f
    private val enemyBaseHp = 16f
    private val enemyBaseSpeed = 55f
    private val contactBaseDmg = 8f
    private val invincibleSec = 1.0f

    // ---------- 玩家与成长 ----------
    var px = 0f; private set
    var py = 0f; private set
    var hp = 100f; private set
    var maxHp = 100f; private set
    var level = 1; private set
    var xp = 0; private set
    var xpNext = 6; private set
    var attackDmg = 10f; private set
    var attackInterval = 0.8f; private set
    var moveSpeed = 220f; private set
    var pickupRange = 100f; private set

    // ---------- 对局状态 ----------
    var phase = Phase.READY; private set
    var elapsed = 0f; private set
    var kills = 0; private set
    private var invincible = 0f
    private var attackTimer = 0f
    private var spawnTimer = 0f
    private var burstTimer = 45f   // 每 45s 一波小高潮
    private var eliteTimer = 60f   // 批45：每 60s 一只 ∑ 求和体（精英）

    // ---------- 批50：反馈特效状态 ----------
    class DeathFx(val x: Float, val y: Float, val color: Int, val seed: Int) { var age = 0f }
    class TrailPt(val x: Float, val y: Float) { var age = 0f }
    val deathFx = ArrayList<DeathFx>(8)
    val trail = ArrayDeque<TrailPt>(8)
    var hurtFlash = 0f              // 玩家受击红晕（Screen 渲染衰减）
    var shake = 0f                  // 受击相机微抖
    var levelRingT = 0f             // 升级金环剩余时间（Screen 每帧递减渲染）
    var levelRingX = 0f; var levelRingY = 0f
    private var trailTimer = 0f
    // 批50：反馈音效计数（Screen 差量触发）
    var sfxShoot = 0; var sfxHit = 0; var sfxDeath = 0; var sfxOrb = 0; var sfxHurt = 0

    // ---------- 视口与相机 ----------
    var viewW = 1080f; var viewH = 2000f
    var camX = 0f; private set
    var camY = 0f; private set

    // ---------- 虚拟摇杆（UI 线程写、循环读的普通变量，避免每帧写 Compose 状态） ----------
    var joyActive = false
    var joyX = 0f; var joyY = 0f          // 归一化方向，长度 ≤1
    var joyBaseX = 0f; var joyBaseY = 0f  // 底座中心（屏幕坐标，绘制用）

    // ---------- 实体池 ----------
    val enemies = ArrayList<Enemy>(64)
    val bullets = ArrayList<Bullet>(64)
    val orbs = ArrayList<Orb>(64)

    /** 升级三选一的候选（空 = 无待选） */
    var pendingUpgrades: List<Upgrade> = emptyList(); private set

    fun setViewport(w: Float, h: Float) { viewW = w; viewH = h }

    /** 剩余无敌帧秒数（绘制闪烁用） */
    fun invincibleNow(): Float = invincible

    /** 已生存秒数（HUD 用） */
    fun timeSec(): Int = elapsed.toInt()

    fun start() {
        if (phase == Phase.READY || phase == Phase.GAMEOVER || phase == Phase.PAUSED) {   // 批21 修复：暂停菜单"重新开始"原本 no-op
            reset()
            phase = Phase.PLAYING
        }
    }

    fun pause() { if (phase == Phase.PLAYING) phase = Phase.PAUSED }
    fun resume() { if (phase == Phase.PAUSED) phase = Phase.PLAYING }

    fun reset() {
        px = 0f; py = 0f
        hp = 100f; maxHp = 100f
        level = 1; xp = 0; xpNext = 6
        attackDmg = 10f; attackInterval = 0.8f
        moveSpeed = 220f; pickupRange = 100f
        elapsed = 0f; kills = 0
        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f; eliteTimer = 60f
        hurtFlash = 0f; shake = 0f; levelRingT = 0f; trailTimer = 0f
        deathFx.clear(); trail.clear()
        sfxShoot = 0; sfxHit = 0; sfxDeath = 0; sfxOrb = 0; sfxHurt = 0
        enemies.clear(); bullets.clear(); orbs.clear()
        pendingUpgrades = emptyList()
        joyActive = false; joyX = 0f; joyY = 0f
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        if (phase != Phase.PLAYING) return
        val dt = dtRaw.coerceIn(0f, 0.05f)   // 掉帧/切后台恢复时不瞬移
        elapsed += dt
        if (invincible > 0f) invincible -= dt
        // 批50：反馈计时衰减
        if (hurtFlash > 0f) hurtFlash -= dt
        if (shake > 0f) shake -= dt

        // 玩家移动
        if (joyActive && (joyX != 0f || joyY != 0f)) {
            px += joyX * moveSpeed * dt
            py += joyY * moveSpeed * dt
        }
        // 批50：相机微抖（受击）
        camX = px - viewW / 2
        camY = py - viewH / 2
        if (shake > 0f) {
            camX += (Random.nextFloat() - 0.5f) * shake * 40f
            camY += (Random.nextFloat() - 0.5f) * shake * 40f
        }
        // 批50：拖尾采样（移动时每 0.05s 一点）与老化
        trailTimer -= dt
        if (trailTimer <= 0f && joyActive) {
            trail.addLast(TrailPt(px, py))
            if (trail.size > 6) trail.removeFirst()
            trailTimer = 0.05f
        }
        for (t in trail) t.age += dt
        while (trail.isNotEmpty() && trail.first().age > 0.3f) trail.removeFirst()
        // 批50：死亡粒子老化
        for (d in deathFx) d.age += dt
        deathFx.removeAll { it.age > 0.35f }

        // 敌人刷新：频率随时间提高（1.3s → 0.45s @5min，批23 注释对齐实现）
        spawnTimer -= dt
        if (spawnTimer <= 0f && enemies.size < MAX_ENEMIES) {
            spawnEnemy()
            spawnTimer = 1.3f - 0.85f * min(elapsed / 300f, 1f)
        }
        burstTimer -= dt
        if (burstTimer <= 0f) {
            burstTimer = 45f
            repeat(5) { if (enemies.size < MAX_ENEMIES) spawnEnemy() }
        }
        eliteTimer -= dt
        if (eliteTimer <= 0f && enemies.size < MAX_ENEMIES) {
            spawnElite()
            eliteTimer = 60f
        }

        // 自动攻击最近敌人
        attackTimer -= dt
        if (attackTimer <= 0f) {
            val target = nearestEnemy()
            if (target != null) {
                val dx = target.x - px; val dy = target.y - py
                val d = hypot(dx, dy)
                if (d > 1f && bullets.size < MAX_BULLETS) {
                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
                    sfxShoot++   // 批50：发射音
                    attackTimer = attackInterval
                }
            }
        }

        // 子弹飞行 + 命中（圆形碰撞：distance² ≤ (r1+r2)²）
        for (b in bullets) {
            b.x += b.dx * dt; b.y += b.dy * dt
            b.life -= dt
            if (b.life <= 0f) { b.alive = false; continue }
            for (e in enemies) {
                if (!e.alive) continue
                val dx = e.x - b.x; val dy = e.y - b.y
                val rr = e.r + bulletR
                if (dx * dx + dy * dy <= rr * rr) {
                    b.alive = false
                    // 批50：命中反馈——沿弹道击退 6px + 白闪 + 音效计数
                    val bl = hypot(b.dx, b.dy)
                    if (bl > 1f) { e.x += b.dx / bl * 6f; e.y += b.dy / bl * 6f }
                    e.hitFlash = 0.1f
                    sfxHit++
                    e.hp -= attackDmg
                    if (e.hp <= 0f) {
                        e.alive = false
                        kills++
                        sfxDeath++
                        // 批50：死亡粒子迸溅（按 kind 配色，8 颗放射 + 中心白闪）
                        if (deathFx.size < 16) {
                            val dcol = when (e.kind) {
                                1 -> 0xFF4DD0E1.toInt()
                                2 -> 0xFFFFB74D.toInt()
                                else -> if (e.r > 20f) 0xFFB23A3A.toInt() else 0xFFE15A5A.toInt()
                            }
                            deathFx.add(DeathFx(e.x, e.y, dcol, (e.x.toInt() + e.y.toInt()) and 0x7fffffff))
                        }
                        defeatedKinds.add(e.kind)
                        kindKills[e.kind] = (kindKills[e.kind] ?: 0) + 1   // 批48：图鉴记录
                        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, 1))
                    }
                    break
                }
            }
        }

        // 敌人追踪 + 碰撞玩家（批48：按种类分支行为）
        for (e in enemies) {
            if (!e.alive) continue
            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d > 1f) {
                val sp = when (e.kind) {
                    1 -> e.speed                                   // 运算符蜂：本体更快
                    else -> {
                        // 几何狂：周期冲刺（冷却 3s，冲 0.4s @2.2x）
                        if (e.kind == 2) {
                            e.dashCd -= dt
                            if (e.dashCd <= 0f && d < 420f) { e.dashT = 0.4f; e.dashCd = 3f }
                        }
                        e.speed * (if (e.dashT > 0f) 2.2f else 1f)
                    }
                }
                val base = dx / d
                // 运算符蜂：沿主方向 + 垂直正弦摆动（绕行避开直线弹幕）
                val perpX = if (e.kind == 1) -dy / d * kotlin.math.sin(elapsed * 4f + e.wobbleSeed) * 0.55f else 0f
                val perpY = if (e.kind == 1) dx / d * kotlin.math.sin(elapsed * 4f + e.wobbleSeed) * 0.55f else 0f
                e.x += (base * sp + perpX) * dt
                e.y += (dy / d * sp + perpY) * dt
                if (e.dashT > 0f) e.dashT -= dt
            }
            if (invincible <= 0f) {
                val rr = e.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    hp -= e.dmg
                    invincible = invincibleSec
                    hurtFlash = 0.3f; shake = 0.15f; sfxHurt++   // 批50：受击反馈
                    if (hp <= 0f) {
                        hp = 0f
                        phase = Phase.GAMEOVER
                        return
                    }
                }
            }
        }

        // 经验球：拾取范围内自动吸附，靠近后收进
        for (o in orbs) {
            if (!o.alive) continue
            val dx = px - o.x; val dy = py - o.y
            val d2 = dx * dx + dy * dy
            val rr = playerR + orbR
            if (d2 <= rr * rr) {
                o.alive = false
                sfxOrb++   // 批50：拾取音
                gainXp(o.value)
                if (phase != Phase.PLAYING) return   // 升级弹窗打断本帧
            } else {
                if (!o.magnet && d2 <= pickupRange * pickupRange) o.magnet = true
                if (o.magnet) {
                    val d = hypot(dx, dy)
                    if (d > 1f) {
                        o.x += dx / d * 520f * dt
                        o.y += dy / d * 520f * dt
                    }
                }
            }
        }

        // alive 标记统一清理
        enemies.removeAll { !it.alive }
        bullets.removeAll { !it.alive }
        orbs.removeAll { !it.alive }
    }

    private fun gainXp(v: Int) {
        xp += v
        if (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 4 + (level - 1) * 3
            pendingUpgrades = rollUpgrades()
            // 批50：升级金环演出（Screen 每帧递减渲染）
            levelRingT = 0.5f; levelRingX = px; levelRingY = py
            phase = Phase.LEVELUP
        }
    }

    /** 升级三选一：5 种强化随机取 3 种 */
    private fun rollUpgrades(): List<Upgrade> {
        // 批42：强化数学化命名（参考《火柴人 vs 数学》美学）；批45：稀有强化
        val base = listOf(
            Upgrade("atk", "× 系数增幅", "子弹伤害 +25%"),
            Upgrade("aspd", "d/dt 加速", "攻击间隔 −15%"),
            Upgrade("spd", "√ 速度矢量", "移动速度 +10%"),
            Upgrade("hp", "|x| 绝对护甲", "生命上限 +25 并回复 25"),
            Upgrade("pickup", "π 引力环", "拾取范围 +30%"),
        )
        val rares = listOf(
            Upgrade("atk2", "×² 平方爆发", "子弹伤害 +50%", true),
            Upgrade("hp2", "|x|² 巨盾", "生命上限 +60 并回复 60", true),
        )
        val pool = base.toMutableList()
        if (Random.nextFloat() < 0.2f) {
            pool[Random.nextInt(pool.size)] = rares[Random.nextInt(rares.size)]
        }
        return pool.shuffled(Random).take(3)
    }

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        when (id) {
            "atk" -> attackDmg *= 1.25f
            "atk2" -> attackDmg *= 1.5f
            "aspd" -> attackInterval *= 0.85f
            "spd" -> moveSpeed *= 1.10f
            "hp" -> { maxHp += 25f; hp = min(hp + 25f, maxHp) }
            "hp2" -> { maxHp += 60f; hp = min(hp + 60f, maxHp) }
            "pickup" -> pickupRange *= 1.30f
        }
        pendingUpgrades = emptyList()
        phase = Phase.PLAYING
    }

    // ---------- 内部 ----------
    private fun nearestEnemy(): Enemy? {
        var best: Enemy? = null
        var bestD = 480f * 480f   // 攻击视距
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - px; val dy = e.y - py
            val d2 = dx * dx + dy * dy
            if (d2 <= bestD) { bestD = d2; best = e }
        }
        return best
    }

    private fun spawnEnemy() {
        // 从玩家周围、屏幕外的圆环上随机角度刷新
        val ring = hypot(viewW, viewH) / 2f + 50f
        val ang = Random.nextFloat() * 6.2831853f
        val hp = enemyBaseHp + elapsed * 0.9f
        val speed = enemyBaseSpeed + min(elapsed * 0.35f, 60f)
        val dmg = contactBaseDmg + min(elapsed / 75f, 8f)
        val r = if (Random.nextFloat() < 0.08f) 26f else 13f + Random.nextFloat() * 7f   // 8% 大只
        // 批42：字形按生存时间三阶解锁（数字→运算符→希腊字母），有成长感
        val glyphPool = when {
            elapsed < 30f -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
            elapsed < 90f -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "−")
            elapsed < 180f -> listOf("2", "3", "5", "7", "+", "−", "×", "÷", "=")
            else -> listOf("π", "√", "∑", "∞", "∆", "×", "÷", "=")
        }
        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
        val sides = if (r > 20f) listOf(3, 4, 5)[Random.nextInt(3)] else 0
        // 批48：种类混合——<30s 全数字崽，30~90s 混入运算符蜂，>90s 混入几何狂
        val kind = when {
            elapsed < 30f -> 0
            elapsed < 90f -> if (Random.nextFloat() < 0.3f) 1 else 0
            else -> when {
                Random.nextFloat() < 0.2f -> 2
                Random.nextFloat() < 0.35f -> 1
                else -> 0
            }
        }
        val spd = if (kind == 1) speed * 1.2f else speed   // 运算符蜂更快
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, spd, dmg, glyph, sides, kind))
    }

    /** 批48：击杀记录（图鉴+计数） */
    val defeatedKinds = mutableSetOf<Int>()
    val kindKills = mutableMapOf<Int, Int>()

    /** 批45：∑ 求和体（精英）——每 60s 一只，六边线框+大血量，压节奏用 */
    private fun spawnElite() {
        val ring = hypot(viewW, viewH) / 2f + 50f
        val ang = Random.nextFloat() * 6.2831853f
        val hp = enemyBaseHp * 6f + elapsed * 3.5f
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, 32f, hp, hp, 42f + min(elapsed * 0.2f, 25f), contactBaseDmg * 1.5f, "∑", 6, 3))
    }

    companion object {
        const val MAX_ENEMIES = 150
        const val MAX_BULLETS = 200
        const val MAX_ORBS = 120
    }
}
