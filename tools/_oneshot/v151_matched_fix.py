"""v1.5.1：乙方 matched 修复 + 上下布局 + ready 解析 + start 守卫（完整读写版）"""

# ========== PkClient.kt：ready 解析 ==========
p = 'app/src/main/java/com/brainquest/game/net/PkClient.kt'
src = open(p, encoding='utf-8').read()
old = '''            "peer_ready" -> onEvent(PkEvent.PeerReady)'''
new = '''            "peer_ready", "ready" -> onEvent(PkEvent.PeerReady)'''
assert old in src, "PkClient peer_ready 未找到"
src = src.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(src)
print('1 PkClient ready OK')

# ========== PkBattleScreen.kt ==========
p = 'app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt'
src = open(p, encoding='utf-8').read()

# 1a. Joined → matched（乙方修复）
old = '''            is PkEvent.Joined -> {
                roomCode = event.code; status = "已加入房间，对手：${event.peer}"
                phase = "waiting"
            }'''
new = '''            is PkEvent.Joined -> {
                roomCode = event.code
                joinedAsGuest = true
                peerName = event.peer
                status = "已加入房间，等待双方准备…"
                phase = "matched"
            }'''
assert old in src, "Joined 未找到"
src = src.replace(old, new, 1)

# 1b. 状态字段
old = '''    var peerVersion by remember { mutableStateOf("") }'''
new = '''    var peerVersion by remember { mutableStateOf("") }
    var peerName by remember { mutableStateOf("") }
    var joinedAsGuest by remember { mutableStateOf(false) }'''
assert old in src, "peerVersion 未找到"
src = src.replace(old, new, 1)

# 1c. matched 页整体替换为上下布局
start = src.index('            "matched" -> {')
end = src.index('            "countdown" -> {')
matched_ui = '''            "matched" -> {
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

                    val iAmHost = embedded != null || !joinedAsGuest
                    PlayerReadyCard(
                        label = "甲方（房主）",
                        name = if (iAmHost) player.nickname else peerName,
                        ready = if (iAmHost) myReady else peerReady,
                        accent = Color(0xFFEF5350),
                    )
                    Text("VS", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    PlayerReadyCard(
                        label = "乙方（挑战者）",
                        name = if (iAmHost) peerName else player.nickname,
                        ready = if (iAmHost) peerReady else myReady,
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
            }
'''
src = src[:start] + matched_ui + src[end:]
print('2 matched 上下布局 OK')

# 1d. 文件尾追加 PlayerReadyCard
card = '''

/** 匹配页玩家卡：角色标签 + 名称 + 准备状态 */
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
            Column(Modifier.weight(1f)) {
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
src = src.rstrip() + '\n' + card
open(p, 'w', encoding='utf-8').write(src)
print('3 PlayerReadyCard OK')
