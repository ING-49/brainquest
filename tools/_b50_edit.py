# -*- coding: utf-8 -*-
"""批50 编辑：幸存者反馈套件（死亡粒子/命中击退白闪/经验球脉动/受击红晕/拖尾/升级演出/4 新音效）"""
import io

# ============ A. Sfx.kt：4 个新合成类型 ============
p = 'app/src/main/java/com/brainquest/game/util/Sfx.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    COIN, CHEER,
}'''
new = '''    COIN, CHEER,
    SHOOT, HIT, DEATH, ORB,   // 批50：幸存者动作反馈音（合成短音）
}'''
assert old in s, 'a1'
s = s.replace(old, new)
old = '''        SfxType.CHEER -> listOf(
            Note(ToneGenerator.TONE_DTMF_1, 80, 30),
            Note(ToneGenerator.TONE_DTMF_3, 80, 30),
            Note(ToneGenerator.TONE_DTMF_5, 80, 30),
            Note(ToneGenerator.TONE_DTMF_9, 150),
        )
    }'''
new = '''        SfxType.CHEER -> listOf(
            Note(ToneGenerator.TONE_DTMF_1, 80, 30),
            Note(ToneGenerator.TONE_DTMF_3, 80, 30),
            Note(ToneGenerator.TONE_DTMF_5, 80, 30),
            Note(ToneGenerator.TONE_DTMF_9, 150),
        )
        SfxType.SHOOT -> listOf(Note(ToneGenerator.TONE_DTMF_0, 45))
        SfxType.HIT -> listOf(Note(ToneGenerator.TONE_DTMF_7, 55))
        SfxType.DEATH -> listOf(
            Note(ToneGenerator.TONE_DTMF_8, 60, 20),
            Note(ToneGenerator.TONE_PROP_ACK, 90),
        )
        SfxType.ORB -> listOf(Note(ToneGenerator.TONE_DTMF_6, 40))
    }'''
assert old in s, 'a2'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('Sfx ok')

# ============ B. SurvivorGame.kt ============
p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# B1) 反馈状态字段
old = '''    private var eliteTimer = 60f   // 批45：每 60s 一只 ∑ 求和体（精英）'''
new = '''    private var eliteTimer = 60f   // 批45：每 60s 一只 ∑ 求和体（精英）

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
    var sfxShoot = 0; var sfxHit = 0; var sfxDeath = 0; var sfxOrb = 0; var sfxHurt = 0'''
assert old in s, 'b1'
s = s.replace(old, new)

# B2) Enemy 加 hitFlash
old = '''        var dashCd = 0f                                    // 批48：几何狂的冲刺冷却
        var dashT = 0f                                     // 批48：冲刺剩余时间
    }'''
new = '''        var dashCd = 0f                                    // 批48：几何狂的冲刺冷却
        var dashT = 0f                                     // 批48：冲刺剩余时间
        var hitFlash = 0f                                  // 批50：命中白闪计时
    }'''
assert old in s, 'b2'
s = s.replace(old, new)

# B3) reset 清理
old = '''        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f; eliteTimer = 60f
        enemies.clear(); bullets.clear(); orbs.clear()'''
new = '''        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f; eliteTimer = 60f
        hurtFlash = 0f; shake = 0f; levelRingT = 0f; trailTimer = 0f
        deathFx.clear(); trail.clear()
        sfxShoot = 0; sfxHit = 0; sfxDeath = 0; sfxOrb = 0; sfxHurt = 0
        enemies.clear(); bullets.clear(); orbs.clear()'''
assert old in s, 'b3'
s = s.replace(old, new)

# B4) tick 开头衰减 + 拖尾采样
old = '''        elapsed += dt
        if (invincible > 0f) invincible -= dt

        // 玩家移动
        if (joyActive && (joyX != 0f || joyY != 0f)) {
            px += joyX * moveSpeed * dt
            py += joyY * moveSpeed * dt
        }
        camX = px - viewW / 2
        camY = py - viewH / 2'''
new = '''        elapsed += dt
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
        deathFx.removeAll { it.age > 0.35f }'''
assert old in s, 'b4'
s = s.replace(old, new)

# B5) 子弹命中：击退 + 白闪 + 计数
old = '''                if (dx * dx + dy * dy <= rr * rr) {
                    b.alive = false
                    e.hp -= attackDmg
                    if (e.hp <= 0f) {
                        e.alive = false
                        kills++
                        defeatedKinds.add(e.kind)
                        kindKills[e.kind] = (kindKills[e.kind] ?: 0) + 1   // 批48：图鉴记录
                        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, 1))
                    }
                    break
                }'''
new = '''                if (dx * dx + dy * dy <= rr * rr) {
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
                }'''
assert old in s, 'b5'
s = s.replace(old, new)

# B6) 玩家受击：红晕 + 微抖 + 计数
old = '''                if (invincible <= 0f) {
                    val rr = e.r + playerR
                    if (dx * dx + dy * dy <= rr * rr) {
                        hp -= e.dmg
                        invincible = invincibleSec'''
new = '''                if (invincible <= 0f) {
                    val rr = e.r + playerR
                    if (dx * dx + dy * dy <= rr * rr) {
                        hp -= e.dmg
                        invincible = invincibleSec
                        hurtFlash = 0.3f; shake = 0.15f; sfxHurt++   // 批50：受击反馈'''
assert old in s, 'b6'
s = s.replace(old, new)

# B7) 经验球拾取计数
old = '''            if (d2 <= rr * rr) {
                o.alive = false
                gainXp(o.value)'''
new = '''            if (d2 <= rr * rr) {
                o.alive = false
                sfxOrb++   // 批50：拾取音
                gainXp(o.value)'''
assert old in s, 'b7'
s = s.replace(old, new)

# B8) 发射计数
old = '''                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
                    attackTimer = attackInterval'''
new = '''                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
                    sfxShoot++   // 批50：发射音
                    attackTimer = attackInterval'''
assert old in s, 'b8'
s = s.replace(old, new)

# B9) 升级金环（gainXp 内设置位置与计时）
old = '''        if (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 4 + (level - 1) * 3
            pendingUpgrades = rollUpgrades()
            phase = Phase.LEVELUP
        }'''
new = '''        if (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 4 + (level - 1) * 3
            pendingUpgrades = rollUpgrades()
            // 批50：升级金环演出（Screen 每帧递减渲染）
            levelRingT = 0.5f; levelRingX = px; levelRingY = py
            phase = Phase.LEVELUP
        }'''
assert old in s, 'b9'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame ok')
