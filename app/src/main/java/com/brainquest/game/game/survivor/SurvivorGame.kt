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
    class Enemy(var x: Float, var y: Float, val r: Float, var hp: Float, val maxHp: Float, val speed: Float, val dmg: Float) {
        var alive = true
    }

    class Bullet(var x: Float, var y: Float, val dx: Float, val dy: Float) {
        var alive = true
        var life = 1.5f
    }

    class Orb(var x: Float, var y: Float, val value: Int) {
        var alive = true
        var magnet = false
    }

    /** 强化选项（升级三选一） */
    data class Upgrade(val id: String, val name: String, val desc: String)

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
        if (phase == Phase.READY || phase == Phase.GAMEOVER) {
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
        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f
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

        // 玩家移动
        if (joyActive && (joyX != 0f || joyY != 0f)) {
            px += joyX * moveSpeed * dt
            py += joyY * moveSpeed * dt
        }
        camX = px - viewW / 2
        camY = py - viewH / 2

        // 敌人刷新：频率随时间提高（1.1s → 0.3s @5min）
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

        // 自动攻击最近敌人
        attackTimer -= dt
        if (attackTimer <= 0f) {
            val target = nearestEnemy()
            if (target != null) {
                val dx = target.x - px; val dy = target.y - py
                val d = hypot(dx, dy)
                if (d > 1f && bullets.size < MAX_BULLETS) {
                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
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
                    e.hp -= attackDmg
                    if (e.hp <= 0f) {
                        e.alive = false
                        kills++
                        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, 1))
                    }
                    break
                }
            }
        }

        // 敌人追踪 + 碰撞玩家（有无敌帧）
        for (e in enemies) {
            if (!e.alive) continue
            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d > 1f) {
                e.x += dx / d * e.speed * dt
                e.y += dy / d * e.speed * dt
            }
            if (invincible <= 0f) {
                val rr = e.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    hp -= e.dmg
                    invincible = invincibleSec
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
            phase = Phase.LEVELUP
        }
    }

    /** 升级三选一：5 种强化随机取 3 种 */
    private fun rollUpgrades(): List<Upgrade> = listOf(
        Upgrade("atk", "⚔️ 攻击力", "子弹伤害 +25%"),
        Upgrade("aspd", "⚡ 攻速", "攻击间隔 −15%"),
        Upgrade("spd", "👟 移速", "移动速度 +10%"),
        Upgrade("hp", "❤️ 生命", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "🧲 拾取", "拾取范围 +30%"),
    ).shuffled(Random).take(3)

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        when (id) {
            "atk" -> attackDmg *= 1.25f
            "aspd" -> attackInterval *= 0.85f
            "spd" -> moveSpeed *= 1.10f
            "hp" -> { maxHp += 25f; hp = min(hp + 25f, maxHp) }
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
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg))
    }

    companion object {
        const val MAX_ENEMIES = 150
        const val MAX_BULLETS = 200
        const val MAX_ORBS = 120
    }
}
