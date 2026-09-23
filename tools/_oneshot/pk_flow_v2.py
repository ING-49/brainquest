"""联机流程重做：matched→confirm→countdown→battle→mydone→result 六阶段"""

src = open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', encoding='utf-8').read()

# ---------- 1. 状态与协议 ----------
old = '''    var peerVersion by remember { mutableStateOf("") }'''
new = '''    var peerVersion by remember { mutableStateOf("") }
    var peerName by remember { mutableStateOf("") }
    var myReady by remember { mutableStateOf(false) }
    var peerReady by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var peerAnswered by remember { mutableIntStateOf(0) }  // 对手已作答题数（含未判分提交）'''
assert old in src
src = src.replace(old, new, 1)

# PeerJoined/Start：进入 matched（不直接开战）；对手名字记录
old = '''            is PkEvent.PeerJoined -> {
                status = "对手 ${event.peer}（${event.version}）已加入！"
                peerVersion = event.version
                runCatching {
                    val qs = buildList {
                        val used = mutableSetOf<String>()
                        var guard = 0
                        while (size < PK_QUESTION_COUNT && guard < 60) {
                            val subject = Subjects.all[guard % Subjects.all.size]
                            val q = vm.bank.pick(subject, 2 + guard % 3)
                            if (q != null && q.type != "fill" && q.id !in used) {
                                add(q); used.add(q.id)
                            }
                            guard++
                        }
                    }
                    questions = qs
                    phase = "battle"
                    qIndex = 0; myCorrect = 0; peerCorrect = 0; totalTime = 0
                    status = "对战开始！"
                    // 房主把题目发给对手：内嵌服务器 → broadcastStart；远程 → sendStart
                    embedded?.setQuestions(qs); embedded?.broadcastStart()
                        ?: client.sendStart(qs)
                    PkDiscovery.stopBeacon()
                }.onFailure { status = "发题失败：${it.message}" }
            }
            is PkEvent.Start -> {
                questions = event.questions
                phase = "battle"
                qIndex = 0; myCorrect = 0; peerCorrect = 0; totalTime = 0
                status = "对战开始！"
            }'''
new = '''            is PkEvent.PeerJoined -> {
                peerName = event.peer
                status = "匹配成功！"
                phase = "matched"   // 匹配成功动画 + 双方确认
                runCatching {
                    // 房主此刻就锁定题目（双方确认后才下发）
                    val qs = buildList {
                        val used = mutableSetOf<String>()
                        var guard = 0
                        while (size < PK_QUESTION_COUNT && guard < 60) {
                            val subject = Subjects.all[guard % Subjects.all.size]
                            val q = vm.bank.pick(subject, 2 + guard % 3)
                            if (q != null && q.type != "fill" && q.id !in used) {
                                add(q); used.add(q.id)
                            }
                            guard++
                        }
                    }
                    questions = qs
                    embedded?.setQuestions(qs)
                    PkDiscovery.stopBeacon()
                }.onFailure { status = "发题失败：${it.message}" }
            }
            is PkEvent.Start -> {
                questions = event.questions
                qIndex = 0; myCorrect = 0; peerCorrect = 0; peerAnswered = 0; totalTime = 0
                myReady = false; peerReady = false
                countdown = 3       // 进入 3·2·1 倒计时
                phase = "countdown"
            }'''
assert old in src
src = src.replace(old, new, 1)

# PeerAnswer：同时统计对手进度与答对数
old = '''            is PkEvent.PeerAnswer -> {
                if (event.correct) peerCorrect++
            }'''
new = '''            is PkEvent.PeerAnswer -> {
                peerAnswered = event.idx + 1
                if (event.correct) peerCorrect++
            }'''
assert old in src
src = src.replace(old, new, 1)

# PeerReady
old = '''            is PkEvent.PeerAnswer -> {'''
new = '''            is PkEvent.PeerReady -> {
                peerReady = true
            }
            is PkEvent.PeerAnswer -> {'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 2. 确认与倒计时协程 ----------
old = '''    fun beginQuestion() {'''
new = '''    // 双方都点了准备 → 房主发题（内嵌广播/远程 sendStart），双方进倒计时
    LaunchedEffect(myReady, peerReady) {
        if (phase == "matched" && myReady && peerReady) {
            delay(600)  // 让"匹配成功"动画呼吸一下
            if (hostMode || embedded != null) {
                embedded?.broadcastStart() ?: client.sendStart(questions)
            }
        }
    }
    LaunchedEffect(phase) {
        if (phase == "countdown") {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            phase = "battle"
        }
    }

    fun beginQuestion() {'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 3. submit/finish：答完进 mydone；ready 发送 ----------
old = '''    fun submit(idx: Int) {'''
new = '''    fun sendReady() {
        myReady = true
        val readyMsg = buildJsonObject { put("t", "ready") }
        if (embedded != null) embedded!!.relayHostMessage(readyMsg) else client.rawSend(readyMsg.toString())
    }

    fun submit(idx: Int) {'''
assert old in src
src = src.replace(old, new, 1)

# nextOrFinish：最后一题后进 mydone（成绩已随 submit 上报；完成标记照发）
old = '''    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            val fin = buildJsonObject {
                put("t", "peer_finish"); put("correct", myCorrect); put("timeMs", totalTime)
            }
            if (embedded != null) embedded!!.hostFinish(myCorrect, totalTime)
            else client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }
    }'''
new = '''    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            val fin = buildJsonObject {
                put("t", "peer_finish"); put("correct", myCorrect); put("timeMs", totalTime)
            }
            if (embedded != null) embedded!!.hostFinish(myCorrect, totalTime)
            else client.sendFinish(myCorrect, totalTime)
            phase = "mydone"
        } else {
            qIndex++
        }
    }'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 4. 界面：matched/confirm/countdown/mydone 分支 + battle 顶部双进度 ----------
old = '''            "waiting" -> {'''
new = '''            "matched" -> {
                // 匹配成功动画 + 双方确认
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val scale = androidx.compose.animation.core.rememberInfiniteTransition()
                        .androidx.compose.animation.core.animateFloat(
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
            }
            "countdown" -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("$countdown", style = MaterialTheme.typography.displayLarge, fontSize = 96.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("准备开始！", style = MaterialTheme.typography.titleLarge)
                }
            }
            "mydone" -> {
                // 我方完成页：我的成绩 + 对手实时进度
                Column(
                    Modifier.fillMaxWidth().padding(top = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✅ 你已完成", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("答对 $myCorrect / ${questions.size} 题 · 用时 ${totalTime / 1000} 秒",
                        style = MaterialTheme.typography.titleMedium)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("对手进度：$peerAnswered/${questions.size} 题（答对 $peerCorrect）",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(
                                progress = { peerAnswered.toFloat() / questions.size },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(status, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            "waiting" -> {'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 5. battle：答题中移除"等对手"文案、完成页文案已换 ----------
old = '''                                if (answered) {
                                    Text(
                                        if (chosen == q?.answer) "✅ 答对了，等对手…" else "❌ 正确答案：${'A' + (q?.answer ?: 0)}. ${q?.options?.getOrNull(q.answer) ?: ""}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (chosen == q?.answer) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    )
                                }'''
new = '''                                if (answered) {
                                    Text(
                                        if (chosen == q?.answer) "✅ 正确" else "❌ 正确答案：${'A' + (q?.answer ?: 0)}. ${q?.options?.getOrNull(q.answer) ?: ""}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (chosen == q?.answer) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    )
                                }'''
assert old in src
src = src.replace(old, new, 1)

# ---------- 6. imports：动画与 graphicsLayer/Color 已有部分，补齐 ----------
old = 'import androidx.compose.foundation.layout.padding'
new = '''import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer'''
assert old in src
src = src.replace(old, new, 1)
old = 'import androidx.compose.ui.text.style.TextAlign'
new = 'import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.unit.sp'
if old in src and 'import androidx.compose.ui.unit.sp' not in src:
    src = src.replace(old, new, 1)

open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', 'w', encoding='utf-8').write(src)
print("PK six-phase flow OK")
