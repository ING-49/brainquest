# -*- coding: utf-8 -*-
"""批43 编辑：地牢房型地面元素 + Boss 差异化（名字/盔甲色相按层轮换）"""
import io

# ============ DungeonGame.kt ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonGame.kt'
s = io.open(p, encoding='utf-8').read()

# 1) bossName 字段（挂 pendingPerks 附近）
anchor = '    var pendingPerks: Map<String, Int> = emptyMap()'
assert anchor in s, 'dg perks'
s = s.replace(anchor, anchor + '''
    // 批43：Boss 差异化——名字按层数轮换（HUD 名牌/出场横幅用）
    var bossName: String = "深渊领主"''')

# 2) Boss 生成时定名
old = '''                boss.bossFloor = floor
                boss.dormant = true'''
new = '''                boss.bossFloor = floor
                bossName = BOSS_NAMES[(floor - 1).mod(BOSS_NAMES.size)]   // 批43：按层轮换
                boss.dormant = true'''
assert old in s, 'dg spawn'
s = s.replace(old, new)

# 3) 出场横幅带名字
old = '''        engine.events.add(CombatEngine.FxEvent(e.x, e.y, "第${floor}层 · 魔王现身", false, null, 7))   // 出场横幅（屏幕层渲染）'''
new = '''        engine.events.add(CombatEngine.FxEvent(e.x, e.y, "第${floor}层 · ${bossName} 现身", false, null, 7))   // 出场横幅（屏幕层渲染）'''
assert old in s, 'dg banner'
s = s.replace(old, new)

# 4) BOSS_NAMES 表（companion object 里）
old = '''        const val MAX_ENEMIES = 150'''
if old not in s:
    # DungeonGame 没有 companion MAX_ENEMIES（那是幸存者）——找 DungeonGame 的 companion 或文件级常量区
    # 挂在类内任意 const 区：找 "companion object" 
    old2 = '    companion object {'
    assert old2 in s, 'dg companion'
    s = s.replace(old2, old2 + '''
        /** 批43：Boss 名字按层数轮换（4 层一循环） */
        val BOSS_NAMES = listOf("深渊领主", "腐沼之主", "炎狱魔将", "寒渊暴君")''', 1)
else:
    s = s.replace(old, old + '''
        /** 批43：Boss 名字按层数轮换（4 层一循环） */
        val BOSS_NAMES = listOf("深渊领主", "腐沼之主", "炎狱魔将", "寒渊暴君")''')
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DungeonGame ok')

# ============ EntityRenderer.kt ============
p2 = 'app/src/main/java/com/brainquest/game/game/dungeon/render/EntityRenderer.kt'
s2 = io.open(p2, encoding='utf-8').read()

# 5) 房型地面元素（插在墙描边之前、装饰之后——画在地面层）
old5 = '''        // 墙（描边）+ 墙顶亮边 + 墙体投影暗带（立体感）'''
new5 = '''        // 批43：房型地面元素——进房/隔门望即感知类型（SHOP 金环 / EVENT 紫法阵 / CHEST 淡金光斑）
        val gcx = l + w / 2f
        val gcy = t + h / 2f
        when (room.type) {
            RoomType.SHOP -> {
                drawCircle(Color(0x30FFC107), 200f, Offset(gcx, gcy))
                drawCircle(Color(0x55FFC107), 160f, Offset(gcx, gcy), style = Stroke(6f))
            }
            RoomType.EVENT -> {
                drawCircle(Color(0x407C4DFF), 190f, Offset(gcx, gcy), style = Stroke(5f))
                drawCircle(Color(0x307C4DFF), 130f, Offset(gcx, gcy), style = Stroke(3f))
            }
            RoomType.CHEST -> {
                drawCircle(Color(0x2AFFC107), 140f, Offset(gcx, gcy))
            }
            else -> {}
        }
        // 墙（描边）+ 墙顶亮边 + 墙体投影暗带（立体感）'''
assert old5 in s2, 'er room'
s2 = s2.replace(old5, new5)

# 6) Boss 盔甲色相按层轮换（狂暴红变保留）
old6 = '''        val rage = e.phase >= 2
        val body = if (rage) androidx.compose.ui.graphics.lerp(Color(0xFF8E2424), GamePalette.BOSS_GLOW, 0.3f + 0.1f * sin(time * 10f)) else Color(0xFF6D2A2A)
        val dark = Color(0xFF3A1414)'''
new6 = '''        val rage = e.phase >= 2
        // 批43：盔甲色相按层轮换（深渊红/腐沼绿/炎狱橙/寒渊蓝），狂暴期整体向 BOSS_GLOW 变亮
        val floorArmor = listOf(
            Color(0xFF6D2A2A) to Color(0xFF3A1414),   // 深渊领主
            Color(0xFF2E5333) to Color(0xFF14261A),   // 腐沼之主
            Color(0xFF7A3212) to Color(0xFF3A1A0A),   // 炎狱魔将
            Color(0xFF1F4E79) to Color(0xFF0D2438),   // 寒渊暴君
        )[(e.bossFloor - 1).mod(4)]
        val body = if (rage) androidx.compose.ui.graphics.lerp(floorArmor.first, GamePalette.BOSS_GLOW, 0.3f + 0.1f * sin(time * 10f)) else floorArmor.first
        val dark = floorArmor.second'''
assert old6 in s2, 'er boss'
s2 = s2.replace(old6, new6)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('EntityRenderer ok')

# ============ DungeonScreen.kt HUD 名 ============
p3 = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s3 = io.open(p3, encoding='utf-8').read()
old7 = '''                        "第${hud.floor}层 · 深渊领主",'''
new7 = '''                        "第${hud.floor}层 · ${game.bossName}",   // 批43：按层轮换'''
assert old7 in s3, 'ds hud'
s3 = s3.replace(old7, new7)
io.open(p3, 'w', encoding='utf-8', newline='').write(s3)
print('DungeonScreen ok')
