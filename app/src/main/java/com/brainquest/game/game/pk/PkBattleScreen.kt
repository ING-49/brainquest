package com.brainquest.game.game.pk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.question.Question
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.BuildConfig
import com.brainquest.game.net.PkClient
import com.brainquest.game.net.PkDiscovery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.brainquest.game.net.PkEvent
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay

private const val QUESTION_TIME_MS = 15_000L
private const val PK_QUESTION_COUNT = 10
private const val DEFAULT_PK_SERVER = "ws://8.148.192.129:8765"      // 公网对战服务器
private const val LEGACY_EMULATOR_SERVER = "ws://10.0.2.2:8765"      // 旧默认（模拟器本机），升级时迁移

/** 联机对战：好友码房间制（阶段一），10 题同答，答对多且快者胜 */
@Composable
fun PkBattleScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 阶段：lobby → waiting → battle → result
    var phase by remember { mutableStateOf("lobby") }
    // 旧默认是模拟器地址，升级后迁移到公网对战服务器
    var serverUrl by remember {
        mutableStateOf(if (player.pkServerUrl == LEGACY_EMULATOR_SERVER) DEFAULT_PK_SERVER else player.pkServerUrl)
    }
    var roomCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var hostMode by remember { mutableStateOf(false) }
    var pkTab by remember { mutableStateOf("remote") }         // lobby 页签：remote / lan
    var remoteConnected by remember { mutableStateOf(false) }  // 与远程服务器的空闲长连接
    var connectedUrl by remember { mutableStateOf("") }        // 空闲连接对应的服务器地址
    var matching by remember { mutableStateOf(false) }         // 快速匹配中
    var onlinePlayers by remember { mutableIntStateOf(-1) }    // -1 = 尚未获取
    var onlineWaiting by remember { mutableIntStateOf(0) }
    var onlineRooms by remember { mutableIntStateOf(0) }
    var matchSubject by remember { mutableStateOf<String?>(null) } // 房主出题科目，null = 混合
    var lanUrl by remember { mutableStateOf("") }              // 局域网手动直连地址
    var showServerEdit by remember { mutableStateOf(false) }   // 长按在线行弹出的服务器地址编辑框
    var showBoard by remember { mutableStateOf(false) }        // 排行榜对话框
    var board by remember { mutableStateOf<List<PkEvent.RankRow>?>(null) }
    var boardMe by remember { mutableStateOf<PkEvent.RankRow?>(null) }
    var pendingBoard by remember { mutableStateOf(false) }     // 连接建立后自动查询排行榜
    var boardSubject by remember { mutableStateOf("混合") }    // 排行榜当前科目（默认混合）
    var pendingMatch by remember { mutableStateOf(false) }     // 连接建立后自动发起快速匹配
    var lanSession by remember { mutableStateOf(false) }       // 本局是否局域网/热点对战（不计分标注用）

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var qIndex by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var chosen by remember { mutableIntStateOf(-1) }
    var myCorrect by remember { mutableIntStateOf(0) }
    var peerCorrect by remember { mutableIntStateOf(0) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var qStartAt by remember { mutableLongStateOf(0L) }
    var timeLeftMs by remember { mutableLongStateOf(QUESTION_TIME_MS) }
    var result by remember { mutableStateOf<PkEvent.Result?>(null) }
    var embedded by remember { mutableStateOf<com.brainquest.game.net.EmbeddedPkServer?>(null) }
    var discovered by remember { mutableStateOf<Map<String, com.brainquest.game.net.PkDiscovery.Beacon>>(emptyMap()) }
    var searching by remember { mutableStateOf(false) }
    var peerVersion by remember { mutableStateOf("") }
    var peerName by remember { mutableStateOf("") }
    var joinedAsGuest by remember { mutableStateOf(false) }
    var myReady by remember { mutableStateOf(false) }
    var peerReady by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var peerAnswered by remember { mutableIntStateOf(0) }  // 对手已作答题数（含未判分提交）
    val myIps = remember { com.brainquest.game.net.PkDiscovery.localIps() }

    // 连续对局：每局开打前必须清零上一局残留（房主路径不经 Start 事件，此前漏重置）
    fun resetBattleState() {
        qIndex = 0
        answered = false
        chosen = -1
        myCorrect = 0
        peerCorrect = 0
        peerAnswered = 0
        totalTime = 0
        timeLeftMs = QUESTION_TIME_MS
    }

    LaunchedEffect(searching) {
        if (searching) {
            com.brainquest.game.net.PkDiscovery.startListening { b ->
                discovered = discovered + (b.room to b)
            }
        } else com.brainquest.game.net.PkDiscovery.stopListening()
    }

    val pkEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<PkEvent>(extraBufferCapacity = 32) }
    val client = remember { PkClient { pkEvents.tryEmit(it) } }

    // 远程页签：维持空闲长连接以显示在线人数（地址变更防抖后自动重连）；切到局域网页签时断开
    LaunchedEffect(pkTab, phase, serverUrl) {
        if (phase != "lobby") return@LaunchedEffect
        if (pkTab == "remote") {
            delay(1500)  // 地址输入防抖：输完再连
            if (phase != "lobby") return@LaunchedEffect
            if (!remoteConnected || connectedUrl != serverUrl) {
                if (client.connect(serverUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)) {
                    connectedUrl = serverUrl
                }
            }
            while (true) {
                delay(15_000)
                if (remoteConnected && connectedUrl == serverUrl) client.sendOnlineQuery()
            }
        } else {
            client.close()
            remoteConnected = false
            onlinePlayers = -1
            if (status == "已连接") status = ""
        }
    }

    LaunchedEffect(Unit) {
        pkEvents.collect { event ->
        when (event) {
            is PkEvent.Connected -> {
                remoteConnected = true
                status = when {
                    matching -> "已连接，正在匹配对手…"
                    event.hostMode -> "已连接，房间创建中…"
                    else -> "已连接"
                }
                if (pendingMatch) {
                    client.sendQuickMatch(player.nickname, BuildConfig.VERSION_NAME, matchSubject ?: "混合")
                    pendingMatch = false
                }
                if (pendingBoard) {
                    client.sendLeaderboard(player.nickname, boardSubject)
                    pendingBoard = false
                }
            }
            is PkEvent.Created -> {
                roomCode = event.code
                hostMode = true
                matching = false
                status = "房间已创建，等待对手加入…"
                phase = "waiting"
            }
            is PkEvent.Joined -> {
                roomCode = event.code
                joinedAsGuest = true
                peerName = event.peer
                matching = false
                status = "已加入房间，等待双方准备…"
                phase = "matched"
            }
            is PkEvent.PeerJoined -> {
                peerName = event.peer
                status = "匹配成功！"
                phase = "matched"   // 匹配成功动画 + 双方确认
                if (embedded != null) {
                    // 仅局域网/热点对战（内嵌服务器无题库）：房主本地选题
                    runCatching {
                        val qs = buildList {
                            val used = mutableSetOf<String>()
                            var guard = 0
                            while (size < PK_QUESTION_COUNT && guard < 150) {
                                val subject = matchSubject ?: Subjects.all[guard % Subjects.all.size]
                                val q = vm.bank.pick(subject, 2 + guard % 3)
                                if (q != null && q.type != "fill" && q.id !in used) {
                                    add(q); used.add(q.id)
                                }
                                guard++
                            }
                        }
                        questions = qs
                        embedded?.setQuestions(qs)
                    }.onFailure { status = "发题失败：${it.message}" }
                }
                // 远程对战：题目由服务器在双方 ready 后下发（v1.6.9 服务器中立对战）
                PkDiscovery.stopBeacon()
            }
            is PkEvent.Start -> {
                questions = event.questions
                resetBattleState()   // 连续对局：清零上一局残留进度
                myReady = false; peerReady = false
                countdown = 3       // 进入 3·2·1 倒计时
                phase = "countdown"
            }
            is PkEvent.PeerReady -> {
                peerReady = true
            }
            is PkEvent.PeerAnswer -> {
                peerAnswered = event.idx + 1
                if (event.correct) peerCorrect++
            }
            is PkEvent.PeerFinish -> {
                peerCorrect = event.correct
                status = "对手已完成 ${event.correct} 题，等待你完成…"
            }
            is PkEvent.Result -> {
                result = event
                phase = "result"
                vm.recordPkResult(event.outcome == "win", event.outcome == "draw")
                Sfx.play(context, player.soundOn, SfxType.WIN)
            }
            is PkEvent.PeerLeft -> {
                status = "对手离开了房间"
                if (phase == "battle") { phase = "lobby"; client.close() }
            }
            is PkEvent.Online -> {
                onlinePlayers = event.players
                onlineWaiting = event.waiting
                onlineRooms = event.rooms
            }
            is PkEvent.Leaderboard -> {
                board = event.top
                boardMe = event.me
            }
            is PkEvent.Disconnected -> {
                remoteConnected = false
                if (matching) matching = false
            }
            is PkEvent.Error -> {
                // 大厅里后台空闲连接的失败不打扰用户（地址没输完/网络抖动），战斗与匹配中的错误仍显示
                if (!(event.msg.startsWith("连接") && phase == "lobby" && !matching)) {
                    status = event.msg
                }
                android.util.Log.w("PkDebug", "服务器消息: ${event.msg}")
            }
            else -> {}  // 云存档等事件由设置页处理
        }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            client.close()
            embedded?.stopServer()
            com.brainquest.game.net.PkDiscovery.stopBeacon()
            com.brainquest.game.net.PkDiscovery.stopListening()
        }
    }

    // 返回键：非大厅阶段先取消会话回联机大厅，大厅再返回才离开页面
    fun backToLobby() {
        client.close()
        embedded?.stopServer()
        embedded = null
        com.brainquest.game.net.PkDiscovery.stopBeacon()
        com.brainquest.game.net.PkDiscovery.stopListening()
        searching = false
        matching = false
        myReady = false; peerReady = false
        joinedAsGuest = false
        peerName = ""; roomCode = ""
        result = null
        remoteConnected = false
        lanSession = false
        resetBattleState()
        status = ""
        phase = "lobby"
    }

    // 排行榜查询：切换科目并拉取（默认混合）
    fun queryBoard(subject: String) {
        boardSubject = subject
        board = null; boardMe = null
        if (remoteConnected && connectedUrl == serverUrl) {
            client.sendLeaderboard(player.nickname, subject)
        } else if (client.connect(serverUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)) {
            connectedUrl = serverUrl
            pendingBoard = true
        }
    }

    androidx.activity.compose.BackHandler(enabled = phase != "lobby") { backToLobby() }

    // 双方都点了准备 → 房主发题（内嵌广播/远程 sendStart），双方进倒计时
    LaunchedEffect(myReady, peerReady) {
        if (phase == "matched" && myReady && peerReady) {
            delay(600)  // 让"匹配成功"动画呼吸一下
            if (embedded != null) {
                embedded!!.broadcastStart()          // 房主(本机做服)：发题给乙方
                resetBattleState()  // 连续对局：房主路径不经 Start 事件，这里必须清零
                phase = "countdown"; countdown = 3
            }
            // 远程对战：双方 ready 消息已到服务器，等服务器抽题下发 PkEvent.Start
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

    fun beginQuestion() {
        qStartAt = System.currentTimeMillis()
    }

    fun sendReady() {
        myReady = true
        val readyMsg = buildJsonObject { put("t", "ready") }
        if (embedded != null) embedded!!.relayHostMessage(readyMsg) else client.rawSend(readyMsg.toString())
    }

    fun submit(idx: Int) {
        val q = questions.getOrNull(qIndex) ?: return
        val correct = idx == q.answer          // 本机即时反馈；最终成绩以服务器判分为准（v1.6.9）
        val spent = System.currentTimeMillis() - qStartAt
        totalTime += spent
        if (correct) myCorrect++
        answered = true
        chosen = idx
        Sfx.play(context, player.soundOn, if (correct) SfxType.CORRECT else SfxType.WRONG)
        vm.recordAnswer(q, idx)
        val peerMsg = buildJsonObject {
            put("t", "peer_answer"); put("idx", qIndex); put("correct", correct); put("timeMs", spent)
        }
        if (embedded != null) embedded!!.relayHostMessage(peerMsg) else client.sendAnswer(qIndex, idx, spent)
    }

    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            val fin = buildJsonObject {
                put("t", "peer_finish"); put("correct", myCorrect); put("timeMs", totalTime)
            }
            if (embedded != null) embedded!!.hostFinish(myCorrect, totalTime)
            else client.sendFinish(totalTime)
            phase = "mydone"
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

    // 答完停留 0.75s 自动进下一题（最后一题自动交卷），无需手动点击
    LaunchedEffect(answered, qIndex, phase) {
        if (phase == "battle" && answered) {
            delay(750)
            nextOrFinish()
        }
    }

    // ---------- 界面 ----------
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader("⚔️ 联机对战", onBack = {
            if (phase != "lobby") backToLobby() else nav.popBackStack()
        }, subtitle = "远程匹配 / 好友房间 / 局域网热点 · 10 题同答")

        when (phase) {
            "lobby" -> {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 战绩 + 模式页签
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("当前战绩：${player.pkWins} 胜 ${player.pkLosses} 负", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = pkTab == "remote", onClick = { pkTab = "remote" },
                                    label = { Text("🌐 远程对战") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = pkTab == "lan", onClick = { pkTab = "lan" },
                                    label = { Text("🏠 局域网") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (pkTab == "remote") {
                        // ---- 远程：在线人数 + 快速匹配 + 好友房间 ----
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌐 远程对战（服务器中转 · 随时随地）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                // 服务器地址默认公网且不显示；长按在线行可改（调试/自建用）
                                Text(
                                    when {
                                        onlinePlayers < 0 -> "… 正在连接服务器获取在线人数"
                                        onlinePlayers <= 1 -> "🟢 在线 $onlinePlayers 人 · 房间 $onlineRooms（现在只有你，喊朋友来吧）"
                                        else -> "🟢 在线 $onlinePlayers 人 · 等待匹配 $onlineWaiting 人 · 房间 $onlineRooms"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                                        detectTapGestures(onLongPress = { showServerEdit = true })
                                    },
                                )
                                if (!matching) {
                                    Button(onClick = {
                                        matching = true
                                        lanSession = false
                                        vm.setSettings(pkServer = serverUrl)
                                        val subject = matchSubject ?: "混合"
                                        if (remoteConnected && connectedUrl == serverUrl) {
                                            client.sendQuickMatch(player.nickname, BuildConfig.VERSION_NAME, subject)
                                        } else if (client.connect(serverUrl, player.nickname, "idle", version = BuildConfig.VERSION_NAME)) {
                                            connectedUrl = serverUrl
                                            pendingMatch = true
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("⚡ 快速匹配（同版本随机对手）") }
                                } else {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            if (onlineWaiting > 0) "匹配中…（服务器等待 $onlineWaiting 人）" else "匹配中…",
                                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        OutlinedButton(onClick = {
                                            client.sendCancelMatch()
                                            matching = false
                                            status = "已取消匹配"
                                        }) { Text("取消") }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    showBoard = true
                                    queryBoard("混合")   // 打开优先显示混合榜
                                }, modifier = Modifier.fillMaxWidth()) { Text("🏆 排行榜（快速匹配积分）") }
                                HorizontalDivider()
                                Text("对战科目（房主出题用）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = matchSubject == null, onClick = { matchSubject = null }, label = { Text("🎲 混合") })
                                    Subjects.all.forEach { s ->
                                        FilterChip(selected = matchSubject == s, onClick = { matchSubject = s }, label = { Text("${Subjects.emoji(s)} $s") })
                                    }
                                }
                                HorizontalDivider()
                                Text("👥 好友房间（把房间码告诉朋友）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = joinCode, onValueChange = { joinCode = it.take(6) },
                                    label = { Text("房间码") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        lanSession = false
                                        vm.setSettings(pkServer = serverUrl)
                                        client.connect(serverUrl, player.nickname, "join", joinCode, BuildConfig.VERSION_NAME)
                                    }, enabled = serverUrl.isNotBlank()) { Text("🚪 加入房间") }
                                    OutlinedButton(onClick = {
                                        lanSession = false
                                        vm.setSettings(pkServer = serverUrl)
                                        client.connect(serverUrl, player.nickname, "create", version = BuildConfig.VERSION_NAME, subject = matchSubject ?: "混合")
                                    }) { Text("🏠 创建房间") }
                                }
                            }
                        }
                        if (showServerEdit) {
                            AlertDialog(
                                onDismissRequest = { showServerEdit = false },
                                confirmButton = {
                                    Button(onClick = {
                                        showServerEdit = false
                                        vm.setSettings(pkServer = serverUrl)
                                    }) { Text("保存") }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showServerEdit = false }) { Text("取消") }
                                },
                                title = { Text("对战服务器地址") },
                                text = {
                                    OutlinedTextField(
                                        value = serverUrl, onValueChange = { serverUrl = it },
                                        label = { Text("ws://主机:端口") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    )
                                },
                            )
                        }
                        if (showBoard) {
                            AlertDialog(
                                onDismissRequest = { showBoard = false },
                                confirmButton = { Button(onClick = { showBoard = false }) { Text("关闭") } },
                                title = { Text("🏆 排行榜 · $boardSubject") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("仅快速匹配计分 · 对战科目由房主选定",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(selected = boardSubject == "混合", onClick = { queryBoard("混合") },
                                                label = { Text("🎲 混合") })
                                            Subjects.all.forEach { s ->
                                                FilterChip(selected = boardSubject == s, onClick = { queryBoard(s) },
                                                    label = { Text("${Subjects.emoji(s)} $s") })
                                            }
                                        }
                                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                        val list = board
                                        when {
                                            list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                Text("  加载中…", style = MaterialTheme.typography.bodyMedium)
                                            }
                                            list.isEmpty() -> Text("该科目还没有人上榜，来打一局快速匹配吧！",
                                                style = MaterialTheme.typography.bodyMedium)
                                            else -> {
                                                list.forEachIndexed { i, r ->
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("${i + 1}. ${r.name}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (r.name == player.nickname) FontWeight.Bold else FontWeight.Normal)
                                                        Text("${r.rating} 分 · ${r.wins}胜${r.losses}负",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                                val me = boardMe
                                                if (me != null && me.rank > 0) {
                                                    Text("我的排名：第 ${me.rank} 名 · ${me.rating} 分（${me.wins} 胜 ${me.losses} 负）",
                                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("我还没上榜，打一局快速匹配即可计分上榜",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        // ---- 局域网：创建（本机做服务器）+ 搜索 + 手动直连 ----
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    // 本机做服务器：内嵌 WebSocket + UDP 信标
                                    val code = (0..999999).random().toString().padStart(6, '0')
                                    val srv = com.brainquest.game.net.EmbeddedPkServer(8765, player.nickname, BuildConfig.VERSION_NAME) { pkEvents.tryEmit(it) }
                                    srv.roomCode = code
                                    embedded = srv
                                    srv.start()
                                    com.brainquest.game.net.PkDiscovery.startBeacon(code, player.nickname, 8765)
                                    roomCode = code
                                    lanSession = true
                                    status = "房间已创建，等待对手加入…"
                                    phase = "waiting"
                                }, modifier = Modifier.fillMaxWidth()) { Text("🏠 创建房间（本机做服务器 · 热点可离线）") }
                                OutlinedButton(onClick = { searching = !searching }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (searching) "收起搜索" else "🔍 搜索附近的房间")
                                }
                                if (searching) {
                                    if (discovered.isEmpty()) {
                                        Text("正在搜索同一网络下的房间…（若搜不到可手动输 IP）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    discovered.values.forEach { b ->
                                        Card(onClick = {
                                            lanSession = true
                                            client.connect("ws://${b.ip}:${b.tcpPort}", player.nickname, "join", b.room, BuildConfig.VERSION_NAME)
                                            PkDiscovery.stopListening()
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(10.dp)) {
                                                Text("🎮 ${b.name} 的房间 ${b.room}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                Text("${b.ip}:${b.tcpPort}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⌨️ 手动连接（输入房主 IP · 搜索失败兜底）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = lanUrl, onValueChange = { lanUrl = it },
                                    label = { Text("房主IP（如 ws://192.168.1.5:8765）") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                )
                                OutlinedTextField(
                                    value = joinCode, onValueChange = { joinCode = it.take(6) },
                                    label = { Text("房间码（连房主本机服务器时可不填）") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                )
                                Button(onClick = {
                                    lanSession = true
                                    client.connect(lanUrl, player.nickname, "join", joinCode, BuildConfig.VERSION_NAME)
                                }, enabled = lanUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("🚪 直连加入") }
                            }
                        }
                    }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "💡 远程对战需联网，由对战服务器中转；局域网热点玩法：房主开热点并创建房间，朋友连热点后搜索即得——全程无需网络。\n搜不到时多为路由器 AP 隔离，可改用热点或手动输 IP。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            "matched" -> {
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
            "waiting" -> {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("🎮", style = MaterialTheme.typography.displayMedium)
                    Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (roomCode.isNotBlank()) {
                        Text("房间码 $roomCode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡 本机对战地址（朋友端『手动连接』输入）：",
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        myIps.forEach { ip ->
                            Text("ws://$ip:8765",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("把房间码告诉你的朋友，等 TA 加入后自动开始", style = MaterialTheme.typography.bodySmall)
                }
            }
            "battle" -> {
                val q = questions.getOrNull(qIndex)
                key(qIndex) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        // 双方进度 x/10（对手实时更新）
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🧑 我 ${if (answered) qIndex + 1 else qIndex}/${questions.size}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("第 ${qIndex + 1}/${questions.size} 题 · ⏳ ${timeLeftMs / 1000}s",
                                color = if (timeLeftMs < 5000) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge)
                            Text("对手 ${peerAnswered.coerceAtMost(questions.size)}/${questions.size}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                            }
                        }
                        q?.let {
                            com.brainquest.game.ui.QuizOptionList(
                                options = it.options,
                                answer = it.answer,
                                chosen = if (answered) chosen else -1,
                                revealed = answered,
                                onChoose = { i -> submit(i) },
                                explanation = it.explanation,
                            )
                        }  // 答完标绿/红框并显示解析，0.75s 后自动进下一题
                    }
                }
            }
            "result" -> {
                val outcome = result!!.outcome
                val myC = result!!.myCorrect
                val peerC = result!!.peerCorrect
                Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(if (outcome == "win") "🏆 胜利！" else if (outcome == "lose") "💀 惜败" else "🤝 平局",
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("我 $myC 题 ｜ 对手 $peerC 题", style = MaterialTheme.typography.titleMedium)
                    if (result!!.ranked) {
                        Text("🏅 积分 ${result!!.myRating}（${if (result!!.ratingDelta >= 0) "+" else ""}${result!!.ratingDelta}）",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32))
                    } else {
                        Text(if (lanSession) "局域网对战 · 不计分" else "好友房间 · 不计分（仅快速匹配计分）",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("我的用时 ${result!!.myTimeMs / 1000} 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { backToLobby() }, modifier = Modifier.fillMaxWidth()) {
                        Text("再来一局")
                    }
                }
            }
        }
    }
}


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
