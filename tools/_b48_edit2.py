# -*- coding: utf-8 -*-
"""批48 编辑第二段：击杀记录钩子（真实代码锚点）+ 行为分支替换"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 击杀记录钩子（真实锚点）
old = '''                    if (e.hp <= 0f) {
                        e.alive = false
                        kills++
                        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, 1))
                    }'''
new = '''                    if (e.hp <= 0f) {
                        e.alive = false
                        kills++
                        defeatedKinds.add(e.kind)                                   // 批48：图鉴记录
                        kindKills[e.kind] = (kindKills[e.kind] ?: 0) + 1
                        if (orbs.size < MAX_ORBS) orbs.add(Orb(e.x, e.y, if (e.kind == 3) 5 else 1))
                    }'''
assert old in s, 'kill hook'
s = s.replace(old, new)

# 2) 行为分支：替换直线追踪为按 kind 分支
old2 = '''        // 敌人追踪 + 碰撞玩家（有无敌帧）
        for (e in enemies) {
            if (!e.alive) continue
            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d > 1f) {
                e.x += dx / d * e.speed * dt
                e.y += dy / d * e.speed * dt
            }'''
new2 = '''        // 敌人追踪 + 碰撞玩家（批48：按种类分支行为）
        for (e in enemies) {
            if (!e.alive) continue
            val dx = px - e.x; val dy = py - e.y
            val d = hypot(dx, dy)
            if (d > 1f) {
                val sp = when (e.kind) {
                    1 -> e.speed                          // 运算符蜂：正弦绕行（垂直摆动分量）
                    else -> {
                        // 几何狂：冲刺计时
                        if (e.kind == 2) {
                            e.dashCd -= dt
                            if (e.dashCd <= 0f && d < 420f) { e.dashT = 0.4f; e.dashCd = 3f }
                        }
                        e.speed * (if (e.dashT > 0f) 2.2f else 1f)
                    }
                }
                val base = dx / d
                // 运算符蜂：沿主方向 + 垂直正弦摆动（避开直线弹幕）
                val perpX = if (e.kind == 1) -dy / d * kotlin.math.sin(gameTime() * 4f + e.wobbleSeed) * 0.55f else 0f
                val perpY = if (e.kind == 1) dx / d * kotlin.math.sin(gameTime() * 4f + e.wobbleSeed) * 0.55f else 0f
                e.x += (base * sp + perpX) * dt
                e.y += (dy / d * sp + perpY) * dt
                if (e.dashT > 0f) e.dashT -= dt
            }'''
assert old2 in s, 'movement'
s = s.replace(old2, new2)

# 3) gameTime() 小助手（elapsed 的只读访问——检查 elapsed 可见性：类内 private var elapsed?）
if 'fun gameTime()' not in s:
    s = s.replace('''    /** 批48：击杀记录（图鉴+计数） */''',
'''    /** 批48：给行为动画用的存活时间 */
    fun gameTime(): Float = elapsed

    /** 批48：击杀记录（图鉴+计数） */''')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame behaviors ok')
