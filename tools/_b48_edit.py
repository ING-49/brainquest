# -*- coding: utf-8 -*-
"""批48 编辑：幸存者怪种类化（数字崽/运算符蜂/几何狂 三行为）+ 图鉴持久化 + 图鉴页"""
import io

# ============ A. PlayerState：幸存者图鉴 ============
p = 'app/src/main/java/com/brainquest/game/data/PlayerState.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    val monsterKills: Map<String, Int> = emptyMap(), // 📖 各怪累计击杀（批47：图鉴详情页展示）'''
new = '''    val monsterKills: Map<String, Int> = emptyMap(), // 📖 各怪累计击杀（批47：图鉴详情页展示）
    val survivorSeen: List<String> = emptyList(), // 📐 数学空间已遭遇的怪种类（批48）'''
assert old in s, 'a'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PlayerState ok')

# ============ B. AppViewModel：标记幸存者图鉴 ============
p = 'app/src/main/java/com/brainquest/game/AppViewModel.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    /** 图鉴：合并本局各怪击杀数（批47） */'''
new = '''    /** 图鉴：标记幸存者已遭遇的怪种类（批48） */
    fun markSurvivorSeen(kinds: List<String>) {
        if (kinds.isEmpty()) return
        commit { it.copy(survivorSeen = (it.survivorSeen + kinds).distinct()) }
    }

    /** 图鉴：合并本局各怪击杀数（批47） */'''
assert old in s, 'b'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('AppViewModel ok')

# ============ C. SurvivorGame：种类字段/行为分支/击杀记录 ============
p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# C1) Enemy 加 kind
old = '''    class Enemy(
        var x: Float, var y: Float, val r: Float, var hp: Float, val maxHp: Float, val speed: Float, val dmg: Float,
        // 批42：数学实体——普通怪=字形（数字/运算符/希腊字母），大怪=几何线框边数（3/4/5）
        val glyph: String = "1",
        val sides: Int = 0,
    ) {
        var alive = true
    }'''
new = '''    class Enemy(
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
    }'''
assert old in s, 'c1'
s = s.replace(old, new)

# C2) spawnEnemy：种类混合（按时间解锁）
old = '''        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
        val sides = if (r > 20f) listOf(3, 4, 5)[Random.nextInt(3)] else 0
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg, glyph, sides))
    }'''
new = '''        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
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
    val kindKills = mutableMapOf<Int, Int>()'''
assert old in s, 'c2'
s = s.replace(old, new)

# C3) 击杀时记录种类
old = '''            子弹命中扣 `attackDmg`'''
if old in s:
    pass
# 找到击杀处理（hp<=0 → alive=false）实际代码
old2 = '''                    if (target != null) {'''
if old2 in s:
    pass
# 直接定位 alive=false 击杀行
old3 = '''        if (e.hp <= 0f && e.alive) {
            e.alive = false
            kills++'''
if old3 in s:
    new3 = '''        if (e.hp <= 0f && e.alive) {
            e.alive = false
            kills++
            defeatedKinds.add(e.kind)
            kindKills[e.kind] = (kindKills[e.kind] ?: 0) + 1   // 批48：图鉴记录'''
    s = s.replace(old3, new3)
    print('kill record hooked')
else:
    # 备选：搜击杀代码块
    import re
    m = re.search(r'e\.alive = false\n(\s+)kills\+\+', s)
    if m:
        s = s.replace(m.group(0), m.group(0) + '\n' + m.group(1) + 'defeatedKinds.add(e.kind)\n' + m.group(1) + 'kindKills[e.kind] = (kindKills[e.kind] ?: 0) + 1   // 批48：图鉴记录')
        print('kill record hooked (regex)')
    else:
        print('WARN: kill hook 未命中')

# C4) 行为分支（找直线追踪代码并替换为按 kind 分支）
old4 = '''        // 直线追踪玩家（单位向量 × speed × dt）'''
if old4 in s:
    pass
# 实际找移动代码
import re as _re
mm = _re.search(r'(        )// [^\n]*追踪[^\n]*\n(        )for \(e in enemies\) \{\n', s)
if mm:
    print('movement block found via marker')
else:
    print('movement marker not found; 用行号探测')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame phase1 ok')
