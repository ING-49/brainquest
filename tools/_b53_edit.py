# -*- coding: utf-8 -*-
"""批53 编辑：幸存者火柴人主角 + 数学空间美术 + 演出补齐"""
import io

# ============ A. SurvivorGame：朝向字段 + 攻击进度助手 ============
p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    var hurtFlash = 0f              // 玩家受击红晕（Screen 渲染衰减）'''
new = '''    var faceX = 0f; var faceY = -1f   // 批53：火柴人朝向（最后移动方向）
    var hurtFlash = 0f              // 玩家受击红晕（Screen 渲染衰减）'''
assert old in s, 'g1'
s = s.replace(old, new)

old = '''        // 玩家移动
        if (joyActive && (joyX != 0f || joyY != 0f)) {
            px += joyX * moveSpeed * dt
            py += joyY * moveSpeed * dt
        }'''
new = '''        // 玩家移动
        if (joyActive && (joyX != 0f || joyY != 0f)) {
            px += joyX * moveSpeed * dt
            py += joyY * moveSpeed * dt
            faceX = joyX; faceY = joyY   // 批53：更新火柴人朝向
        }'''
assert old in s, 'g2'
s = s.replace(old, new)

old = '''    /** 已生存秒数（HUD 用） */
    fun timeSec(): Int = elapsed.toInt()'''
new = '''    /** 已生存秒数（HUD 用） */
    fun timeSec(): Int = elapsed.toInt()

    /** 批53：攻击进度 0→1（火柴人攻击姿态用） */
    fun attackProg(): Float = ((attackInterval - attackTimer) / attackInterval).coerceIn(0f, 1f)'''
assert old in s, 'g3'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame ok')

# ============ B. SurvivorScreen：火柴人玩家（替换双圆） ============
p2 = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()
old2 = '''            // 玩家（无敌帧期间闪烁）
            val psx = game.px - game.camX
            val psy = game.py - game.camY
            val blink = game.invincibleNow() > 0f && (game.elapsed * 12f).toInt() % 2 == 0
            if (!blink) {
                drawCircle(PLAYER_C, game.playerR, Offset(psx, psy))
                drawCircle(Color.White, game.playerR - 5f, Offset(psx, psy))
            }'''
new2 = '''            // 玩家（批53：程序化发光火柴人——《火柴人vs数学》美术本体）
            val psx = game.px - game.camX
            val psy = game.py - game.camY
            val blink = game.invincibleNow() > 0f && (game.elapsed * 12f).toInt() % 2 == 0
            if (!blink) {
                val hf = (game.hurtFlash / 0.3f).coerceIn(0f, 1f)
                val moving = game.joyActive && (game.joyX != 0f || game.joyY != 0f)
                val walk = game.elapsed * 10f
                val swing = if (moving) 1f else 0f
                val bob = if (moving) kotlin.math.sin(walk * 2f) * 1.5f else kotlin.math.sin(game.elapsed * 2.2f) * 1.2f
                val hipY = psy - 11f + bob * 0.4f
                val neckY = psy - 22f + bob
                val headY = neckY - 7.5f + bob
                val legSwing = kotlin.math.sin(walk) * 7f * swing
                val armSwing = kotlin.math.sin(walk + 3.14159f) * 5.5f * swing
                val atkP = game.attackProg()
                val fx = game.faceX; val fy = game.faceY
                val fl = kotlin.math.hypot(fx, fy).coerceAtLeast(0.001f)
                val dirX = fx / fl; val dirY = fy / fl
                val segs = mutableListOf<Pair<Offset, Offset>>()
                val neck = Offset(psx, neckY)
                val hip = Offset(psx, hipY)
                segs.add(neck to hip)   // 躯干
                val knee1 = Offset(psx + legSwing * 0.55f, hipY + 5.5f)
                val foot1 = Offset(knee1.x + legSwing * 0.5f, psy)
                val knee2 = Offset(psx - legSwing * 0.55f, hipY + 5.5f)
                val foot2 = Offset(knee2.x - legSwing * 0.5f, psy)
                segs.add(hip to knee1); segs.add(knee1 to foot1)
                segs.add(hip to knee2); segs.add(knee2 to foot2)
                val shoulder = Offset(neck.x, neck.y + 1.5f)
                val attacking = atkP < 0.25f
                if (attacking) {
                    // 批53：攻击姿态——前臂伸向目标方向（射击方向即朝向）
                    val hand = Offset(neck.x + dirX * 15f, neck.y + dirY * 15f - 3f)
                    val elbow = Offset((neck.x + hand.x) / 2f, (neck.y + hand.y) / 2f + 2f)
                    segs.add(shoulder to elbow); segs.add(elbow to hand)
                    val arm2Elbow = Offset(neck.x - dirX * 4f - armSwing * 0.4f, neck.y + 6f)
                    val arm2Hand = Offset(arm2Elbow.x - 3f, arm2Elbow.y + 7f)
                    segs.add(shoulder to arm2Elbow); segs.add(arm2Elbow to arm2Hand)
                } else {
                    val el1 = Offset(shoulder.x + armSwing * 0.5f, shoulder.y + 5.5f)
                    val hd1 = Offset(el1.x + armSwing * 0.45f, el1.y + 6f)
                    segs.add(shoulder to el1); segs.add(el1 to hd1)
                    val el2 = Offset(shoulder.x - armSwing * 0.5f, shoulder.y + 5.5f)
                    val hd2 = Offset(el2.x - armSwing * 0.45f, el2.y + 6f)
                    segs.add(shoulder to el2); segs.add(el2 to hd2)
                }
                // 受击挤压拉伸（绕脚底缩放，衰减随 hurtFlash）
                withTransform({
                    scale(1f + 0.12f * hf, 1f - 0.16f * hf, pivot = Offset(psx, psy))
                }) {
                    // 光晕层（粗线低透明）→ 主干层（细线）——火柴人是发光体
                    for (seg in segs) {
                        drawLine(PLAYER_C.copy(alpha = 0.30f), seg.first, seg.second, 8f)
                    }
                    for (seg in segs) {
                        drawLine(PLAYER_C, seg.first, seg.second, 3.5f)
                    }
                    // 头：发光圆 + 白核（朝向侧微偏）
                    val headPos = Offset(psx + dirX * 1.5f, headY)
                    drawCircle(PLAYER_C.copy(alpha = 0.30f), 9.5f, headPos)
                    drawCircle(PLAYER_C, 6.5f, headPos)
                    drawCircle(Color.White, 2.6f, Offset(headPos.x - 1f, headPos.y - 1.5f))
                    // 关节点（肘/膝小亮点）
                    for (j in listOf(knee1, knee2)) drawCircle(Color.White.copy(alpha = 0.5f), 1.5f, j)
                }
            }'''
assert old2 in s2, 'b1'
s2 = s2.replace(old2, new2)
if 'import androidx.compose.ui.graphics.drawscope.withTransform' not in s2:
    s2 = s2.replace('import androidx.compose.ui.text.drawText', 'import androidx.compose.ui.graphics.drawscope.withTransform\nimport androidx.compose.ui.text.drawText')

# B2) 场景：远景漂浮几何线框（视差 0.35，wrap 环绕）
old3 = '''            // 背景网格（批42：数学坐标纸——每 5 格一条主格线，过暗不抢主体）'''
new3 = '''            // 批53：远景漂浮几何线框（空间层次：远景层，视差 0.35）
            for (i in 0..2) {
                val seed = i * 77.7f
                val spanW = size.width + 400f
                val spanH = size.height + 300f
                val baseX = (seed * 13.7f) % spanW
                val baseY = (seed * 7.3f) % spanH
                val px2 = ((baseX - game.camX * 0.35f + kotlin.math.sin(game.elapsed * 0.13f + seed) * 40f) % spanW + spanW) % spanW - 200f
                val py2 = ((baseY - game.camY * 0.35f + kotlin.math.cos(game.elapsed * 0.11f + seed) * 30f) % spanH + spanH) % spanH - 150f
                val wr = 90f + i * 40f
                val sides2 = 3 + i
                val wpath = androidx.compose.ui.graphics.Path()
                for (k in 0 until sides2) {
                    val ang2 = -1.5707963f + k * (6.2831853f / sides2)
                    val vx2 = px2 + kotlin.math.cos(ang2) * wr
                    val vy2 = py2 + kotlin.math.sin(ang2) * wr
                    if (k == 0) wpath.moveTo(vx2, vy2) else wpath.lineTo(vx2, vy2)
                }
                wpath.close()
                drawPath(wpath, Color(0xFF3D5A80).copy(alpha = 0.10f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
            // 背景网格（批42：数学坐标纸——每 5 格一条主格线，过暗不抢主体）'''
assert old3 in s2, 'b2'
s2 = s2.replace(old3, new3)

# B3) 装饰符号三重变化（hash 格心：大小/角度/透明度）
old4 = '''            // 批42：X/Y 坐标轴与原点（世界 0,0）——"数学空间"的锚'''
idx = s2.find(old4)
assert idx > 0, 'b3 anchor'
# 在轴绘制之前插入符号装饰段
deco = '''            // 批53：格心装饰符号（hash 恒定：大小/角度/透明度三重变化，集中在轴附近引导视线）
            val symPool = listOf("π", "√", "∑", "∞", "∆", "+", "−", "×", "÷")
            var dgy = -game.camY % (step * 2)
            if (dgy < 0) dgy += step * 2
            while (dgy < size.height) {
                var dgx = -game.camX % (step * 2)
                if (dgx < 0) dgx += step * 2
                while (dgx < size.width) {
                    val wx2 = dgx + game.camX; val wy2 = dgy + game.camY
                    val h2 = ((wx2.toInt() * 73856093) xor (wy2.toInt() * 19349663))
                    if ((h2 ushr 5) % 100 < 22) {
                        val g2 = symPool[(h2 ushr 9) % symPool.size]
                        val rot2 = ((h2 ushr 13) % 30 - 15) * 0.0174f
                        val sc2 = 9 + (h2 ushr 17) % 7
                        val lay2 = glyphMetrics.getOrPut("deco$g2${sc2 / 4}") {
                            textMeasurer.measure(g2, TextStyle(fontSize = sc2.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        }
                        withTransform({
                            translate(dgx + step - 4f, dgy + step - 4f)
                            rotate(rot2, pivot = Offset.Zero)
                        }) {
                            drawText(lay2, color = Color(0x14FFFFFF), topLeft = Offset(-lay2.size.width / 2f, -lay2.size.height / 2f))
                        }
                    }
                    dgx += step * 2
                }
                dgy += step * 2
            }
            // X/Y 坐标轴（世界 0,0）与原点'''
s2 = s2.replace(old4, deco, 1)

io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('scene ok')
