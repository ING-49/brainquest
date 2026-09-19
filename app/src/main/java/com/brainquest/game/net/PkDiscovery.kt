package com.brainquest.game.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 局域网房间信标：房主每秒 UDP 广播房间信息（端口 8766），加入方监听发现 */
object PkDiscovery {

    private const val BEACON_PORT = 8766
    private val json = Json { ignoreUnknownKeys = true }
    private val running = AtomicBoolean(false)
    private var socketRef = AtomicReference<DatagramSocket?>(null)
    private var listenSocketRef = AtomicReference<DatagramSocket?>(null)
    private var listenerThread = AtomicReference<Thread?>(null)
    private var beaconThread = AtomicReference<Thread?>(null)

    data class Beacon(val room: String, val name: String, val ip: String, val tcpPort: Int)

    /** 房主：启动信标广播 */
    fun startBeacon(roomCode: String, hostName: String, tcpPort: Int) {
        if (!running.compareAndSet(false, true)) return
        beaconThread.set(Thread {
            try {
                val socket = DatagramSocket().apply { broadcast = true }
                socketRef.set(socket)
                val payload = buildJsonObject {
                    put("app", "brainquest")
                    put("room", roomCode)
                    put("name", hostName)
                    put("port", tcpPort)
                }.toString().toByteArray()
                while (running.get()) {
                    // 广播 + 各网卡定向广播（覆盖热点/多网卡场景）
                    val targets = mutableListOf(InetAddress.getByName("255.255.255.255"))
                    runCatching {
                        NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                            ni.inetAddresses.toList().forEach { addr ->
                                if (!addr.isLoopbackAddress && addr.address.size == 4) {
                                    val ip = addr.hostAddress ?: ""
                                    if (ip.contains('.')) {
                                        val base = ip.substringBeforeLast('.')
                                        runCatching { targets.add(InetAddress.getByName("$base.255")) }
                                    }
                                }
                            }
                        }
                    }
                    for (t in targets.distinctBy { it.hostAddress }) {
                        runCatching { socket.send(DatagramPacket(payload, payload.size, t, BEACON_PORT)) }
                    }
                    Thread.sleep(1000)
                }
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true; it.start() })
    }

    fun stopBeacon() {
        running.set(false)
        socketRef.getAndSet(null)?.close()
        beaconThread.getAndSet(null)?.interrupt()
    }

    /** 加入方：监听信标（onFound 可能重复收到，调用方按 room 去重） */
    fun startListening(onFound: (Beacon) -> Unit) {
        if (listenSocketRef.get() != null) return
        val t = Thread {
            try {
                val socket = DatagramSocket(BEACON_PORT)
                socket.broadcast = true
                socket.reuseAddress = true
                listenSocketRef.set(socket)
                val buf = ByteArray(1024)
                while (!Thread.currentThread().isInterrupted) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    runCatching {
                        val msg = json.parseToJsonElement(String(packet.data, 0, packet.length)).jsonObject
                        if (msg["app"]?.jsonPrimitive?.content == "brainquest") {
                            val room = msg["room"]?.jsonPrimitive?.content ?: return@runCatching
                            val name = msg["name"]?.jsonPrimitive?.content ?: ""
                            val ip = packet.address.hostAddress ?: return@runCatching
                            val port = msg["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 8765
                            onFound(Beacon(room, name, ip, port))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true; it.start() }
        listenerThread.set(t)
    }

    fun stopListening() {
        listenerThread.getAndSet(null)?.interrupt()
        listenSocketRef.getAndSet(null)?.close()
    }

    /** 取本机局域网 IPv4 列表（房主展示给对手用） */
    fun localIps(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && it.address.size == 4 }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
    }
}
