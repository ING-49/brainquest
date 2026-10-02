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

    /** 帧事件（伤害飘字/粒子/闪电，渲染层消费后转成持续特效） */
    data class FxEvent(
        val x: Float, val y: Float, val text: String, val crit: Boolean,
        val element: Element?, val kind: Int,   // kind 0=伤害 1=死亡 2=拾取 3=闪电段
        val x2: Float = 0f, val y2: Float = 0f, // 闪电段终点
    )

    /** 精英词缀 */
    enum class Affix { RAGE, SHIELD, SPLIT }

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
    private val spawnNow = ArrayList<Enemy>(4)   // 迭代中的安全生成队列（Boss召唤/精英分裂）

    /** 场地边界（敌人不出当前房间）；null = 不限制 */
    var arenaLeft = 0f; var arenaTop = 0f
    var arenaRight = 0f; var arenaBottom = 0f

    /** 职业被动 id（knight 格挡 / mage 击杀回血 / ranger 闪避），null = 无 */
    var passiveId: String? = null

    // ---------- 主动技能 ----------
    var skillId: String? = null; private set
    var skillCd = 0f; private set
    var skillCdMax = 14f
    var timeScale = 1f; private set      // 时间减速/冰冻全局倍率（只作用于敌人）
    private var timeScaleTimer = 0f
    var shake = 0f; private set          // 震屏强度（渲染层读，随 tick 衰减）
    var shieldTime = 0f; private set     // 护盾剩余时间
    private var shieldLeft = 0f          // 护盾剩余吸收量

    /** 每层结束的三选一技能池 */
    fun rollSkills(rng: Random): List<String> = listOf(
        "dash", "shield", "heal", "slowtime", "freeze", "meteor", "chain"
    ).shuffled(rng).take(3)

    fun setSkill(id: String) { skillId = id; skillCd = 0f }

    fun useSkill(): Boolean {
        val id = skillId ?: return false
        if (phase != Phase.PLAYING || skillCd > 0f) return false
        skillCd = skillCdMax
        when (id) {
            "dash" -> {   // 冲刺：朝面向位移 240px + 短无敌
                px += cos(facing) * 240f; py += sin(facing) * 240f
                invincible = invincibleSec
                shake = 6f
            }
            "shield" -> { shieldTime = 8f; shieldLeft = 50f }
            "heal" -> heal((maxHp * 0.4f).toInt())
            "slowtime" -> { timeScaleTimer = 5f; timeScale = 0.3f }
            "freeze" -> enemies.forEach { it.frozen = maxOf(it.frozen, 2.5f) }
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
        skillCd = 0f; shieldTime = 0f; shieldLeft = 0f
        timeScale = 1f; timeScaleTimer = 0f; shake = 0f
        phase = Phase.IDLE
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        events.clear()
        if (phase != Phase.PLAYING) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        elapsed += dt
        if (invincible > 0f) invincible -= dt
        if (skillCd > 0f) skillCd -= dt
        if (shieldTime > 0f) shieldTime -= dt
        if (timeScaleTimer > 0f) { timeScaleTimer -= dt; if (timeScaleTimer <= 0f) timeScale = 1f }
        if (shake > 0f) shake -= dt

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
            if (e.hitFlash > 0f) e.hitFlash -= dt
            // 元素状态
            ElementSystem.tickStatus(e, dt, this)

            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d < 1f) continue
            var sp = e.speed * timeScale
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
                else -> {   // SKELETON / DUMMY：直追（Boss 走专属 AI）
                    if (e.bossFloor > 0) bossAI(e, dx, dy, d, sp, dt)
                    else { e.x += dx / d * sp * dt; e.y += dy / d * sp * dt }
                }
            }
            // 不沉入玩家圆心：贴到接触环即止（多怪堆进圆心会让近战扇形永远背对目标打空）
            val minD = e.r + playerR + 2f
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
        for (oi in orbs.indices) {
            val o = orbs[oi]
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
        if (e.shieldHp > 0f) {
            val absorbed = minOf(e.shieldHp, dmg)
            e.shieldHp -= absorbed
            dmg -= absorbed
        }
        e.hp -= dmg
        if (e.reflect && e.bossFloor == 0 && dmg > 0f) hurtPlayer(dmg * 0.15f)
        e.hitFlash = 0.12f
        events.add(FxEvent(e.x, e.y - e.r, "${dmg.toInt()}", crit, element, 0))
        if (element != null) ElementSystem.onHit(e, element, this)
        if (e.hp <= 0f && e.alive) killEnemy(e)
    }

    /** 击杀：逻辑立即结算（掉落/计数/回调），尸体进入 0.3s 死亡动画由渲染淡出 */
    fun killEnemy(e: Enemy) {
        e.alive = false
        e.dying = true
        e.deathTimer = 0.3f
        events.add(FxEvent(e.x, e.y, "", false, null, 1))
        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, e.xpValue))
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
        events.add(FxEvent(px, py - 30f, "-${dmg.toInt()}", false, null, 0))
        if (hp <= 0) { hp = 0; phase = Phase.GAMEOVER }
    }

    fun heal(n: Int) { if (phase != Phase.GAMEOVER) hp = min(hp + n, maxHp) }

    // ---------- 装备增量（换装差量应用） ----------
    private var lastEnemyLog = -1
    fun setMaxHp(n: Int) { maxHp = n.coerceAtLeast(1); if (hp > maxHp) hp = maxHp }
    /** DEBUG 兜底：自动驾驶用（升级空队列时恢复探索） */
    fun forcePlaying() { phase = Phase.PLAYING }
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
        var hits = 0
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy)
            if (d > range + e.r) continue
            val ang = abs(angleDiff(atan2(dy, dx), facing))
            if (ang <= arcRad / 2f) { hitEnemy(e, dmg, element); hits++ }
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
