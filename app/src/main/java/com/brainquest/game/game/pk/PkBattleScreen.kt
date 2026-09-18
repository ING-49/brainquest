package com.brainquest.game.game.pk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.question.Question
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.net.PkClient
import com.brainquest.game.net.PkEvent
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay

private const val QUESTION_TIME_MS = 15_000L
private const val PK_QUESTION_COUNT = 10

/** 联机对战：好友码房间制（阶段一），10 题同答，答对多且快者胜 */
@Composable
fun PkBattleScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 阶段：lobby → waiting → battle → result
    var phase by remember { mutableStateOf("lobby") }
    var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8765") }
    var roomCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var hostMode by remember { mutableStateOf(false) }

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var qIndex by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var chosen by remember { mutableIntStateOf(-1) }
    var myCorrect by remember { mutableIntStateOf(0) }
    var peerCorrect by remember { mutableIntStateOf(0) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var qStartAt by remember { mutableLongStateOf(0L) }
    var timeLeftMs by remember { mutableLongStateOf(QUESTION_TIME_MS) }
    var result by remember { mutableStateOf<Triple<String, Int, Int>?>(null) } // (outcome, myC, peerC)

    val pkEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<PkEvent>(extraBufferCapacity = 32) }
    val client = remember { PkClient { pkEvents.tryEmit(it) } }

    LaunchedEffect(Unit) {
        pkEvents.collect { event ->
        when (event) {
            is PkEvent.Connected -> {
                status = if (event.hostMode) "已连接，房间创建中…" else "已连接，加入中…"
            }
            is PkEvent.Created -> {
                roomCode = event.code
                hostMode = true
                status = "房间已创建，等待对手加入…"
                phase = "waiting"
            }
            is PkEvent.Joined -> {
                roomCode = event.code; status = "已加入房间，对手：${event.peer}"
                phase = "waiting"
            }
            is PkEvent.PeerJoined -> {
                android.util.Log.i("PkDebug", "对手加入，开始发题")
                status = "对手 ${event.peer} 已加入！"
                // 服务器只把 peer_joined 发给房主 → 直接选题开局
                runCatching {
                    val qs = buildList {
                        // 混合抽题：数学生成 + 各科题库（不含填空），去重
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
                    android.util.Log.i("PkDebug", "选题完成 ${qs.size} 题，发送 start")
                    client.sendStart(qs)
                    android.util.Log.i("PkDebug", "start 已发送")
                }.onFailure { android.util.Log.e("PkDebug", "发题失败", it) }
            }
            is PkEvent.Start -> {
                questions = event.questions
                phase = "battle"
                qIndex = 0; myCorrect = 0; peerCorrect = 0; totalTime = 0
                status = "对战开始！"
            }
            is PkEvent.PeerAnswer -> {
                if (event.correct) peerCorrect++
            }
            is PkEvent.PeerFinish -> {
                status = "对手已完成 ${event.correct} 题，等待你完成…"
            }
            is PkEvent.Result -> {
                result = Triple(event.outcome, event.myCorrect, event.peerCorrect)
                phase = "result"
                vm.recordPkResult(event.outcome == "win", event.outcome == "draw")
                Sfx.play(context, player.soundOn, SfxType.WIN)
            }
            is PkEvent.PeerLeft -> {
                status = "对手离开了房间"
                if (phase == "battle") { phase = "lobby"; client.close() }
            }
            is PkEvent.Error -> status = event.msg
        }
        }
    }

    DisposableEffect(Unit) { onDispose { client.close() } }

    fun beginQuestion() {
        qStartAt = System.currentTimeMillis()
    }

    fun submit(idx: Int) {
        val q = questions.getOrNull(qIndex) ?: return
        val correct = idx == q.answer
        val spent = System.currentTimeMillis() - qStartAt
        totalTime += spent
        if (correct) myCorrect++
        answered = true
        chosen = idx
        Sfx.play(context, player.soundOn, if (correct) SfxType.CORRECT else SfxType.WRONG)
        vm.recordAnswer(q, idx)
        client.sendAnswer(qIndex, correct, spent)
    }

    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }
    }

    // 计时器
    LaunchedEffect(phase, qIndex) {
        if (phase == "battle" && qIndex < questions.size) {
            qStartAt = System.currentTimeMillis()
            timeLeftMs = QUESTION_TIME_MS
            answered = false; chosen = -1
            while (timeLeftMs > 0 && !answered) {
                delay(100)
                timeLeftMs = QUESTION_TIME_MS - (System.currentTimeMillis() - qStartAt)
            }
            if (!answered) submit(-1)  // 超时按未答
        }
    }

    // ---------- 界面 ----------
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader("⚔️ 联机对战", onBack = {
            client.close(); phase = "lobby"; nav.popBackStack()
        }, subtitle = "好友码房间制 · 10 题同答 · 答对多且快者胜")

        when (phase) {
            "lobby" -> {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("当前战绩：${player.pkWins} 胜 ${player.pkLosses} 负", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = serverUrl, onValueChange = { serverUrl = it },
                            label = { Text("对战服务器") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                        Button(onClick = {
                            client.connect(serverUrl, player.nickname, "create")
                        }, modifier = Modifier.fillMaxWidth()) { Text("🏠 创建房间") }
                        OutlinedTextField(
                            value = joinCode, onValueChange = { joinCode = it.take(6) },
                            label = { Text("输入好友的房间码") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                        Button(onClick = {
                            if (joinCode.length == 6) client.connect(serverUrl, player.nickname, "join", joinCode)
                        }, modifier = Modifier.fillMaxWidth(), enabled = joinCode.length == 6) { Text("🚪 加入房间") }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "💡 两台设备连同一网络（电脑跑 pk_server.py），或今后部署到公网随时玩。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            "waiting" -> {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("🎮", style = MaterialTheme.typography.displayMedium)
                    Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (roomCode.isNotBlank() && roomCode != "（见服务器日志）") {
                        Text("房间码 $roomCode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("把房间码告诉你的朋友，等 TA 加入后自动开始", style = MaterialTheme.typography.bodySmall)
                }
            }
            "battle" -> {
                val q = questions.getOrNull(qIndex)
                key(qIndex) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        // 双方进度
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🧑 ${player.nickname}: $myCorrect", style = MaterialTheme.typography.labelLarge)
                            Text("第 ${qIndex + 1}/${questions.size} 题 · ⏳ ${timeLeftMs / 1000}s",
                                color = if (timeLeftMs < 5000) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge)
                            Text("对手: $peerCorrect", style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(
                            progress = { (timeLeftMs.toFloat() / QUESTION_TIME_MS).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Card(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${Subjects.emoji(q?.subject ?: "")} ${q?.subject ?: ""} · 难度${q?.difficulty ?: ""}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(q?.question ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp))
                                if (answered) {
                                    Text(
                                        if (chosen == q?.answer) "✅ 答对了，等对手…" else "❌ 正确答案：${'A' + (q?.answer ?: 0)}. ${q?.options?.getOrNull(q.answer) ?: ""}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (chosen == q?.answer) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    )
                                }
                            }
                        }
                        if (!answered) {
                            q?.options?.forEachIndexed { i, opt ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    onClick = { submit(i) },
                                ) {
                                    Text("${'A' + i}. $opt", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(12.dp))
                                }
                            }
                        } else {
                            Button(onClick = { nextOrFinish() }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (qIndex + 1 >= questions.size) "完成（提交成绩）" else "下一题 →")
                            }
                        }
                    }
                }
            }
            "result" -> {
                val (outcome, myC, peerC) = result!!
                Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(if (outcome == "win") "🏆 胜利！" else if (outcome == "lose") "💀 惜败" else "🤝 平局",
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("我 $myC 题 ｜ 对手 $peerC 题", style = MaterialTheme.typography.titleMedium)
                    Text("用时 ${(result!!.second)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { client.close(); phase = "lobby" }, modifier = Modifier.fillMaxWidth()) {
                        Text("再来一局")
                    }
                }
            }
        }
    }
}
