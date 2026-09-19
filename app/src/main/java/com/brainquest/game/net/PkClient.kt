package com.brainquest.game.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** 联机对战消息（服务器协议见 pk_server.py） */
sealed class PkEvent {
    data class Created(val code: String) : PkEvent()     // 房间已创建（自己是房主）
    data class Joined(val code: String, val peer: String) : PkEvent()
    data class PeerJoined(val peer: String, val version: String = "?") : PkEvent()
    data class Start(val questions: List<com.brainquest.game.data.question.Question>) : PkEvent()
    data object PeerReady : PkEvent()
    data class PeerAnswer(val idx: Int, val correct: Boolean) : PkEvent()
    data class PeerFinish(val correct: Int, val timeMs: Long) : PkEvent()
    data class Result(val outcome: String, val myCorrect: Int, val myTimeMs: Long, val peerCorrect: Int, val peerTimeMs: Long) : PkEvent()
    data object PeerLeft : PkEvent()
    data class Error(val msg: String) : PkEvent()
    data class Connected(val hostMode: Boolean) : PkEvent()  // WebSocket 已连上
}

/** OkHttp WebSocket 联机客户端：连接 → 创建/加入 → 消息收发 */
class PkClient(private val onEvent: (PkEvent) -> Unit) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var hostMode = false

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(url: String, name: String, mode: String, code: String = "", version: String = "") {
        val httpUrl = url.replace("ws://", "http://").replace("wss://", "https://").trimEnd('/')
        val request = Request.Builder().url("$httpUrl/?name=$name").build()
        hostMode = mode == "create"
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onEvent(PkEvent.Connected(hostMode))
                val msg = buildJsonObject {
                    put("t", mode)
                    put("name", name)
                    put("version", version)
                    if (mode == "join") put("code", code)
                }
                webSocket.send(msg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onEvent(PkEvent.Error("连接失败：${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(PkEvent.Error("连接已关闭：$reason"))
            }
        })
    }

    private fun parse(text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (obj["t"]?.jsonPrimitive?.content) {
            "created" -> onEvent(PkEvent.Created(obj["code"]!!.jsonPrimitive.content))
            "joined" -> onEvent(PkEvent.Joined(obj["code"]!!.jsonPrimitive.content, obj["peer"]!!.jsonPrimitive.content))
            "peer_joined" -> onEvent(PkEvent.PeerJoined(obj["peer"]!!.jsonPrimitive.content, obj["version"]?.jsonPrimitive?.content ?: "?"))
            "start" -> {
                val qs = json.decodeFromString<List<com.brainquest.game.data.question.Question>>(
                    obj["questions"]!!.jsonArray.toString(),
                )
                onEvent(PkEvent.Start(qs))
            }
            "peer_ready", "ready" -> onEvent(PkEvent.PeerReady)
            "peer_answer" -> onEvent(PkEvent.PeerAnswer(obj["idx"]!!.jsonPrimitive.content.toInt(), obj["correct"]!!.jsonPrimitive.content.toBoolean()))
            "peer_finish" -> onEvent(PkEvent.PeerFinish(obj["correct"]!!.jsonPrimitive.content.toInt(), obj["timeMs"]!!.jsonPrimitive.content.toLong()))
            "result" -> onEvent(PkEvent.Result(
                outcome = obj["outcome"]!!.jsonPrimitive.content,
                myCorrect = obj["my"]!!.jsonObject["correct"]!!.jsonPrimitive.content.toInt(),
                myTimeMs = obj["my"]!!.jsonObject["timeMs"]!!.jsonPrimitive.content.toLong(),
                peerCorrect = obj["peer"]!!.jsonObject["correct"]!!.jsonPrimitive.content.toInt(),
                peerTimeMs = obj["peer"]!!.jsonObject["timeMs"]!!.jsonPrimitive.content.toLong(),
            ))
            "peer_left" -> onEvent(PkEvent.PeerLeft)
            "error" -> onEvent(PkEvent.Error(obj["msg"]!!.jsonPrimitive.content))
        }
    }

    fun sendStart(questions: List<com.brainquest.game.data.question.Question>) {
        val arr = JsonArray(questions.map {
            json.encodeToString(com.brainquest.game.data.question.Question.serializer(), it)
                .let { s -> json.parseToJsonElement(s) }
        })
        val msg = buildJsonObject {
            put("t", "start")
            put("questions", arr)
        }
        send(msg.toString())
    }

    fun sendAnswer(idx: Int, correct: Boolean, timeMs: Long) {
        send(buildJsonObject {
            put("t", "answer"); put("idx", idx); put("correct", correct); put("timeMs", timeMs)
        }.toString())
    }

    fun sendFinish(correct: Int, timeMs: Long) {
        send(buildJsonObject {
            put("t", "finish"); put("correct", correct); put("timeMs", timeMs)
        }.toString())
    }

    private fun send(text: String) {
        scope.launch { ws?.send(text) }
    }

    /** 发送任意已构建的 JSON（内嵌服务器模式下房主转发用） */
    fun rawSend(text: String) {
        send(text)
    }

    fun close() {
        ws?.close(1000, "bye")
        ws = null
    }
}
