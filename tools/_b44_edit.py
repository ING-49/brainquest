# -*- coding: utf-8 -*-
"""批44 编辑：五子棋星位+回合图形化 / 华容道暖纸底 / 贪吃蛇大分数"""
import io

# ============ GomokuScreen.kt ============
p = 'app/src/main/java/com/brainquest/game/game/gomoku/GomokuScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 星位（网格线循环之后、棋子循环之前）
old = '''            for (r in 0 until game.size) {
                for (c in 0 until game.size) {
                    val v = game.cell(r, c)'''
new = '''            // 批44：星位（天元 + 四星，15 路标准）
            val starDot = cell * 0.22f
            listOf(3 to 3, 3 to 11, 11 to 3, 11 to 11, 7 to 7).forEach { (sr, sc) ->
                Box(
                    Modifier
                        .offset(x = cell * sc - starDot / 2, y = cell * sr - starDot / 2)
                        .size(starDot)
                        .background(Color(0x40000000), CircleShape),
                )
            }
            for (r in 0 until game.size) {
                for (c in 0 until game.size) {
                    val v = game.cell(r, c)'''
assert old in s, 'gk stars'
s = s.replace(old, new)

# 2) 人机回合指示图形化（黑子=轮到你，白子=电脑思考）
old2 = '''                Text(
                    "本局 ${sessionWins} 胜 ${sessionLosses} 负 · 🔥 连胜 $streak · " +
                        (if (game.finished) "本局结束" else if (game.playerTurn) "轮到你" else "电脑思考中…"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )'''
new2 = '''                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    // 批44：回合图形化指示——黑子=轮到你，白子=电脑思考中
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (game.playerTurn && !game.finished) Color(0xFF212121) else Color(0xFDFDFD))
                            .border(1.dp, Color(0xFF6D4C41), CircleShape),
                    )
                    Text(
                        " 本局 ${sessionWins} 胜 ${sessionLosses} 负 · 🔥 连胜 $streak · " +
                            (if (game.finished) "本局结束" else if (game.playerTurn) "轮到你" else "电脑思考中…"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }'''
assert old2 in s, 'gk turn'
s = s.replace(old2, new2)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('Gomoku ok')

# ============ KlotskiScreen.kt ============
p2 = 'app/src/main/java/com/brainquest/game/game/klotski/KlotskiScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()
old3 = '''    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {'''
new3 = '''    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF5), Color(0xFFF6EFE0))))   // 批44：淡暖纸感底，衬古典棋盘
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {'''
assert old3 in s2, 'kl bg'
s2 = s2.replace(old3, new3)
if 'import androidx.compose.ui.graphics.Brush' not in s2:
    s2 = s2.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color')
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('Klotski ok')

# ============ SnakeScreen.kt ============
p3 = 'app/src/main/java/com/brainquest/game/game/snake/SnakeScreen.kt'
s3 = io.open(p3, encoding='utf-8').read()
old4 = '''            Text("🍎 得分 ${game.score}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)'''
new4 = '''            Text("🍎 ${game.score}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))   // 批44：街机大分数'''
assert old4 in s3, 'sn score'
s3 = s3.replace(old4, new4)
io.open(p3, 'w', encoding='utf-8', newline='').write(s3)
print('Snake ok')
