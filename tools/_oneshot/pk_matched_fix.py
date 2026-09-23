"""乙方准备按钮修复 + 匹配页上下布局（甲方上/乙方下）"""

src = open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', encoding='utf-8').read()

# ---------- 1. 乙方 Joined → matched ----------
old = '''            is PkEvent.Joined -> {
                roomCode = event.code; status = "已加入房间，对手：${event.peer}"
                phase = "waiting"
            }'''
new = '''            is PkEvent.Joined -> {
                roomCode = event.code
                peerName = event.peer
                status = "已加入房间，等待双方准备…"
                phase = "matched"   // 乙方同样进入匹配确认页（有准备按钮）
            }'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 2. matched 页上下布局（甲方上/乙方下） ----------
old = '''            "matched" -> {
                // 匹配成功动画 + 双方确认
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                    val scale = transition.animateFloat(
                        initialValue = 0.92f, targetValue = 1.08f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(600),
                            androidx.compose.animation.core.RepeatMode.Reverse,
                        ), label = "pulse",
                    ).value
                    Text("🎉", style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale))
                    Text("匹配成功！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("$peerName VS ${player.nickname}", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { sendReady() }, enabled = !myReady, modifier = Modifier.fillMaxWidth()) {
                        Text(if (myReady) "已准备，等待对方…" else "✋ 准备就绪")
                    }
                    if (myReady && peerReady) Text("双方已就绪！", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { listOf(myReady, peerReady).count { it } / 2f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }'''
new = '''            "matched" -> {
                // 匹配成功动画 + 上下布局双方确认（甲方=房主 在上，乙方=你 在下）
                Column(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                    val scale = transition.animateFloat(
                        initialValue = 0.92f, targetValue = 1.08f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(600),
                            androidx.compose.animation.core.RepeatMode.Reverse,
                        ), label = "pulse",
                    ).value
                    Text("🎉", style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale))
                    Text("匹配成功！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                    // 甲方（房主）卡
                    PlayerReadyCard(
                        label = "甲方（房主）",
                        name = if (embedded != null || hostMode) player.nickname else peerName,
                        ready = if (embedded != null || hostMode) myReady else peerReady,
                        accent = Color(0xFFEF5350),
                    )
                    Text("VS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary)
                    // 乙方（挑战者）卡
                    PlayerReadyCard(
                        label = "乙方（挑战者）",
                        name = if (embedded != null || hostMode) peerName else player.nickname,
                        ready = if (embedded != null || hostMode) peerReady else myReady,
                        accent = Color(0xFF42A5F5),
                    )
                    Button(onClick = { sendReady() }, enabled = !myReady, modifier = Modifier.fillMaxWidth()) {
                        Text(if (myReady) "已准备，等待对方…" else "✋ 准备就绪")
                    }
                    if (myReady && peerReady) Text("双方已就绪，即将开始！",
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 3. PlayerReadyCard 组件（文件尾追加） ----------
src += '''

/** 匹配页玩家卡：名称 + 准备状态 */
@Composable
private fun PlayerReadyCard(label: String, name: String, ready: Boolean, accent: Color) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(12.dp).graphicsLayer(shape = androidx.compose.ui.graphics.RectangleShape),
            ) {}
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                if (ready) "✓ 已准备" else "⏳ 未准备",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (ready) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
            )
        }
    }
}
'''

# ---------- 4. PkClient：解析 "ready"（双方互发的 ready 都视为对方已准备） ----------
src2 = open('app/src/main/java/com/brainquest/game/net/PkClient.kt', encoding='utf-8').read()
old = '''            "peer_ready" -> onEvent(PkEvent.PeerReady)'''
new = '''            "peer_ready", "ready" -> onEvent(PkEvent.PeerReady)'''
assert old in src2
src2 = src2.replace(old, new2 if False else new, 1)
open('app/src/main/java/com/brainquest/game/net/PkClient.kt', 'w', encoding='utf-8').write(src2)
print('4 PkClient ready parse OK')
