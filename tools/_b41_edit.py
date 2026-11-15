# -*- coding: utf-8 -*-
"""批41 编辑脚本：BattleScreen 战斗氛围 + PkBattleScreen 对抗横幅"""
import io

# ============ BattleScreen.kt ============
p = 'app/src/main/java/com/brainquest/game/game/quizbattle/BattleScreen.kt'
s = io.open(p, encoding='utf-8').read()
for imp in [
    'import androidx.compose.foundation.border',
    'import androidx.compose.foundation.layout.fillMaxHeight',
    'import androidx.compose.ui.graphics.Brush',
    'import androidx.compose.ui.draw.clip',
]:
    if imp not in s:
        # 挂到各自字母序邻居后
        neighbors = {
            'import androidx.compose.foundation.border': 'import androidx.compose.foundation.background',
            'import androidx.compose.foundation.layout.fillMaxHeight': 'import androidx.compose.foundation.layout.fillMaxWidth',
            'import androidx.compose.ui.graphics.Brush': 'import androidx.compose.ui.graphics.Color',
            'import androidx.compose.ui.draw.clip': 'import androidx.compose.ui.graphics.Color',
        }
        anchor = neighbors[imp]
        assert anchor in s, anchor
        s = s.replace(anchor, anchor + '\n' + imp, 1)

# 1) 根 Column：暖底渐变，去掉统一 padding（改为分区管理）
old = '''    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader('''
new = '''    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF2), Color(0xFFF4F6FB))))
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        PageHeader('''
assert old in s, 'bs root'
s = s.replace(old, new)
old = '''            subtitle = "限时 ${battle.timeLimitSec}s / 题 · 答对出招 答错挨打",
        )
        if (confirmExit) {'''
new = '''            subtitle = "限时 ${battle.timeLimitSec}s / 题 · 答对出招 答错挨打",
        )
        }
        if (confirmExit) {'''
assert old in s, 'bs header close'
s = s.replace(old, new)

# 2) 敌人卡 → 敌方阵营暗带
old = '''        // 敌人面板
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    battle.enemy.emoji,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = enemyShake.value * if (enemyShake.value < 0.5f) -14f else 14f
                            alpha = 1f
                        }
                        .size(width = 56.dp, height = 56.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(battle.enemy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (battle.combo >= 2) {
                            LaunchedEffect(battle.combo) {
                                comboScale.snapTo(1.5f)
                                comboScale.animateTo(1f, tween(180))
                            }
                            Text(
                                "🔥 连击 x${battle.combo}",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = comboScale.value
                                    scaleY = comboScale.value
                                },
                            )
                        }
                    }
                    BqProgressBar(
                        progress = battle.enemyHp.toFloat() / battle.enemyHpMax,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = androidx.compose.ui.graphics.lerp(Color(0xFFE53935), Color.White, hpFlash.value * 0.8f),
                        height = 10.dp,
                    )
                    Text(
                        "HP ${battle.enemyHp} / ${battle.enemyHpMax}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }'''
new = '''        // 批41：敌方阵营暗带（战斗氛围，全宽出血）——品类参照：回合制战斗的"敌方区域"
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2434), Color(0xFF121826))))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp)
                        .background(
                            Brush.radialGradient(
                                0f to Color(0x55E53935),
                                0.7f to Color(0x22E53935),
                                1f to Color(0x00E53935),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        battle.enemy.emoji,
                        fontSize = 34.sp,
                        modifier = Modifier.graphicsLayer {
                            translationX = enemyShake.value * if (enemyShake.value < 0.5f) -14f else 14f
                        },
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(battle.enemy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        if (battle.combo >= 2) {
                            LaunchedEffect(battle.combo) {
                                comboScale.snapTo(1.5f)
                                comboScale.animateTo(1f, tween(180))
                            }
                            Text(
                                "🔥 连击 x${battle.combo}",
                                color = Color(0xFFFFB74D),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = comboScale.value
                                    scaleY = comboScale.value
                                },
                            )
                        }
                    }
                    SegmentedHpBar(
                        frac = battle.enemyHp.toFloat() / battle.enemyHpMax,
                        flash = hpFlash.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Text(
                        "HP ${battle.enemyHp} / ${battle.enemyHpMax}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }'''
assert old in s, 'bs enemy'
s = s.replace(old, new)

# 3) 我方 Row 加水平 padding
old = '''        // 我方状态
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),'''
new = '''        // 我方状态
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 10.dp),'''
assert old in s, 'bs self'
s = s.replace(old, new)

# 4) 飘字 Box 加水平 padding
old = '''        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {'''
new = '''        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(36.dp), contentAlignment = Alignment.Center) {'''
assert old in s, 'bs float'
s = s.replace(old, new)

# 5) 题卡：水平 padding + 描边
old = '''        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {'''
new = '''        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .weight(1f, fill = false)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {'''
assert old in s, 'bs qcard'
s = s.replace(old, new)

# 6) 道具 Row 加水平 padding
old = '''        // 道具栏
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {'''
new = '''        // 道具栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {'''
assert old in s, 'bs items'
s = s.replace(old, new)

# 7) 分段血条组件（文件末尾追加）
s += '''
/** 批41：浅色分段血条（敌方暗带上用；5 段，分段思路参考 GameUi.HudHpBar） */
@Composable
private fun SegmentedHpBar(frac: Float, flash: Float, modifier: Modifier = Modifier) {
    val segments = 5
    Box(
        modifier
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33000000))
    ) {
        Row(Modifier.fillMaxHeight().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(segments) { i ->
                val filled = i < (frac * segments).coerceIn(0f, segments.toFloat())
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (filled) androidx.compose.ui.graphics.lerp(Color(0xFFE53935), Color.White, flash * 0.8f)
                            else Color(0x22FFFFFF)
                        )
                )
            }
        }
    }
}
'''
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('BattleScreen ok')

# ============ PkBattleScreen.kt ============
p2 = 'app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()
for imp, anchor in [
    ('import androidx.compose.foundation.border', 'import androidx.compose.foundation.background'),
    ('import androidx.compose.ui.graphics.Brush', 'import androidx.compose.ui.graphics.Color'),
]:
    if imp not in s2:
        assert anchor in s2, anchor
        s2 = s2.replace(anchor, anchor + '\n' + imp, 1)
if 'import androidx.compose.ui.draw.clip' not in s2:
    s2 = s2.replace('import androidx.compose.ui.graphics.Brush', 'import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.graphics.Brush', 1)

# 8) 对抗横幅
old2 = '''                        // 双方进度 x/10（对手实时更新）
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🧑 我 ${if (answered) qIndex + 1 else qIndex}/${questions.size}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("第 ${qIndex + 1}/${questions.size} 题 · ⏳ ${timeLeftMs / 1000}s",
                                color = if (timeLeftMs < 5000) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge)
                            Text(if (peerLost) "⛓ 对手掉线" else "对手 ${peerAnswered.coerceAtMost(questions.size)}/${questions.size}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = if (peerLost) Color(0xFFC62828) else Color.Unspecified)
                        }
                        BqProgressBar(
                            progress = (timeLeftMs.toFloat() / QUESTION_TIME_MS).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            height = 6.dp,
                        )'''
new2 = '''                        // 批41：对抗横幅（红蓝双方对撞，竞技视觉）
                        val myScoreNow = if (answered) qIndex + 1 else qIndex
                        val peerScoreNow = peerAnswered.coerceAtMost(questions.size)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1A42A5F5))
                                    .border(1.dp, Color(0x5542A5F5), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text("🧑 我", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                                Text("$myScoreNow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                BqProgressBar(myScoreNow / questions.size.toFloat(), Modifier.fillMaxWidth().padding(top = 3.dp), height = 5.dp, color = Color(0xFF42A5F5))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Q${qIndex + 1}/${questions.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "⏳ ${timeLeftMs / 1000}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (timeLeftMs < 5000) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1AEF5350))
                                    .border(1.dp, Color(0x55EF5350), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    if (peerLost) "⛓ 掉线" else "对手",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC62828),
                                )
                                Text(
                                    if (peerLost) "--" else "$peerScoreNow",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFC62828),
                                )
                                BqProgressBar(
                                    peerScoreNow / questions.size.toFloat(),
                                    Modifier.fillMaxWidth().padding(top = 3.dp),
                                    height = 5.dp, color = Color(0xFFEF5350),
                                )
                            }
                        }
                        BqProgressBar(
                            progress = (timeLeftMs.toFloat() / QUESTION_TIME_MS).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            height = 6.dp,
                            color = if (timeLeftMs < 5000) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                        )'''
assert old2 in s2, 'pk banner'
s2 = s2.replace(old2, new2)

# 9) 结算庆祝带
old3 = '''                    Text(if (outcome == "win") "🏆 胜利！" else if (outcome == "lose") "💀 惜败" else "🤝 平局",
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)'''
new3 = '''                    // 批41：结算庆祝带（胜蓝/败红/平灰）
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    when (outcome) {
                                        "win" -> listOf(Color(0x5542A5F5), Color(0x1142A5F5))
                                        "lose" -> listOf(Color(0x55EF5350), Color(0x11EF5350))
                                        else -> listOf(Color(0x339E9E9E), Color(0x119E9E9E))
                                    }
                                )
                            )
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            if (outcome == "win") "🏆 胜利！" else if (outcome == "lose") "💀 惜败" else "🤝 平局",
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }'''
assert old3 in s2, 'pk result'
s2 = s2.replace(old3, new3)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('PkBattleScreen ok')
