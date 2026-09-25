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
    data class Result(
        val outcome: String,
        val myCorrect: Int,
        val myTimeMs: Long,
        val peerCorrect: Int,
        val peerTimeMs: Long,
        val ranked: Boolean = false,   // 是否计分局（仅快速匹配计分）
        val myRating: Int = 0,         // 我的积分（计分局）
        val ratingDelta: Int = 0,      // 本局积分变化
        val peerRating: Int = 0,
    ) : PkEvent()
    /** 排行榜条目 */
    data class RankRow(val name: String, val rating: Int, val wins: Int, val losses: Int, val games: Int = 0, val rank: Int = 0)
    data class Leaderboard(val top: List<RankRow>, val me: RankRow?, val subject: String = "") : PkEvent()
    data class SaveOk(val size: Int) : PkEvent()          // 云存档上传成功
    data class SaveData(val data: String) : PkEvent()     // 云存档下载数据
    data object SaveDeleted : PkEvent()                   // 云存档已删除
    data object PeerLeft : PkEvent()
    data class Error(val msg: String) : PkEvent()
    data class Connected(val hostMode: Boolean) : PkEvent()  // WebSocket 已连上
    data class Online(val players: Int, val waiting: Int, val rooms: Int) : PkEvent()  // 在线统计广播
    data object Disconnected : PkEvent()                 // 连接断开（失败或关闭）
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

    /** 地址是否形如 ws://主机[:端口]（防输入中途连接崩溃） */
    private fun validPkUrl(url: String): Boolean {
        val t = url.trim()
        if (!t.startsWith("ws://") && !t.startsWith("wss://")) return false
        val host = t.substringAfter("://").substringBefore("/").substringBefore(":")
        return host.isNotBlank()
    }

    /** @return 是否成功发起连接（地址无效时返回 false 并发 Error 事件） */
    fun connect(url: String, name: String, mode: String, code: String = "", version: String = "", subject: String = ""): Boolean {
        if (!validPkUrl(url)) {
            onEvent(PkEvent.Error("服务器地址需形如 ws://主机:端口"))
            onEvent(PkEvent.Disconnected)
            return false
        }
        ws?.close(1000, "reconnect")  // 保证任意时刻只有一条连接
        return try {
            val httpUrl = url.trim().replace("ws://", "http://").replace("wss://", "https://").trimEnd('/')
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
                        if (subject.isNotBlank()) put("subject", subject)   // 好友房/快速匹配的出题科目
                    }
                    webSocket.send(msg.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parse(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    onEvent(PkEvent.Error("连接失败：${t.message}"))
                    onEvent(PkEvent.Disconnected)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onEvent(PkEvent.Error("连接已关闭：$reason"))
                    onEvent(PkEvent.Disconnected)
                }
            })
            true
        } catch (e: Exception) {
            onEvent(PkEvent.Error("地址无效或无法连接：${e.message}"))
            onEvent(PkEvent.Disconnected)
            false
        }
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
            "result" -> {
                val rating = obj["rating"]?.let { runCatching { it.jsonObject }.getOrNull() }
                onEvent(PkEvent.Result(
                    outcome = obj["outcome"]!!.jsonPrimitive.content,
                    myCorrect = obj["my"]!!.jsonObject["correct"]!!.jsonPrimitive.content.toInt(),
                    myTimeMs = obj["my"]!!.jsonObject["timeMs"]!!.jsonPrimitive.content.toLong(),
                    peerCorrect = obj["peer"]!!.jsonObject["correct"]!!.jsonPrimitive.content.toInt(),
                    peerTimeMs = obj["peer"]!!.jsonObject["timeMs"]!!.jsonPrimitive.content.toLong(),
                    ranked = rating?.get("ranked")?.jsonPrimitive?.content?.toBoolean() ?: false,
                    myRating = rating?.get("my")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    ratingDelta = rating?.get("delta")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    peerRating = rating?.get("peer")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                ))
            }
            "leaderboard" -> {
                fun row(o: JsonObject, fallbackRank: Int) = PkEvent.RankRow(
                    name = o["name"]?.jsonPrimitive?.content ?: "?",
                    rating = o["rating"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000,
                    wins = o["wins"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    losses = o["losses"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    games = o["games"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    rank = o["rank"]?.jsonPrimitive?.content?.toIntOrNull() ?: fallbackRank,
                )
                val top = obj["top"]?.jsonArray?.mapIndexed { i, el -> row(el.jsonObject, i + 1) } ?: emptyList()
                val me = obj["me"]?.let { runCatching { row(it.jsonObject, 0) }.getOrNull() }
                onEvent(PkEvent.Leaderboard(top, me, obj["subject"]?.jsonPrimitive?.content ?: ""))
            }
            "save_ok" -> onEvent(PkEvent.SaveOk(obj["size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0))
            "save_data" -> onEvent(PkEvent.SaveData(obj["data"]?.jsonPrimitive?.content ?: ""))
            "save_del_ok" -> onEvent(PkEvent.SaveDeleted)
            "peer_left" -> onEvent(PkEvent.PeerLeft)
            "online" -> onEvent(PkEvent.Online(
                players = obj["players"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                waiting = obj["waiting"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                rooms = obj["rooms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ))
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

    fun sendAnswer(idx: Int, choice: Int, timeMs: Long) {
        // v1.6.9 服务器中立对战：上报所选项，由服务器按题库判分（correct 字段不再自报）
        send(buildJsonObject {
            put("t", "answer"); put("idx", idx); put("choice", choice); put("timeMs", timeMs)
        }.toString())
    }

    fun sendFinish(timeMs: Long) {
        send(buildJsonObject {
            put("t", "finish"); put("timeMs", timeMs)
        }.toString())
    }

    /** 取消快速匹配 */
    fun sendCancelMatch() {
        send(buildJsonObject { put("t", "cancel_match") }.toString())
    }

    /** 发起快速匹配（连接已建立时用；未连接时先 connect(mode="idle") 再调用） */
    fun sendQuickMatch(name: String, version: String, subject: String = "混合") {
        send(buildJsonObject {
            put("t", "quick_match"); put("name", name); put("version", version); put("subject", subject)
        }.toString())
    }

    /** 查询在线人数（服务器同时也会主动广播） */
    fun sendOnlineQuery() {
        send(buildJsonObject { put("t", "online") }.toString())
    }

    /** 查询某科目排行榜（含我自己的排名），默认混合 */
    fun sendLeaderboard(name: String, subject: String = "混合") {
        send(buildJsonObject {
            put("t", "leaderboard"); put("name", name); put("subject", subject)
        }.toString())
    }

    /** 云存档：上传（owner = 本机身份码，服务器做归属绑定） */
    fun sendCloudPut(code: String, owner: String, data: String) {
        send(buildJsonObject {
            put("t", "save_put"); put("code", code); put("owner", owner); put("data", data)
        }.toString())
    }

    /** 云存档：下载（owner 校验归属；换设备恢复时传原设备身份码 + new_owner = 本机身份码完成转移） */
    fun sendCloudGet(code: String, owner: String, newOwner: String = "") {
        send(buildJsonObject {
            put("t", "save_get"); put("code", code); put("owner", owner)
            if (newOwner.isNotBlank()) put("new_owner", newOwner)
        }.toString())
    }

    /** 云存档：删除（用户数据删除通道，需归属身份） */
    fun sendCloudDel(code: String, owner: String) {
        send(buildJsonObject {
            put("t", "save_del"); put("code", code); put("owner", owner)
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
