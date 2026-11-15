# -*- coding: utf-8 -*-
"""批48 收尾：行为分支 + gameTime + spawnElite kind=3"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 行为分支替换（原直线追踪 → 按 kind 分支）
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
            }'''
if '按种类分支行为' not in s:
    assert old2 in s, 'movement'
    s = s.replace(old2, new2)
    print('movement ok')

# 2) spawnElite kind=3
old4 = '''        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, 32f, hp, hp, 42f + min(elapsed * 0.2f, 25f), contactBaseDmg * 1.5f, "∑", 6))'''
if 'contactBaseDmg * 1.5f, "∑", 6, 3)' not in s and old4 in s:
    s = s.replace(old4, old4.replace('"∑", 6))', '"∑", 6, 3))'))
    print('elite kind ok')

# 3) gameTime 不需要了（直接用 elapsed，同类内可见）——检查 elapsed 声明可见性
print('elapsed decl:', [l.strip() for l in s.splitlines() if 'var elapsed' in l])

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('done')
