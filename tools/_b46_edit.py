# -*- coding: utf-8 -*-
"""批46 编辑：地牢 Boss 形态分化——腐沼触须 / 炎狱双角 / 寒渊冰晶（按层叠加形态元素）"""
import io

p = 'app/src/main/java/com/brainquest/game/game/dungeon/render/EntityRenderer.kt'
s = io.open(p, encoding='utf-8').read()

# drawBossBody 末尾（王冠之后）追加按层形态元素
old = '''            // 王冠（金色三尖）
            val crownY = headY - r * 0.42f
            drawRect(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Size(36f, 8f))
            drawLine(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Offset(cx - 14f, crownY - 20f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx, crownY - 8f), Offset(cx, crownY - 24f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx + 18f, crownY - 8f), Offset(cx + 14f, crownY - 20f), 5f)
        }
    }'''
new = '''            // 王冠（金色三尖）
            val crownY = headY - r * 0.42f
            drawRect(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Size(36f, 8f))
            drawLine(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Offset(cx - 14f, crownY - 20f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx, crownY - 8f), Offset(cx, crownY - 24f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx + 18f, crownY - 8f), Offset(cx + 14f, crownY - 20f), 5f)
            // 批46：按层形态分化——腐沼触须(2) / 炎狱双角(3) / 寒渊冰晶(4)；深渊领主(1)为基础形态
            val sway2 = sway
            when ((e.bossFloor - 1).mod(4)) {
                1 -> {   // 腐沼之主：体侧两条摆动触须
                    for (t in 0..1) {
                        val side = if (t == 0) -1f else 1f
                        val bx = sx + side * r * 0.75f + sway2
                        val by = sy - r * 0.1f
                        val wag = sin(time * 3f + t * 2.1f) * r * 0.18f
                        drawLine(dark, Offset(bx, by), Offset(bx + side * r * 0.42f, by + r * 0.18f + wag), 7f)
                        drawLine(dark, Offset(bx + side * r * 0.42f, by + r * 0.18f + wag), Offset(bx + side * r * 0.6f, by + r * 0.42f + wag * 1.6f), 5f)
                    }
                }
                2 -> {   // 炎狱魔将：双角（头两侧上翘三角线）
                    val hx = cx
                    val hy = headY - r * 0.30f
                    drawLine(Color(0xFFFF7043), Offset(hx - r * 0.30f, hy), Offset(hx - r * 0.5f, hy - r * 0.5f), 6f)
                    drawLine(Color(0xFFFF7043), Offset(hx + r * 0.30f, hy), Offset(hx + r * 0.5f, hy - r * 0.5f), 6f)
                    drawLine(Color(0xFFFFAB91), Offset(hx - r * 0.30f, hy), Offset(hx - r * 0.44f, hy - r * 0.34f), 3f)
                    drawLine(Color(0xFFFFAB91), Offset(hx + r * 0.30f, hy), Offset(hx + r * 0.44f, hy - r * 0.34f), 3f)
                }
                3 -> {   // 寒渊暴君：周身旋转冰晶（四个小菱形环绕）
                    for (k in 0..3) {
                        val ang = time * 1.3f + k * 1.5707963f
                        val ix = cx + cos(ang) * r * 1.05f
                        val iy = sy + sin(ang) * r * 0.85f
                        drawLine(Color(0xFF80DEEA), Offset(ix, iy - 9f), Offset(ix + 7f, iy), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix + 7f, iy), Offset(ix, iy + 9f), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix, iy + 9f), Offset(ix - 7f, iy), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix - 7f, iy), Offset(ix, iy - 9f), 3f)
                    }
                }
            }
        }
    }'''
assert old in s, 'boss body'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('Boss shapes ok')
