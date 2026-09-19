package com.brainquest.game.net

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.brainquest.game.data.question.Question
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 房主手机内嵌对战服务器：把 pk_server.py 的房间逻辑跑在本机。
 * 协议与 PC 服务器完全一致 → 加入方 PkClient 零改动。
 */
class EmbeddedPkServer(
    private val port: Int,
    private val hostName: String,
    private val hostVersion: String,
    private val onEvent: (PkEvent) -> Unit,
) : WebSocketServer(InetSocketAddress(port)) {

    private val json = Json { ignoreUnknownKeys = true }
    private var guest: WebSocket? = null
    private var guestName: String = ""
    private val finishes = mutableMapOf<WebSocket, Pair<Int, Long>>()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /** 界面可观察的连接状态 */
    var guestJoined by mutableStateOf(false)
        private set

    private val questions = mutableListOf<Question>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) { /* 等 join 消息 */ }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        if (conn == guest) {
            guest = null
            guestJoined = false
            onEvent(PkEvent.PeerLeft)
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (obj["t"]?.jsonPrimitive?.content) {
            "join" -> {
                if (guest != null) {
                    sendTo(conn, buildJsonObject { put("t", "error"); put("msg", "房间已满") }.toString())
                    return
                }
                val peerVer = obj["version"]?.jsonPrimitive?.content ?: "?"
                if (peerVer != hostVersion) {
                    sendTo(conn, buildJsonObject {
                        put("t", "error")
                        put("msg", "版本不一致（你 v$peerVer / 房主 v$hostVersion），请双方都更新到最新版")
                    }.toString())
                    return
                }
                guest = conn
                guestName = obj["name"]?.jsonPrimitive?.content ?: "玩家"
                guestJoined = true
                sendTo(conn, buildJsonObject {
                    put("t", "joined"); put("code", roomCode); put("peer", hostName)
                }.toString())
                onEvent(PkEvent.PeerJoined(guestName))
            }
            "start" -> { /* 房主本地处理，不走网络 */ }
            "answer" -> {
                // 加入方的作答 → 通知房主界面（更新对手进度）
                val idx = obj["idx"]?.jsonPrimitive?.content?.toInt() ?: 0
                val correct = obj["correct"]?.jsonPrimitive?.content?.toBoolean() ?: false
                onEvent(PkEvent.PeerAnswer(idx, correct))
            }
            "finish" -> {
                // 加入方完成 → 记录；双方都完成则判定
                guestFinish = (obj["correct"]?.jsonPrimitive?.content?.toInt() ?: 0) to
                    (obj["timeMs"]?.jsonPrimitive?.content?.toLong() ?: 0L)
                onEvent(PkEvent.PeerFinish(guestFinish!!.first, guestFinish!!.second))
                tryResult()
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) { /* 由 onClose 统一处理 */ }

    override fun onStart() { }

    var roomCode: String = ""

    fun setQuestions(qs: List<Question>) {
        questions.clear()
        questions.addAll(qs)
    }

    private var hostFinish: Pair<Int, Long>? = null
    private var guestFinish: Pair<Int, Long>? = null

    /** 房主完成 → 转发对手 + 尝试判定 */
    fun hostFinish(correct: Int, timeMs: Long) {
        hostFinish = correct to timeMs
        sendTo(guest, buildJsonObject {
            put("t", "peer_finish"); put("correct", correct); put("timeMs", timeMs)
        }.toString())
        tryResult()
    }

    private fun tryResult() {
        val hf = hostFinish ?: return
        val gf = guestFinish ?: return
        val outcome = when {
            hf.first != gf.first -> if (hf.first > gf.first) "win" else "lose"
            hf.second != gf.second -> if (hf.second < gf.second) "win" else "lose"
            else -> "draw"
        }
        onEvent(PkEvent.Result(outcome, hf.first, hf.second, gf.first, gf.second))
        sendTo(guest, buildJsonObject {
            put("t", "result"); put("outcome", if (outcome == "win") "lose" else if (outcome == "lose") "win" else "draw")
            put("my", buildJsonObject { put("correct", gf.first); put("timeMs", gf.second) })
            put("peer", buildJsonObject { put("correct", hf.first); put("timeMs", hf.second) })
        }.toString())
    }

    /** 开局：把题目整包发给加入方 */
    fun broadcastStart() {
        started.set(true)
        val arr = JsonArray(questions.map {
            json.encodeToString(Question.serializer(), it).let { s -> json.parseToJsonElement(s) }
        })
        val msg = buildJsonObject { put("t", "start"); put("questions", arr) }
        sendTo(guest, msg.toString())
    }

    /** 房主作答/完成 → 转发给加入方（由 PkBattleScreen 在事件流处调用） */
    fun relayHostMessage(msg: kotlinx.serialization.json.JsonObject) {
        sendTo(guest, msg.toString())
    }

    fun stopServer() {
        if (closed.compareAndSet(false, true)) {
            runCatching {
                guest?.close(1000, "host closed")
                stop(500)
            }
        }
    }

    private fun sendTo(conn: WebSocket?, text: String) {
        conn?.let { c -> CoroutineScope(Dispatchers.IO).launch { runCatching { c.send(text) } } }
    }

    companion object {
        /** 房主作答/完成事件的统一转发入口（PkBattleScreen 调用） */
        fun relay(client: PkClient?, embedded: EmbeddedPkServer?, msg: kotlinx.serialization.json.JsonObject) {
            if (embedded != null) embedded.relayHostMessage(msg)
            else client?.let { c ->
                CoroutineScope(Dispatchers.IO).launch { runCatching { c.rawSend(msg.toString()) } }
            }
        }
    }
}
