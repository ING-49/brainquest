# -*- coding: utf-8 -*-
"""批45 编辑：幸存者数学深化——稀有强化 / × 乘号子弹 / ∑ 精英怪"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# 1) Upgrade 加稀有度字段
old = '''    /** 强化选项（升级三选一） */
    data class Upgrade(val id: String, val name: String, val desc: String)'''
new = '''    /** 强化选项（升级三选一）；批45：rare=稀有强化（金色卡，效果更强） */
    data class Upgrade(val id: String, val name: String, val desc: String, val rare: Boolean = false)'''
assert old in s, 'u1'
s = s.replace(old, new)

# 2) rollUpgrades：20% 概率混入一张稀有
old = '''    private fun rollUpgrades(): List<Upgrade> = listOf(
        // 批42：强化数学化命名（参考《火柴人 vs 数学》美学；机制不变）
        Upgrade("atk", "× 系数增幅", "子弹伤害 +25%"),
        Upgrade("aspd", "d/dt 加速", "攻击间隔 −15%"),
        Upgrade("spd", "√ 速度矢量", "移动速度 +10%"),
        Upgrade("hp", "|x| 绝对护甲", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "π 引力环", "拾取范围 +30%"),
    ).shuffled(Random).take(3)'''
new = '''    private fun rollUpgrades(): List<Upgrade> {
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
    }'''
assert old in s, 'u2'
s = s.replace(old, new)

# 3) chooseUpgrade 处理稀有项
old = '''            "atk" -> attackDmg *= 1.25f
            "aspd" -> attackInterval *= 0.85f
            "spd" -> moveSpeed *= 1.10f
            "hp" -> { maxHp += 25f; hp = min(hp + 25f, maxHp) }
            "pickup" -> pickupRange *= 1.30f'''
new = '''            "atk" -> attackDmg *= 1.25f
            "atk2" -> attackDmg *= 1.5f
            "aspd" -> attackInterval *= 0.85f
            "spd" -> moveSpeed *= 1.10f
            "hp" -> { maxHp += 25f; hp = min(hp + 25f, maxHp) }
            "hp2" -> { maxHp += 60f; hp = min(hp + 60f, maxHp) }
            "pickup" -> pickupRange *= 1.30f'''
assert old in s, 'u3'
s = s.replace(old, new)

# 4) elite 计时器字段
old = '''    private var spawnTimer = 0f
    private var burstTimer = 45f   // 每 45s 一波小高潮'''
new = '''    private var spawnTimer = 0f
    private var burstTimer = 45f   // 每 45s 一波小高潮
    private var eliteTimer = 60f   // 批45：每 60s 一只 ∑ 求和体（精英）'''
assert old in s, 'u4'
s = s.replace(old, new)

# 5) tick 里刷精英
old = '''        burstTimer -= dt
        if (burstTimer <= 0f) {
            burstTimer = 45f
            repeat(5) { if (enemies.size < MAX_ENEMIES) spawnEnemy() }
        }'''
new = '''        burstTimer -= dt
        if (burstTimer <= 0f) {
            burstTimer = 45f
            repeat(5) { if (enemies.size < MAX_ENEMIES) spawnEnemy() }
        }
        eliteTimer -= dt
        if (eliteTimer <= 0f && enemies.size < MAX_ENEMIES) {
            spawnElite()
            eliteTimer = 60f
        }'''
assert old in s, 'u5'
s = s.replace(old, new)

# 6) spawnElite 函数（挂在 spawnEnemy 后）
old = '''        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
        val sides = if (r > 20f) listOf(3, 4, 5)[Random.nextInt(3)] else 0
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg, glyph, sides))
    }'''
new = '''        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
        val sides = if (r > 20f) listOf(3, 4, 5)[Random.nextInt(3)] else 0
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg, glyph, sides))
    }

    /** 批45：∑ 求和体（精英）——每 60s 一只，六边线框+大血量，压节奏用 */
    private fun spawnElite() {
        val ring = hypot(viewW, viewH) / 2f + 50f
        val ang = Random.nextFloat() * 6.2831853f
        val hp = enemyBaseHp * 6f + elapsed * 3.5f
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, 32f, hp, hp, 42f + min(elapsed * 0.2f, 25f), contactBaseDmg * 1.5f, "∑", 6))
    }'''
assert old in s, 'u6'
s = s.replace(old, new)

# 7) reset 时重置 eliteTimer
old = '''        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f'''
new = '''        invincible = 0f; attackTimer = 0f; spawnTimer = 0f; burstTimer = 45f; eliteTimer = 60f'''
assert old in s, 'u7'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame ok')

# ============ SurvivorScreen.kt ============
p2 = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()

# 8) 子弹 → 金色 × 乘号（两段交叉线）
old8 = '''            // 子弹
            for (b in game.bullets) {
                if (!b.alive) continue
                drawCircle(BULLET_C, game.bulletR, Offset(b.x - game.camX, b.y - game.camY))
            }'''
new8 = '''            // 子弹（批45：金色 × 乘号——两段交叉线，数学主题）
            for (b in game.bullets) {
                if (!b.alive) continue
                val bx = b.x - game.camX; val by = b.y - game.camY
                drawLine(BULLET_C, Offset(bx - 5f, by - 5f), Offset(bx + 5f, by + 5f), 3f)
                drawLine(BULLET_C, Offset(bx - 5f, by + 5f), Offset(bx + 5f, by - 5f), 3f)
            }'''
assert old8 in s2, 'u8'
s2 = s2.replace(old8, new8)

# 9) 精英 ∑ 大字形（大怪几何框内叠金色 ∑）
old9 = '''                    drawPath(path, ENEMY_BIG_C.copy(alpha = 0.30f))
                    drawPath(path, ENEMY_BIG_C, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))'''
new9 = '''                    drawPath(path, ENEMY_BIG_C.copy(alpha = 0.30f))
                    drawPath(path, ENEMY_BIG_C, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
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
                    }'''
assert old9 in s2, 'u9'
s2 = s2.replace(old9, new9)

# 10) LEVELUP 稀有卡：金色 accent + ✦稀有 角标
old10 = '''                        game.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(Modifier.weight(1f), accent = GameUi.Xp, contentPadding = 16.dp) {'''
new10 = '''                        game.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(Modifier.weight(1f), accent = if (up.rare) GameUi.Gold else GameUi.Xp, contentPadding = 16.dp) {'''
assert old10 in s2, 'u10'
s2 = s2.replace(old10, new10)
old11 = '''                                    Text(
                                        "强化 0${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = GameUi.Gold, modifier = Modifier.padding(start = 8.dp),
                                    )'''
new11 = '''                                    Text(
                                        if (up.rare) "✦ 稀有强化" else "强化 0${idx + 1}",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (up.rare) Color(0xFFFFB74D) else GameUi.Gold,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )   // 批45：稀有卡金标'''
assert old11 in s2, 'u11'
s2 = s2.replace(old11, new11)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('SurvivorScreen ok')
