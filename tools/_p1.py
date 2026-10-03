# -*- coding: utf-8 -*-
"""补丁 1：事件消费 + 挥砍强化 + 摇杆/按钮（跑完即删）"""
import io
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()

old = '''                    4 -> { rings.add(RingFx(ev.x, ev.y, GamePalette.BOSS_GLOW, 220f, 0.6f)); DungeonSfx.play(context, player.soundOn, R.raw.dg_boss, 0.8f, 1500) }'''
new = '''                    4 -> { rings.add(RingFx(ev.x, ev.y, GamePalette.BOSS_GLOW, 220f, 0.6f)); DungeonSfx.play(context, player.soundOn, R.raw.dg_boss, 0.8f, 1500) }
                    8 -> rings.add(RingFx(ev.x, ev.y, Color(0xB0ECEFF1), 70f, 0.22f))
                    9 -> if (parts.size < 300) {
                        val mc = when (ev.element) {
                            com.brainquest.game.game.core.Element.FIRE -> GamePalette.ELEM_FIRE
                            com.brainquest.game.game.core.Element.ICE -> GamePalette.ELEM_ICE
                            else -> GamePalette.UI_TEXT
                        }
                        repeat(3) {
                            val ang = rngFx.nextFloat() * 6.283f
                            val sp = 40f + rngFx.nextFloat() * 80f
                            parts.add(obtainPart().also { it.set(ev.x, ev.y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, 0.18f, mc) })
                        }
                    }'''
assert s.count(old) == 1, 'kinds'
s = s.replace(old, new)

old = '''                if (alpha > 0.02f && sweep > 1f) {
                    drawArc(
                        Color(0x88FFFFFF).copy(alpha = alpha),
                        startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(psx - 95f, psy - 95f),
                        size = Size(190f, 190f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(8f),
                    )
                }'''
new = '''                if (alpha > 0.02f && sweep > 1f) {
                    // 攻击范围指示：扇形微光填充 + 亮弧边
                    drawArc(
                        Color(0x33FFFFFF).copy(alpha = alpha),
                        startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(psx - 95f, psy - 95f),
                        size = Size(190f, 190f),
                    )
                    drawArc(
                        Color(0x88FFFFFF).copy(alpha = alpha),
                        startAngle = Math.toDegrees(en.facing.toDouble()).toFloat() - 50f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(psx - 95f, psy - 95f),
                        size = Size(190f, 190f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(12f),
                    )
                }'''
assert s.count(old) == 1, 'slash'
s = s.replace(old, new)

old = '''            // 虚拟摇杆：固定左下底座常显
            val jb = Offset(96.dp.toPx(), size.height - 96.dp.toPx())
            drawCircle(JOY_C, 56f, jb, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            drawCircle(
                JOY_C, 26f,'''
new = '''            // 虚拟摇杆：固定左下底座常显（加大更醒目）
            val jb = Offset(110.dp.toPx(), size.height - 100.dp.toPx())
            drawCircle(Color(0x33FFFFFF), 78f, jb)
            drawCircle(JOY_C, 72f, jb, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            drawCircle(
                JOY_C, 32f,'''
assert s.count(old) == 1, 'joy'
s = s.replace(old, new)

old = '''            Column(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {'''
new = '''            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {'''
assert s.count(old) == 1, 'btnpos'
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8').write(s)
print('p1 ok')
