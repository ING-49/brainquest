# -*- coding: utf-8 -*-
"""批50 补齐：b6-b9（b1-b5/Sfx 已应用）"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# b6 玩家受击
old = '''            if (invincible <= 0f) {
                val rr = e.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    hp -= e.dmg
                    invincible = invincibleSec'''
new = '''            if (invincible <= 0f) {
                val rr = e.r + playerR
                if (dx * dx + dy * dy <= rr * rr) {
                    hp -= e.dmg
                    invincible = invincibleSec
                    hurtFlash = 0.3f; shake = 0.15f; sfxHurt++   // 批50：受击反馈'''
if 'hurtFlash = 0.3f' in s:
    print('b6 already')
else:
    assert old in s, 'b6'
    s = s.replace(old, new)
    print('b6 ok')

# b7 经验球拾取
old = '''            if (d2 <= rr * rr) {
                o.alive = false
                gainXp(o.value)'''
new = '''            if (d2 <= rr * rr) {
                o.alive = false
                sfxOrb++   // 批50：拾取音
                gainXp(o.value)'''
if 'sfxOrb++' in s:
    print('b7 already')
else:
    assert old in s, 'b7'
    s = s.replace(old, new)
    print('b7 ok')

# b8 发射
old = '''                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
                    attackTimer = attackInterval'''
new = '''                    bullets.add(Bullet(px, py, dx / d * bulletSpeed, dy / d * bulletSpeed))
                    sfxShoot++   // 批50：发射音
                    attackTimer = attackInterval'''
if 'sfxShoot++' in s:
    print('b8 already')
else:
    assert old in s, 'b8'
    s = s.replace(old, new)
    print('b8 ok')

# b9 升级金环
old = '''            pendingUpgrades = rollUpgrades()
            phase = Phase.LEVELUP
        }'''
new = '''            pendingUpgrades = rollUpgrades()
            // 批50：升级金环演出（Screen 每帧递减渲染）
            levelRingT = 0.5f; levelRingX = px; levelRingY = py
            phase = Phase.LEVELUP
        }'''
if 'levelRingT = 0.5f' in s:
    print('b9 already')
else:
    assert old in s, 'b9'
    s = s.replace(old, new)
    print('b9 ok')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('done')
