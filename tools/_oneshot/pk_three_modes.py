"""联机页三模式改造：创建(本机做服)/搜索/手动 + 对战内嵌/远程统一"""
src = open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', encoding='utf-8').read()

# 1. 状态：内嵌服务器 + 发现列表
old = '''    var result by remember { mutableStateOf<PkEvent.Result?>(null) }'''
new = '''    var result by remember { mutableStateOf<PkEvent.Result?>(null) }
    var embedded by remember { mutableStateOf<com.brainquest.game.net.EmbeddedPkServer?>(null) }
    var discovered by remember { mutableStateOf<Map<String, com.brainquest.game.net.PkDiscovery.Beacon>>(emptyMap()) }'''
assert old in src
src = src.replace(old, new, 1)

# 2. 事件流：PeerJoined 统一处理（内嵌/远程），加入方 start
old = '''            is PkEvent.PeerJoined -> {
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
                    phase = "battle"   // 房主不走 Start 事件，这里直接进对战
                    qIndex = 0; myCorrect = 0; peerCorrect = 0; totalTime = 0
                    status = "对战开始！"
                    android.util.Log.i("PkDebug", "选题完成 ${qs.size} 题，发送 start")
                    client.sendStart(qs)
                    android.util.Log.i("PkDebug", "start 已发送")
                }.onFailure { android.util.Log.e("PkDebug", "发题失败", it) }
            }'''
new = '''            is PkEvent.PeerJoined -> {
                status = "对手 ${event.peer} 已加入！"
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
            }'''
assert old in src
src = src.replace(old, new, 1)

# 3. PeerFinish/PeerLeft 提示（等待页也可见）
old = '''            is PkEvent.PeerFinish -> {
                status = "对手已完成 ${event.correct} 题，等待你完成…"
            }'''
new = '''            is PkEvent.PeerFinish -> {
                peerCorrect = event.correct
                status = "对手已完成 ${event.correct} 题，等待你完成…"
            }'''
assert old in src
src = src.replace(old, new, 1)

# 4. lobby：三模式 UI + 搜索
old = '''            "lobby" -> {
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
            }'''
new = '''            "lobby" -> {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("当前战绩：${player.pkWins} 胜 ${player.pkLosses} 负", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                // 本机做服务器：内嵌 WebSocket + UDP 信标
                                val code = (0..999999).random().toString().padStart(6, '0')
                                val srv = com.brainquest.game.net.EmbeddedPkServer(8765, player.nickname) { pkEvents.tryEmit(it) }
                                srv.roomCode = code
                                embedded = srv
                                srv.start()
                                com.brainquest.game.net.PkDiscovery.startBeacon(code, player.nickname, 8765)
                                roomCode = code
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
                                        client.connect("ws://${b.ip}:${b.tcpPort}", player.nickname, "join", b.room)
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
                            Text("⌨️ 手动连接（远程服务器 / 搜索失败兜底）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = serverUrl, onValueChange = { serverUrl = it },
                                label = { Text("对战服务器 / 房主IP") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            OutlinedTextField(
                                value = joinCode, onValueChange = { joinCode = it.take(6) },
                                label = { Text("房间码（连房主本机服务器时可不填）") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    vm.setSettings(pkServer = serverUrl)
                                    client.connect(serverUrl, player.nickname, "join", joinCode)
                                }, enabled = serverUrl.isNotBlank()) { Text("🚪 加入") }
                                OutlinedButton(onClick = {
                                    vm.setSettings(pkServer = serverUrl)
                                    client.connect(serverUrl, player.nickname, "create")
                                }) { Text("在远程服务器上创建") }
                            }
                        }
                    }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "💡 热点玩法：房主开手机热点并创建房间，朋友连热点后搜索即得——全程无需网络。\\n搜不到时多为路由器 AP 隔离，可改用热点或手动输 IP。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }'''
assert old in src
src = src.replace(old, new, 1)

# 5. searching 状态 + 发现回调 + 退出清理
old = '''    var discovered by remember { mutableStateOf<Map<String, com.brainquest.game.net.PkDiscovery.Beacon>>(emptyMap()) }'''
new = '''    var discovered by remember { mutableStateOf<Map<String, com.brainquest.game.net.PkDiscovery.Beacon>>(emptyMap()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(searching) {
        if (searching) {
            com.brainquest.game.net.PkDiscovery.startListening { b ->
                discovered = discovered + (b.room to b)
            }
        } else com.brainquest.game.net.PkDiscovery.stopListening()
    }'''
assert old in src
src = src.replace(old, new, 1)

# 6. 创建/加入后停止搜索；退出页清理（DisposableEffect 已关 client，补内嵌+信标）
old = '''    DisposableEffect(Unit) { onDispose { client.close() } }'''
new = '''    DisposableEffect(Unit) {
        onDispose {
            client.close()
            embedded?.stopServer()
            com.brainquest.game.net.PkDiscovery.stopBeacon()
            com.brainquest.game.net.PkDiscovery.stopListening()
        }
    }'''
assert old in src
src = src.replace(old, new, 1)

# 7. 再来一局/返回：清理内嵌与信标
old = '''                    Button(onClick = { client.close(); phase = "lobby" }, modifier = Modifier.fillMaxWidth()) {
                        Text("再来一局")
                    }'''
new = '''                    Button(onClick = {
                        client.close(); embedded?.stopServer()
                        com.brainquest.game.net.PkDiscovery.stopBeacon()
                        embedded = null; phase = "lobby"
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("再来一局")
                    }'''
assert old in src
src = src.replace(old, new, 1)

# 8. submit/nextOrFinish 统一走 relay（内嵌/远程）
old = '''        client.sendAnswer(qIndex, correct, spent)'''
new = '''        val peerMsg = buildJsonObject {
            put("t", "peer_answer"); put("idx", qIndex); put("correct", correct); put("timeMs", spent)
        }
        if (embedded != null) embedded!!.relayHostMessage(peerMsg) else client.sendAnswer(qIndex, correct, spent)'''
assert old in src
src = src.replace(old, new, 1)

old = '''        if (qIndex + 1 >= questions.size) {
            client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }'''
new = '''        if (qIndex + 1 >= questions.size) {
            val fin = buildJsonObject {
                put("t", "peer_finish"); put("correct", myCorrect); put("timeMs", totalTime)
            }
            if (embedded != null) embedded!!.hostFinish(myCorrect, totalTime)
            else client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }'''
assert old in src
src = src.replace(old, new, 1)

# 9. 服务器地址默认值用记忆值
old = '''    var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8765") }'''
new = '''    var serverUrl by remember { mutableStateOf(player.pkServerUrl) }'''
assert old in src
src = src.replace(old, new, 1)

# 10. imports
old = 'import androidx.compose.foundation.layout.height'
new = 'import androidx.compose.foundation.layout.height\nimport androidx.compose.material3.OutlinedButton'
assert src.count(old) == 1
src = src.replace(old, new, 1)

open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', 'w', encoding='utf-8').write(src)
print("lobby/battle three-mode OK")
