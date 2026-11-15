# -*- coding: utf-8 -*-
"""批47 编辑：图鉴档案详情 + 击杀统计 + 新怪泥沼傀儡 + 中途退出合并修复"""
import io

# ============ A. PlayerState ============
p = 'app/src/main/java/com/brainquest/game/data/PlayerState.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    val seenMonsters: List<String> = emptyList(), // 📖 已遭遇的怪物（图鉴，批20-D）'''
new = '''    val seenMonsters: List<String> = emptyList(), // 📖 已遭遇的怪物（图鉴，批20-D）
    val monsterKills: Map<String, Int> = emptyMap(), // 📖 各怪累计击杀（批47：图鉴详情页展示）'''
assert old in s, 'a'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PlayerState ok')

# ============ B. AppViewModel ============
p = 'app/src/main/java/com/brainquest/game/AppViewModel.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    /** 图鉴：合并本局遭遇的怪物（批20-D） */
    fun mergeDungeonSeen(kinds: List<String>) {
        if (kinds.isEmpty()) return
        commit { it.copy(seenMonsters = (it.seenMonsters + kinds).distinct()) }
    }'''
new = '''    /** 图鉴：合并本局遭遇的怪物（批20-D） */
    fun mergeDungeonSeen(kinds: List<String>) {
        if (kinds.isEmpty()) return
        commit { it.copy(seenMonsters = (it.seenMonsters + kinds).distinct()) }
    }

    /** 图鉴：合并本局各怪击杀数（批47） */
    fun mergeMonsterKills(kills: Map<String, Int>) {
        if (kills.isEmpty()) return
        commit {
            val merged = it.monsterKills.toMutableMap()
            kills.forEach { (k, v) -> merged[k] = (merged[k] ?: 0) + v }
            it.copy(monsterKills = merged)
        }
    }'''
assert old in s, 'b'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('AppViewModel ok')

# ============ C1. DungeonGame：击杀计数 ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonGame.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    val seenMonsters = mutableSetOf<String>()   // 📖 本局遭遇的怪物（图鉴，批20-D；结算合并进 PlayerState）'''
new = '''    val seenMonsters = mutableSetOf<String>()   // 📖 本局遭遇的怪物（图鉴，批20-D；结算合并进 PlayerState）
    val monsterKills = mutableMapOf<String, Int>()   // 📖 本局各怪击杀数（批47）'''
assert old in s, 'c1'
s = s.replace(old, new)
old = '''        engine.onEnemyKilled = { e ->
            seenMonsters.add(e.kind.name)
            if (e.bossFloor > 0) seenMonsters.add("BOSS_" + listOf("big_demon", "chort", "big_zombie", "ogre")[(e.bossFloor - 1).mod(4)])'''
new = '''        engine.onEnemyKilled = { e ->
            seenMonsters.add(e.kind.name)
            monsterKills[e.kind.name] = (monsterKills[e.kind.name] ?: 0) + 1   // 批47：击杀计数
            if (e.bossFloor > 0) {
                seenMonsters.add("BOSS_" + listOf("big_demon", "chort", "big_zombie", "ogre")[(e.bossFloor - 1).mod(4)])
                val bKey = "BOSS_" + listOf("big_demon", "chort", "big_zombie", "ogre")[(e.bossFloor - 1).mod(4)]
                monsterKills[bKey] = (monsterKills[bKey] ?: 0) + 1
            }'''
assert old in s, 'c2'
s = s.replace(old, new)

# ============ C2. 刷怪表加泥沼傀儡 ============
old = '''            val kindHp = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.9f
                EnemyKind.BOOM_SLIME -> 0.8f
                else -> 1f
            }'''
new = '''            val kindHp = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.9f
                EnemyKind.BOOM_SLIME -> 0.8f
                EnemyKind.MUDDY_GOLEM -> 2.2f
                else -> 1f
            }'''
assert old in s, 'c3'
s = s.replace(old, new)
old = '''            val kindDmg = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.35f
                EnemyKind.BOOM_SLIME -> 1.5f
                EnemyKind.BONE_ARCHER -> 0.9f
                else -> 1f
            }'''
new = '''            val kindDmg = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.35f
                EnemyKind.BOOM_SLIME -> 1.5f
                EnemyKind.BONE_ARCHER -> 0.9f
                else -> 1f
            }'''
s = s.replace(old, new)
old = '''            val speed = (when (kind) {
                EnemyKind.BAT -> 95f
                EnemyKind.BONE_ARCHER -> 78f
                EnemyKind.BOOM_SLIME -> 85f
                EnemyKind.SLIME -> 55f
                EnemyKind.CASTER -> 60f
                EnemyKind.SHIELD_GUARD -> 45f
                else -> 68f'''
new = '''            val speed = (when (kind) {
                EnemyKind.BAT -> 95f
                EnemyKind.BONE_ARCHER -> 78f
                EnemyKind.BOOM_SLIME -> 85f
                EnemyKind.SLIME -> 55f
                EnemyKind.CASTER -> 60f
                EnemyKind.SHIELD_GUARD -> 45f
                EnemyKind.MUDDY_GOLEM -> 40f
                else -> 68f'''
assert old in s, 'c4'
s = s.replace(old, new)
old = '''            val r = when {
                big -> 48f
                elite -> 27f
                kind == EnemyKind.SHIELD_GUARD -> 24f
                kind == EnemyKind.BONE_ARCHER -> 16f
                kind == EnemyKind.BOOM_SLIME -> 15f
                kind == EnemyKind.SLIME -> 17f
                else -> 18f
            }'''
new = '''            val r = when {
                big -> 48f
                elite -> 27f
                kind == EnemyKind.SHIELD_GUARD -> 24f
                kind == EnemyKind.BONE_ARCHER -> 16f
                kind == EnemyKind.BOOM_SLIME -> 15f
                kind == EnemyKind.SLIME -> 17f
                kind == EnemyKind.MUDDY_GOLEM -> 22f
                else -> 18f
            }'''
assert old in s, 'c5'
s = s.replace(old, new)

# ============ C3. 战斗房刷怪池（第2层起 1/6 泥沼傀儡）+ 第二波 ============
old = '''            repeat(n) {
'''
idx = s.find(old)
assert idx > 0, 'c6 pool anchor'
# 找 BATTLE 池的级联段（floor>=2 且 1/6 → BONE_ARCHER 附近）
old6 = '''                    if (floor >= 2 && rng.nextInt(6) == 0) EnemyKind.BONE_ARCHER'''
if old6 in s:
    s = s.replace(old6, '''                    if (floor >= 2 && rng.nextInt(6) == 0) EnemyKind.MUDDY_GOLEM
                    else if (floor >= 2 && rng.nextInt(6) == 0) EnemyKind.BONE_ARCHER''')
    print('battle pool: muddy added')
else:
    # 备选锚点：其他写法
    print('WARN: battle pool anchor 未命中，需人工检查')
# 第二波
old7 = '''            val kind = listOf(EnemyKind.SKELETON, EnemyKind.BAT, EnemyKind.CASTER, EnemyKind.BOOM_SLIME, EnemyKind.BONE_ARCHER).random(rng)'''
if old7 in s:
    s = s.replace(old7, '''            val kind = listOf(EnemyKind.SKELETON, EnemyKind.BAT, EnemyKind.CASTER, EnemyKind.BOOM_SLIME, EnemyKind.BONE_ARCHER, EnemyKind.MUDDY_GOLEM).random(rng)''')
    print('second wave: muddy added')
else:
    print('WARN: second wave anchor 未命中')
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DungeonGame ok')

# ============ D. Models.kt ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/model/Models.kt'
s = io.open(p, encoding='utf-8').read()
old = '''enum class EnemyKind { DUMMY, SLIME, SKELETON, BAT, CASTER, BONE_ARCHER, SHIELD_GUARD, BOOM_SLIME }'''
new = '''enum class EnemyKind { DUMMY, SLIME, SKELETON, BAT, CASTER, BONE_ARCHER, SHIELD_GUARD, BOOM_SLIME, MUDDY_GOLEM }'''
assert old in s, 'd'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('Models ok')

# ============ E. CombatEngine：吞弹 ============
p = 'app/src/main/java/com/brainquest/game/game/core/CombatEngine.kt'
s = io.open(p, encoding='utf-8').read()
old = '''                if (dx * dx + dy * dy <= rr * rr) {
                    val bl = hypot(b.dx, b.dy)'''
new = '''                if (dx * dx + dy * dy <= rr * rr) {
                    // 批47：泥沼傀儡「吞弹」——玩家子弹被吸收并回 2 血，远程打它是在喂它
                    if (e.kind == EnemyKind.MUDDY_GOLEM) {
                        b.alive = false
                        e.hp = min(e.maxHp, e.hp + 2f)
                        continue
                    }
                    val bl = hypot(b.dx, b.dy)'''
assert old in s, 'e'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('CombatEngine ok')

# ============ F. SpriteAnims：muddy 映射 ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/render/SpriteAnims.kt'
s = io.open(p, encoding='utf-8').read()
old = '''        "BOOM_SLIME" to "wogol",
    )'''
new = '''        "BOOM_SLIME" to "wogol",
        "MUDDY_GOLEM" to "muddy",
    )'''
assert old in s, 'f'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SpriteAnims ok')
print('PHASE1_DONE')
