# -*- coding: utf-8 -*-
"""批51 编辑：地牢粒子物理真修复 + dg_shoot/击杀音 + 金币翻转 + 装备光柱旋转 + 火球爆点"""
import io

# ============ A. 粒子物理真修复（DungeonScreen 推进处） ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()
old = '''            // delay 粒子（t<0）：只计时不动不画（暴风雪冰锥落地迸溅用）
            parts.forEach { it.t += dtFx; if (it.t >= 0f) { it.x += it.vx * dtFx; it.y += it.vy * dtFx } }'''
new = '''            // delay 粒子（t<0）：只计时不动不画（暴风雪冰锥落地迸溅用）
            // 批51：重力/阻尼字段真正生效——碎片带 g 落地、风压粒子 drag 衰减（此前生成了却从未消费）
            parts.forEach {
                it.t += dtFx
                if (it.t >= 0f) {
                    if (it.g != 0f) it.vy += it.g * dtFx
                    if (it.drag != 0f) {
                        val damp = kotlin.math.exp(-it.drag * dtFx)
                        it.vx *= damp; it.vy *= damp
                    }
                    it.x += it.vx * dtFx; it.y += it.vy * dtFx
                }
            }'''
assert old in s, 'a'
s = s.replace(old, new)

# B. dg_shoot 接入（kind 9 枪口火光）
old2 = '''                    9 -> if (parts.size < DungeonSettings.particleCap(context)) {'''
new2 = '''                    9 -> {
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_shoot, 0.3f, 120)   // 批51：射击音（dg_shoot 此前闲置）
                        if (parts.size < DungeonSettings.particleCap(context)) {'''
assert old2 in s, 'b'
s = s.replace(old2, new2)
# 补该 case 的收尾花括号（原 if 块结束后加一层）
old2b = '''                        repeat(3) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 40f + rngFx.nextFloat() * 80f
                            parts.add(obtainPart().also { it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.18f, mc) })
                        }
                    }'''
new2b = '''                        repeat(3) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 40f + rngFx.nextFloat() * 80f
                            parts.add(obtainPart().also { it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.18f, mc) })
                        }
                        }
                    }'''
assert old2b in s, 'b2'
s = s.replace(old2b, new2b)

# C. 击杀音（kind 1 死亡碎片消费处）
old3 = '''                    1 -> {   // 死亡碎片化：三角碎片旋转飞散（按敌人主色，带重力）+ 灵魂光点飞向玩家'''
new3 = '''                    1 -> {   // 死亡碎片化：三角碎片旋转飞散（按敌人主色，带重力）+ 灵魂光点飞向玩家
                        DungeonSfx.play(context, player.soundOn, R.raw.dg_hit, 0.3f, 150)   // 批51：击杀音（此前无）'''
assert old3 in s, 'c'
s = s.replace(old3, new3)

# D. 火球爆点（ENGINE：FIRE 弹命中且耗尽穿透时发 kind 18 爆点）
p2 = 'app/src/main/java/com/brainquest/game/game/core/CombatEngine.kt'
s2 = io.open(p2, encoding='utf-8').read()
old4 = '''                    if (b.pierce > 0) b.pierce-- else { b.alive = false }
                    break
                }
            }
        }
    }'''
new4 = '''                    // 批51：火球命中爆点（耗尽穿透时）——复用 kind 18 爆裂连锁渲染
                    if (b.element == Element.FIRE && b.pierce == 0) {
                        events.add(FxEvent(e.x, e.y, "", false, Element.FIRE, 18))
                    }
                    if (b.pierce > 0) b.pierce-- else { b.alive = false }
                    break
                }
            }
        }
    }'''
assert old4 in s2, 'd'
s2 = s2.replace(old4, new4)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('engine fire boom ok')

# ============ E. 金币翻转 + 磁吸拖尾（EntityRenderer drawCoins） ============
p3 = 'app/src/main/java/com/brainquest/game/game/dungeon/render/EntityRenderer.kt'
s3 = io.open(p3, encoding='utf-8').read()
old5 = '''    fun drawCoins(scope: DrawScope, game: DungeonGame, time: Float) {
        for (c in game.engine.coinDrops) {
            val sx = c.x - game.camX
            val sy = c.y - game.camY + kotlin.math.sin(time * 6f + c.x * 0.05f) * 2f
            scope.drawCircle(Color(0xFFD97706), 7f, Offset(sx, sy))
            scope.drawCircle(Color(0xFFFDE68A), 2.8f, Offset(sx - 1.6f, sy - 1.6f))
            scope.drawCircle(Color(0xFFD97706), 7f, Offset(sx, sy), style = Stroke(1.5f))
        }
    }'''
new5 = '''    fun drawCoins(scope: DrawScope, game: DungeonGame, time: Float) {
        for (c in game.engine.coinDrops) {
            val sx = c.x - game.camX
            val sy = c.y - game.camY + kotlin.math.sin(time * 6f + c.x * 0.05f) * 2f
            // 批51：硬币翻转（宽度按 cos 收缩模拟旋转）+ 磁吸拖尾短线
            val spin = kotlin.math.cos(time * 7f + c.x * 0.13f)
            val rw = 7f * kotlin.math.abs(spin).coerceAtLeast(0.25f)
            if (c.magnet) {
                val dx = game.px - c.x; val dy = game.py - c.y
                val dl = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                scope.drawLine(Color(0x66FDE68A), Offset(sx, sy), Offset(sx + dx / dl * 26f, sy + dy / dl * 26f), 3f)
            }
            scope.drawOval(Color(0xFFD97706), Offset(sx - rw, sy - 7f), Size(rw * 2, 14f))
            scope.drawOval(Color(0xFFFDE68A), Offset(sx - rw * 0.4f, sy - 2.8f), Size(rw * 0.8f, 5.6f))
            scope.drawOval(Color(0xFFD97706), Offset(sx - rw, sy - 7f), Size(rw * 2, 14f), style = Stroke(1.5f))
        }
    }'''
assert old5 in s3, 'e'
s3 = s3.replace(old5, new5)

# F. 装备光柱 + 缓旋（drawDrops）
old6 = '''            val c = Color(d.item.rarityColorLong)
            val pulse = 1f + sin(time * 5f + d.t) * 0.15f
            val r = 10f * pulse
            scope.drawLine(c, Offset(sx, sy - r), Offset(sx + r, sy), 4f)
            scope.drawLine(c, Offset(sx + r, sy), Offset(sx, sy + r), 4f)
            scope.drawLine(c, Offset(sx, sy + r), Offset(sx - r, sy), 4f)
            scope.drawLine(c, Offset(sx - r, sy), Offset(sx, sy - r), 4f)
            scope.drawCircle(SHADOW, 8f, Offset(sx, sy + 12f))'''
new6 = '''            val c = Color(d.item.rarityColorLong)
            val pulse = 1f + sin(time * 5f + d.t) * 0.15f
            val r = 10f * pulse
            // 批51：品质色光柱（向上渐隐）+ 菱形缓旋——掉落更有存在感
            scope.drawRect(
                Brush.verticalGradient(listOf(c.copy(alpha = 0.05f), c.copy(alpha = 0.30f))),
                Offset(sx - 7f, sy - 90f), Size(14f, 90f),
            )
            scope.withTransform({
                translate(sx, sy)
                rotate(time * 1.2f, pivot = Offset.Zero)
            }) {
                scope.drawLine(c, Offset(0f, -r), Offset(r, 0f), 4f)
                scope.drawLine(c, Offset(r, 0f), Offset(0f, r), 4f)
                scope.drawLine(c, Offset(0f, r), Offset(-r, 0f), 4f)
                scope.drawLine(c, Offset(-r, 0f), Offset(0f, -r), 4f)
            }
            scope.drawCircle(SHADOW, 8f, Offset(sx, sy + 12f))'''
assert old6 in s3, 'f'
s3 = s3.replace(old6, new6)
# Brush/translate/rotate imports
if 'import androidx.compose.ui.graphics.Brush' not in s3:
    s3 = s3.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color')
io.open(p3, 'w', encoding='utf-8', newline='').write(s3)
print('EntityRenderer ok')
