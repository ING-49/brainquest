# -*- coding: utf-8 -*-
"""批50 屏幕层：死亡粒子/拖尾/经验球脉动/命中白闪/受击红晕/升级金环/LEVELUP入场/音效差量"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 经验球脉动 + 光晕
old = '''            // 经验球
            for (o in game.orbs) {
                if (!o.alive) continue
                val sx = o.x - game.camX; val sy = o.y - game.camY
                if (sx < -20f || sy < -20f || sx > size.width + 20f || sy > size.height + 20f) continue
                drawCircle(ORB_C, game.orbR, Offset(sx, sy))
            }'''
new = '''            // 经验球（批50：脉动 + 磁吸光晕 + 大球渲染）
            for (o in game.orbs) {
                if (!o.alive) continue
                val sx = o.x - game.camX; val sy = o.y - game.camY
                if (sx < -20f || sy < -20f || sx > size.width + 20f || sy > size.height + 20f) continue
                val pulse = 0.85f + 0.25f * kotlin.math.sin(game.elapsed * 5f + o.x * 0.03f)
                val orbR = if (o.value > 1) game.orbR * 1.6f else game.orbR
                if (o.magnet) drawCircle(ORB_C.copy(alpha = 0.18f), orbR + 6f, Offset(sx, sy))   // 磁吸光晕
                drawCircle(ORB_C, orbR * pulse, Offset(sx, sy))
                drawCircle(Color(0xFFDFFFE3), orbR * pulse * 0.35f, Offset(sx - orbR * 0.2f, sy - orbR * 0.2f))   // 高光
            }

            // 批50：死亡粒子迸溅（按 kind 配色，8 颗放射 + 中心白闪，寿命 0.35s）
            for (d in game.deathFx) {
                val k = (d.age / 0.35f).coerceIn(0f, 1f)
                val dcol = Color(d.color)
                val cx = d.x - game.camX; val cy = d.y - game.camY
                for (i in 0..7) {
                    val ang = i * 0.7853981f + d.seed % 16 * 0.1f
                    val dist = d.age * (140f + (i % 3) * 60f)
                    drawCircle(dcol.copy(alpha = (1f - k) * 0.9f), 3.5f * (1f - k * 0.6f), Offset(cx + kotlin.math.cos(ang) * dist, cy + kotlin.math.sin(ang) * dist))
                }
                if (d.age < 0.12f) {
                    val fw = 1f - d.age / 0.12f
                    drawCircle(Color.White.copy(alpha = fw * 0.85f), 18f * fw, Offset(cx, cy))
                }
            }'''
assert old in s, 'v1'
s = s.replace(old, new)

# 2) 敌人命中白闪（血条前插入）
old = '''                if (e.hp < e.maxHp) {
                    val w = e.r * 2f
                    drawRect(BAR_BG, Offset(sx - e.r, sy - e.r - 8f), Size(w, 3f))
                    drawRect(HP_C, Offset(sx - e.r, sy - e.r - 8f), Size(w * (e.hp / e.maxHp), 3f))
                }'''
new = '''                // 批50：命中白闪（hitFlash 0.1s）
                if (e.hitFlash > 0f) {
                    drawCircle(Color.White.copy(alpha = (e.hitFlash * 6f).coerceAtMost(0.8f)), e.r + 2f, Offset(sx, sy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
                if (e.hp < e.maxHp) {
                    val w = e.r * 2f
                    drawRect(BAR_BG, Offset(sx - e.r, sy - e.r - 8f), Size(w, 3f))
                    drawRect(HP_C, Offset(sx - e.r, sy - e.r - 8f), Size(w * (e.hp / e.maxHp), 3f))
                }'''
assert old in s, 'v2'
s = s.replace(old, new)

# 3) 玩家拖尾（玩家绘制之前）
old = '''            // 玩家（无敌帧期间闪烁）
            val psx = game.px - game.camX
            val psy = game.py - game.camY'''
new = '''            // 批50：移动拖尾（渐隐青圆，越旧越淡越小）
            for (t in game.trail) {
                val tx = t.x - game.camX; val ty = t.y - game.camY
                val a = 1f - (t.age / 0.3f).coerceIn(0f, 1f)
                drawCircle(PLAYER_C.copy(alpha = 0.22f * a), game.playerR * (0.35f + 0.4f * a), Offset(tx, ty))
            }

            // 玩家（无敌帧期间闪烁）
            val psx = game.px - game.camX
            val psy = game.py - game.camY'''
assert old in s, 'v3'
s = s.replace(old, new)

# 4) 升级金环 + 受击红晕（摇杆之后、Canvas 结束前）
old = '''            // 虚拟摇杆
            if (game.joyActive) {
                drawCircle(JOY_C, 56f, Offset(game.joyBaseX, game.joyBaseY), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(JOY_C, 26f, Offset(game.joyBaseX + game.joyX * joyMaxPx, game.joyBaseY + game.joyY * joyMaxPx))
            }

        }'''
new = '''            // 虚拟摇杆
            if (game.joyActive) {
                drawCircle(JOY_C, 56f, Offset(game.joyBaseX, game.joyBaseY), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                drawCircle(JOY_C, 26f, Offset(game.joyBaseX + game.joyX * joyMaxPx, game.joyBaseY + game.joyY * joyMaxPx))
            }

            // 批50：升级金环（从升级点扩散，0.5s）
            if (game.levelRingT > 0f) {
                val rx = game.levelRingX - game.camX; val ry = game.levelRingY - game.camY
                val k = (game.levelRingT / 0.5f).coerceIn(0f, 1f)
                drawCircle(GameUi.Gold.copy(alpha = 0.7f * k), 170f * (1f - k) + 20f, Offset(rx, ry), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
            }

            // 批50：受击红晕（屏幕边缘，随 hurtFlash 衰减）
            if (game.hurtFlash > 0f) {
                val a = (game.hurtFlash / 0.3f).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0x00FF5252), Color(0x66FF5252).copy(alpha = 0.5f * a), Color(0x00FF5252)),
                        startY = 0f, endY = size.height,
                    )
                )
            }

        }'''
assert old in s, 'v4'
s = s.replace(old, new)

# 5) 音效差量触发（帧循环内）
old = '''            if (h != hud) hud = h
        }
    }'''
new = '''            if (h != hud) hud = h
            // 批50：反馈音效（差量触发；ToneGenerator 自带 60ms 节流）
            if (game.sfxShoot > sfxPrev[0]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.SHOOT); sfxPrev[0] = game.sfxShoot }
            if (game.sfxHit > sfxPrev[1]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.HIT); sfxPrev[1] = game.sfxHit }
            if (game.sfxDeath > sfxPrev[2]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.DEATH); sfxPrev[2] = game.sfxDeath }
            if (game.sfxOrb > sfxPrev[3]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.ORB); sfxPrev[3] = game.sfxOrb }
            if (game.sfxHurt > sfxPrev[4]) { Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.CRASH); sfxPrev[4] = game.sfxHurt }
        }
    }'''
assert old in s, 'v5'
s = s.replace(old, new)

# 6) sfxPrev 状态（frame 之后）
old = '''    val frame = remember { mutableIntStateOf(0) }'''
new = '''    val frame = remember { mutableIntStateOf(0) }
    val sfxPrev = remember { intArrayOf(0, 0, 0, 0, 0) }   // 批50：音效差量基线'''
assert old in s, 'v6'
s = s.replace(old, new)

# 7) LEVELUP 卡入场动画（遮罩 Box graphicsLayer + Animatable）
old = '''        if (hud.phase == SurvivorGame.Phase.LEVELUP) {
            Box(Modifier.fillMaxSize().background(Color(0xB305070C)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = 24.dp),'''
new = '''        if (hud.phase == SurvivorGame.Phase.LEVELUP) {
            // 批50：卡片入场动画（alpha/scale 220ms）
            val lvIn = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(hud.phase) {
                if (hud.phase == SurvivorGame.Phase.LEVELUP) {
                    lvIn.snapTo(0f)
                    lvIn.animateTo(1f, androidx.compose.animation.core.tween(220))
                }
            }
            Box(
                Modifier.fillMaxSize().background(Color(0xB305070C)).graphicsLayer {
                    alpha = 0.4f + 0.6f * lvIn.value
                    val sc = 0.92f + 0.08f * lvIn.value
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp),'''
assert old in s, 'v7'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorScreen ok')
