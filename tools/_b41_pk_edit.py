# -*- coding: utf-8 -*-
"""批41 PK 编辑（修正 import 锚点版）"""
import io

p2 = 'app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()
# 统一插入点：Arrangement import（必存在）
INSERT_AFTER = 'import androidx.compose.foundation.layout.Arrangement'
for imp in [
    'import androidx.compose.foundation.border',
    'import androidx.compose.foundation.background',
    'import androidx.compose.ui.graphics.Brush',
    'import androidx.compose.ui.draw.clip',
]:
    if imp not in s2:
        assert INSERT_AFTER in s2
        s2 = s2.replace(INSERT_AFTER, INSERT_AFTER + '\n' + imp, 1)

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
