# -*- coding: utf-8 -*-
"""批42 编辑：幸存者数学空间化（坐标系纸 + 数学字形敌人 + 几何线框大怪 + 强化数学化）"""
import io

# ============ SurvivorGame.kt ============
p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorGame.kt'
s = io.open(p, encoding='utf-8').read()

# 1) Enemy 加字形/边数字段
old = '''    class Enemy(var x: Float, var y: Float, val r: Float, var hp: Float, val maxHp: Float, val speed: Float, val dmg: Float) {
        var alive = true
    }'''
new = '''    class Enemy(
        var x: Float, var y: Float, val r: Float, var hp: Float, val maxHp: Float, val speed: Float, val dmg: Float,
        // 批42：数学实体——普通怪=字形（数字/运算符/希腊字母），大怪=几何线框边数（3/4/5）
        val glyph: String = "1",
        val sides: Int = 0,
    ) {
        var alive = true
    }'''
assert old in s, 'sg enemy'
s = s.replace(old, new)

# 2) 强化数学化命名（机制不动）
old = '''    private fun rollUpgrades(): List<Upgrade> = listOf(
        Upgrade("atk", "⚔️ 攻击力", "子弹伤害 +25%"),
        Upgrade("aspd", "⚡ 攻速", "攻击间隔 −15%"),
        Upgrade("spd", "👟 移速", "移动速度 +10%"),
        Upgrade("hp", "❤️ 生命", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "🧲 拾取", "拾取范围 +30%"),
    ).shuffled(Random).take(3)'''
new = '''    private fun rollUpgrades(): List<Upgrade> = listOf(
        // 批42：强化数学化命名（参考《火柴人 vs 数学》美学；机制不变）
        Upgrade("atk", "× 系数增幅", "子弹伤害 +25%"),
        Upgrade("aspd", "d/dt 加速", "攻击间隔 −15%"),
        Upgrade("spd", "√ 速度矢量", "移动速度 +10%"),
        Upgrade("hp", "|x| 绝对护甲", "生命上限 +25 并回复 25"),
        Upgrade("pickup", "π 引力环", "拾取范围 +30%"),
    ).shuffled(Random).take(3)'''
assert old in s, 'sg upgrades'
s = s.replace(old, new)

# 3) spawnEnemy：字形按生存时间分三阶解锁 + 大怪随机边数
old = '''        val r = if (Random.nextFloat() < 0.08f) 26f else 13f + Random.nextFloat() * 7f   // 8% 大只
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg))'''
new = '''        val r = if (Random.nextFloat() < 0.08f) 26f else 13f + Random.nextFloat() * 7f   // 8% 大只
        // 批42：字形按生存时间三阶解锁（数字→运算符→希腊字母），有成长感
        val glyphPool = when {
            elapsed < 30f -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
            elapsed < 90f -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "−")
            elapsed < 180f -> listOf("2", "3", "5", "7", "+", "−", "×", "÷", "=")
            else -> listOf("π", "√", "∑", "∞", "∆", "×", "÷", "=")
        }
        val glyph = glyphPool[Random.nextInt(glyphPool.size)]
        val sides = if (r > 20f) listOf(3, 4, 5)[Random.nextInt(3)] else 0
        enemies.add(Enemy(px + cos(ang) * ring, py + sin(ang) * ring, r, hp, hp, speed, dmg, glyph, sides))'''
assert old in s, 'sg spawn'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorGame ok')

# ============ SurvivorScreen.kt ============
p2 = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()

# imports（挂 GRID/颜色常量附近或字母序）
anchor_imp = 'import androidx.compose.ui.graphics.Color'
if 'import androidx.compose.ui.text.drawText' not in s2:
    for imp in [
        'import androidx.compose.ui.text.drawText',
        'import androidx.compose.ui.text.TextStyle',
        'import androidx.compose.ui.text.rememberTextMeasurer',
        'import androidx.compose.ui.text.TextLayoutResult',
        'import androidx.compose.ui.graphics.Path',
    ]:
        if imp not in s2:
            if anchor_imp in s2:
                s2 = s2.replace(anchor_imp, anchor_imp + '\n' + imp, 1)
            else:
                s2 = s2.replace('import androidx.compose.foundation.Canvas', 'import androidx.compose.foundation.Canvas\n' + imp, 1)

# 4) 常量：主格线/轴色
old4 = 'private val GRID = 0xFF1D2A3E'
if old4 not in s2:
    # 可能写作 GRID = Color(0xFF1D2A3E)
    old4 = s2[s2.find('GRID'):s2.find('GRID')+40]
assert 'GRID' in s2
# 用通用方式：在 BG 定义后追加
if 'GRID_MAJOR' not in s2:
    s2 = s2.replace('private val BG = Color(0xFF101826)',
        'private val BG = Color(0xFF101826)\n    // 批42：数学坐标纸——副格(原GRID)/主格线/坐标轴\n    private val GRID_MAJOR = Color(0xFF2E4A6B)\n    private val AXIS_C = Color(0xFF3D5A80)')

# 5) 背景网格 → 坐标纸 + 轴 + 原点
old5 = '''            // 背景网格（随相机平移）
            val step = 64f
            var gx = -game.camX % step
            if (gx < 0) gx += step
            while (gx < size.width) {
                drawLine(GRID, Offset(gx, 0f), Offset(gx, size.height), 2f)
                gx += step
            }
            var gy = -game.camY % step
            if (gy < 0) gy += step
            while (gy < size.height) {
                drawLine(GRID, Offset(0f, gy), Offset(size.width, gy), 2f)
                gy += step
            }'''
new5 = '''            // 背景网格（批42：数学坐标纸——每 5 格一条主格线，过暗不抢主体）
            val step = 64f
            var gx = -game.camX % step
            if (gx < 0) gx += step
            while (gx < size.width) {
                val worldX = gx + game.camX
                val major = kotlin.math.roundToInt(worldX / step) % 5 == 0
                drawLine(if (major) GRID_MAJOR else GRID, Offset(gx, 0f), Offset(gx, size.height), if (major) 3f else 2f)
                gx += step
            }
            var gy = -game.camY % step
            if (gy < 0) gy += step
            while (gy < size.height) {
                val worldY = gy + game.camY
                val major = kotlin.math.roundToInt(worldY / step) % 5 == 0
                drawLine(if (major) GRID_MAJOR else GRID, Offset(0f, gy), Offset(size.width, gy), if (major) 3f else 2f)
                gy += step
            }
            // 批42：X/Y 坐标轴与原点（世界 0,0）——"数学空间"的锚
            val ox = 0f - game.camX
            val oy = 0f - game.camY
            if (ox > -10f && ox < size.width + 10f) drawLine(AXIS_C, Offset(ox, 0f), Offset(ox, size.height), 4f)
            if (oy > -10f && oy < size.height + 10f) drawLine(AXIS_C, Offset(0f, oy), Offset(size.width, oy), 4f)
            if (ox > -30f && ox < size.width + 30f && oy > -30f && oy < size.height + 30f) {
                drawCircle(AXIS_C, 10f, Offset(ox, oy), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }'''
assert old5 in s2, 'ss grid'
s2 = s2.replace(old5, new5)

# 6) 敌人绘制：大怪几何线框 + 普通怪字形（发光白字）
old6 = '''                drawCircle(if (e.r > 20f) ENEMY_BIG_C else ENEMY_C, e.r, Offset(sx, sy))
                if (e.hp < e.maxHp) {'''
new6 = '''                if (e.sides >= 3) {
                    // 批42：大怪=几何线框体（三角/方/五边），微光填充
                    val path = Path()
                    for (k in 0 until e.sides) {
                        val ang = -1.5707963f + k * (6.2831853f / e.sides)
                        val vx = sx + kotlin.math.cos(ang) * e.r
                        val vy = sy + kotlin.math.sin(ang) * e.r
                        if (k == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                    }
                    path.close()
                    drawPath(path, ENEMY_BIG_C.copy(alpha = 0.30f))
                    drawPath(path, ENEMY_BIG_C, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                } else {
                    drawCircle(ENEMY_C, e.r, Offset(sx, sy))
                    // 批42：发光数学字形（红晕四向重绘 + 白心）
                    var layout = glyphMetrics[e.glyph]
                    if (layout == null) {
                        layout = textMeasurer.measure(
                            e.glyph,
                            TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        )
                        glyphMetrics[e.glyph] = layout
                    }
                    val tl = Offset(sx - layout.size.width / 2f, sy - layout.size.height / 2f)
                    for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                        drawText(layout, color = ENEMY_C, topLeft = tl + off)
                    }
                    drawText(layout, topLeft = tl)
                }
                if (e.hp < e.maxHp) {'''
assert old6 in s2, 'ss enemy'
s2 = s2.replace(old6, new6)

# 7) 记忆化基础设施（frame 驱动重绘前的 composable 状态区）：挂在 joyMaxPx 之后
old7 = '''    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }'''
new7 = '''    val joyMaxPx = with(LocalDensity.current) { 48.dp.toPx() }
    // 批42：数学字形测量缓存（drawText 需要 TextLayoutResult；按字形缓存避免每帧测量）
    val textMeasurer = rememberTextMeasurer()
    val glyphMetrics = remember { mutableMapOf<String, androidx.compose.ui.text.TextLayoutResult>() }'''
assert old7 in s2, 'ss metrics'
s2 = s2.replace(old7, new7)

# 8) READY 副标题数学化
old8 = '''                                Text("弹幕求生 · 杀敌升级 · 三选一", fontSize = 11.sp, color = GameUi.TextDim)'''
new8 = '''                                Text("数学空间 · 几何生存 · 三选一", fontSize = 11.sp, color = GameUi.TextDim)   // 批42 主题化'''
assert old8 in s2, 'ss ready'
s2 = s2.replace(old8, new8)
# READY 图标 🧟 → ∑
old8b = '''                            Text("🧟", fontSize = 30.sp)'''
new8b = '''                            Text("∑", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)   // 批42'''
s2 = s2.replace(old8b, new8b)

# 9) LEVELUP 卡：强化 id → 数学字形图标
old9 = '''                        game.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(Modifier.weight(1f), accent = GameUi.Xp, contentPadding = 16.dp) {
                                Text("强化 0${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GameUi.Gold)'''
new9 = '''                        game.pendingUpgrades.forEachIndexed { idx, up ->
                            GamePanel(Modifier.weight(1f), accent = GameUi.Xp, contentPadding = 16.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 批42：强化的数学字形标识
                                    Text(
                                        upGlyph[up.id] ?: "?",
                                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GameUi.Xp,
                                    )
                                    Text(
                                        "强化 0${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = GameUi.Gold, modifier = Modifier.padding(start = 8.dp),
                                    )
                                }'''
assert old9 in s2, 'ss levelup'
s2 = s2.replace(old9, new9)
# upGlyph map（放 READY 前的常量区/或文件级）——文件级私有 map
if 'upGlyph' in s2 and 'private val upGlyph' not in s2:
    s2 = s2.replace('fun SurvivorScreen(vm: AppViewModel, nav: NavHostController) {',
'''/** 批42：强化 id → 数学字形 */
private val upGlyph = mapOf("atk" to "×", "aspd" to "∂", "spd" to "√", "hp" to "|x|", "pickup" to "π")

fun SurvivorScreen(vm: AppViewModel, nav: NavHostController) {''')

io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('SurvivorScreen ok')
