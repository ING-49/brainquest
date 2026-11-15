# -*- coding: utf-8 -*-
"""批48 屏幕层：种类配色 / 图鉴页 / READY 入口 / 持久化"""
import io

p = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 种类配色（敌 Circle 分支）
old = '''                drawCircle(if (e.r > 20f) ENEMY_BIG_C else ENEMY_C, e.r, Offset(sx, sy))
                // 批42：发光数学字形（红晕四向重绘 + 白心）'''
new = '''                // 批48：按种类配色——数字崽红 / 运算符蜂青 / 几何狂橙
                val kindColor = when (e.kind) {
                    1 -> Color(0xFF4DD0E1)
                    2 -> Color(0xFFFFB74D)
                    else -> if (e.r > 20f) ENEMY_BIG_C else ENEMY_C
                }
                drawCircle(kindColor, e.r, Offset(sx, sy))
                // 批42：发光数学字形（四向重绘 + 白心）'''
assert old in s, 'v1'
s = s.replace(old, new)
# 字形光晕颜色同步 kindColor
old1b = '''                    for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                        drawText(layout, color = ENEMY_C, topLeft = tl + off)
                    }'''
new1b = '''                    for (off in listOf(Offset(-1.5f, 0f), Offset(1.5f, 0f), Offset(0f, -1.5f), Offset(0f, 1.5f))) {
                        drawText(layout, color = kindColor, topLeft = tl + off)
                    }'''
assert old1b in s, 'v1b'
s = s.replace(old1b, new1b)

# 2) 几何线框颜色用 kindColor（大怪/精英段）
old2 = '''                    drawPath(path, ENEMY_BIG_C.copy(alpha = 0.30f))
                    drawPath(path, ENEMY_BIG_C, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))'''
new2 = '''                    val geoC = if (e.kind == 3) ENEMY_BIG_C else Color(0xFFFFB74D)
                    drawPath(path, geoC.copy(alpha = 0.30f))
                    drawPath(path, geoC, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))'''
assert old2 in s, 'v2'
s = s.replace(old2, new2)

# 3) READY 弹窗加图鉴入口按钮（开始游戏之前）
old3 = '''                        Spacer(Modifier.height(16.dp))
                        GameActionButton(
                            "开始游戏",'''
new3 = '''                        Spacer(Modifier.height(16.dp))
                        GameActionButton(
                            "📜 图鉴",
                            onClick = { showCodex = true },
                            primary = false,
                            height = 44.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        GameActionButton(
                            "开始游戏",'''
assert old3 in s, 'v3'
s = s.replace(old3, new3)

# 4) 状态 + 图鉴页 overlay（挂在 READY 块之前）
old4 = '''        if (hud.phase == SurvivorGame.Phase.READY) {'''
new4 = '''        // ---------- 批48：数学空间图鉴（4 种怪档案） ----------
        if (showCodex) {
            Box(Modifier.fillMaxSize().background(Color(0xE6070A14))) {
                Column(
                    Modifier.fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("←", onClick = { showCodex = false }, size = 38.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("📐 数学空间图鉴", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GameUi.Gold)
                            Text(
                                "已遭遇 ${survCodexEntries.count { game.defeatedKinds.contains(it.kindIdx) }} / ${survCodexEntries.size} · 击杀后解锁档案",
                                fontSize = 10.sp, color = GameUi.TextDim,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    survCodexEntries.forEach { e ->
                        val seen = game.defeatedKinds.contains(e.kindIdx)
                        GamePanel(Modifier.fillMaxWidth().padding(vertical = 6.dp), accent = if (seen) GameUi.Gold else null, contentPadding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.glyph, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (seen) e.color else Color(0x55FFFFFF))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(if (seen) e.name else "？？？" + "（" + e.name + "）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(if (seen) "已收录" else "未遭遇", fontSize = 10.sp, color = if (seen) GameUi.Gold else GameUi.TextFaint)
                                }
                            }
                            if (seen) {
                                Text("属性：" + e.attr, style = MaterialTheme.typography.bodySmall, color = GameUi.Text, modifier = Modifier.padding(top = 8.dp))
                                Text("行动习惯：" + e.behavior, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim, modifier = Modifier.padding(top = 3.dp))
                            } else {
                                Text("档案加密——遭遇并击杀后解锁。", style = MaterialTheme.typography.bodySmall, color = GameUi.TextFaint, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GameActionButton("返回", onClick = { showCodex = false }, primary = false, height = 44.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (hud.phase == SurvivorGame.Phase.READY) {'''
assert old4 in s, 'v4'
s = s.replace(old4, new4)

# 5) showCodex 状态 + BackHandler
old5 = '''    var confirmExit by remember { mutableStateOf(false) }'''
new5 = '''    var confirmExit by remember { mutableStateOf(false) }
    var showCodex by remember { mutableStateOf(false) }   // 批48：数学空间图鉴'''
assert old5 in s, 'v5'
s = s.replace(old5, new5)

# 6) 持久化：两处 addSurvivorResult 调用点追加 markSurvivorSeen
old6 = '''vm.addSurvivorResult('''
cnt = s.count(old6)
s = s.replace(old6, '''vm.markSurvivorSeen(game.defeatedKinds.map { k -> "SURV_" + listOf("DIGIT", "OP", "GEOM", "ELITE")[k.coerceIn(0, 3)] })
            vm.addSurvivorResult(''')
print('survivor result sites patched:', cnt)

# 7) 文件级：图鉴数据
s += '''

/** 批48：数学空间图鉴条目 */
private data class SurvCodexEntry(
    val key: String,
    val kindIdx: Int,
    val glyph: String,
    val name: String,
    val color: androidx.compose.ui.graphics.Color,
    val attr: String,
    val behavior: String,
)

private val survCodexEntries = listOf(
    SurvCodexEntry("SURV_DIGIT", 0, "1", "数字崽", androidx.compose.ui.graphics.Color(0xFFE15A5A),
        "血量低 · 速度中 · 威胁低",
        "无脑直线扑向玩家，成群出没——数量是唯一威胁"),
    SurvCodexEntry("SURV_OP", 1, "+", "运算符蜂", androidx.compose.ui.graphics.Color(0xFF4DD0E1),
        "血量低 · 速度快 · 威胁中",
        "顶着 + − 符号沿正弦轨迹绕飞，天然避开直线弹幕——需要预判拦截"),
    SurvCodexEntry("SURV_GEOM", 2, "△", "几何狂", androidx.compose.ui.graphics.Color(0xFFFFB74D),
        "血量中 · 速度爆发 · 威胁中高",
        "多边形线框体，每 3 秒蓄力冲刺一次（2.2 倍速），冲刺前会靠近锁定"),
    SurvCodexEntry("SURV_ELITE", 3, "∑", "∑ 求和体", androidx.compose.ui.graphics.Color(0xFFB23A3A),
        "血量极高 · 速度慢 · 威胁高",
        "每 60 秒降临的精英：六边线框+金色大 ∑，缓慢但坚定——击杀掉落大经验，优先级目标"),
)
'''
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SurvivorScreen ok')
