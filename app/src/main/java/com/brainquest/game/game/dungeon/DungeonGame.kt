package com.brainquest.game.game.dungeon

import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.EnemyKind
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 地牢幸存者：游戏状态机与世界逻辑（纯 Kotlin，零 Compose 依赖）。
 *
 * 世界坐标系：房间内部为 ROOM_W×ROOM_H 的矩形，放在网格 (gx,gy) 上；
 * 相邻房间以走廊（门）相连，门在房间清怪前封闭（走廊从可通行集合剔除）。
 * 玩家用轴分离移动 + "开放矩形并集"做碰撞，敌人阶段 1 为木桩占位（摸到即清）。
 */
class DungeonGame {

    enum class Phase { READY, CLASS_SELECT, EXPLORING, LEVELUP, LOOT, SHOP, EVENT, PAUSED, GAMEOVER, VICTORY }

    // ---------- 世界常量 ----------
    companion object {
        const val ROOM_W = 1000f
        const val ROOM_H = 640f
        const val GRID_X = 1400f   // 房间横向间距（含走廊）
        const val GRID_Y = 960f    // 纵向间距
        const val DOOR_H = 150f    // 门/走廊宽度
        const val DOOR_PROBE = 44f // 走廊端头向房间内伸的长度（保证与房间收边区重叠，可平滑穿门）
        const val WALL = 26f       // 墙厚（绘制）
        const val PLAYER_R = 17f
        const val MAX_FLOOR = 5
    }

    // ---------- 房内占位怪（阶段 2 换成 CombatEngine 实体） ----------
    class Dummy(var x: Float, var y: Float, val kind: EnemyKind, val big: Boolean) {
        var alive = true
        var t = 0f   // 存在时间（动画）
    }

    // ---------- 对局状态 ----------
    var phase = Phase.READY; private set
    private var locked = false   // 锁门：在未清房间内战斗时所有走廊封闭（清完开）
    var floor = 1; private set
    var rooms: List<Room> = emptyList(); private set
    var startRoom: Room? = null; private set
    var currentRoom: Room? = null; private set
    var clearedRooms = 0; private set
    var totalKills = 0; private set
    var runTimeSec = 0; private set
    private var rng = Random(0)
    private var timeAcc = 0f

    // ---------- 玩家 ----------
    var cls: ClassDef? = null; private set
    var px = 0f; private set
    var py = 0f; private set
    var hp = 100; private set
    var maxHp = 100; private set
    var facing = 0f                 // 弧度，atan2(dir.y, dir.x)
    var walkPhase = 0f; private set
    var moving = false; private set
    var attack = 0; private set
    var attackInterval = 0.8f; private set
    var speed = 220f; private set

    // ---------- 输入（UI 线程写，循环读） ----------
    var joyActive = false
    var joyX = 0f; var joyY = 0f
    var joyBaseX = 0f; var joyBaseY = 0f

    // ---------- 相机（供渲染读） ----------
    var camX = 0f; private set
    var camY = 0f; private set
    var viewW = 1080f; var viewH = 2000f

    /** 当前房内的占位怪 */
    val dummies = ArrayList<Dummy>(8)

    fun setViewport(w: Float, h: Float) { viewW = w; viewH = h }

    // ---------- 流程 ----------
    fun toClassSelect() { if (phase == Phase.READY) phase = Phase.CLASS_SELECT }

    fun selectClass(id: String) {
        if (phase != Phase.CLASS_SELECT) return
        cls = ClassDef.byId(id)
        startRun()
    }

    private fun startRun() {
        floor = 1
        totalKills = 0
        runTimeSec = 0
        cls?.let { c ->
            maxHp = c.maxHp; hp = c.maxHp
            speed = c.speed; attack = c.attack; attackInterval = c.attackInterval
        }
        buildFloor()
        phase = Phase.EXPLORING
    }

    private fun buildFloor() {
        rng = Random(floor * 7919 + clearedRooms)
        val result = DungeonGenerator.generate(floor, rng)
        rooms = result.rooms
        startRoom = result.start
        currentRoom = result.start
        result.start.visited = true
        result.start.cleared = true
        // 玩家放到起点房中心
        px = roomLeft(result.start) + ROOM_W / 2
        py = roomTop(result.start) + ROOM_H / 2
        enterRoom(result.start)
    }

    private fun enterRoom(room: Room) {
        currentRoom = room
        if (!room.cleared && (room.type == RoomType.CHEST || room.type == RoomType.SHOP)) {
            // 非战斗房无怪可清：进门即视为可通行（宝箱/商店内容阶段 4/7 实装）
            room.cleared = true
            clearedRooms++
        }
        locked = !room.cleared
        if (!room.visited) {
            room.visited = true
            if (room.cleared) clearedRooms++
        }
        dummies.clear()
        if (!room.cleared) {
            // 占位怪：数量与房型相关（阶段 2 换真实敌人；Boss 房阶段 5 实装 Boss）
            val n = when (room.type) {
                RoomType.ELITE -> 2
                RoomType.BOSS -> 1
                RoomType.BATTLE -> 3 + rng.nextInt(3)
                else -> 0
            }
            repeat(n) { spawnDummy(room) }
        }
    }

    private fun spawnDummy(room: Room) {
        val big = room.type == RoomType.BOSS
        // 随机散布在房内，避开门口与玩家落点
        for (tryN in 0 until 20) {
            val x = roomLeft(room) + 120f + rng.nextFloat() * (ROOM_W - 240f)
            val y = roomTop(room) + 100f + rng.nextFloat() * (ROOM_H - 200f)
            if (abs(x - px) + abs(y - py) > 200f) {
                dummies.add(Dummy(x, y, EnemyKind.DUMMY, big))
                return
            }
        }
        dummies.add(Dummy(roomLeft(room) + ROOM_W / 2, roomTop(room) + 140f, EnemyKind.DUMMY, big))
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        if (phase != Phase.EXPLORING) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        timeAcc += dt
        if (timeAcc >= 1f) { runTimeSec += 1; timeAcc -= 1f }

        // 移动（轴分离碰撞）
        val jx = if (joyActive) joyX else 0f
        val jy = if (joyActive) joyY else 0f
        moving = jx != 0f || jy != 0f
        if (moving) {
            val len = kotlin.math.hypot(jx, jy)
            val nx = jx / len
            val ny = jy / len
            facing = kotlin.math.atan2(ny, nx)
            walkPhase += dt * 10f
            moveAxis(nx * speed * dt, 0f)
            moveAxis(0f, ny * speed * dt)
        }
        camX = px - viewW / 2
        camY = py - viewH / 2

        // 走进新房间：触发进房（关门/刷怪）
        val here = rooms.firstOrNull { r ->
            val l = roomLeft(r); val t = roomTop(r)
            px >= l && px <= l + ROOM_W && py >= t && py <= t + ROOM_H
        }
        if (here != null && here !== currentRoom) enterRoom(here)

        // 占位怪动画与"摸到即清"
        val iter = dummies.iterator()
        while (iter.hasNext()) {
            val d = iter.next()
            d.t += dt
            val dx = d.x - px; val dy = d.y - py
            val rr = PLAYER_R + (if (d.big) 34f else 22f)
            if (dx * dx + dy * dy <= rr * rr) {
                d.alive = false
                iter.remove()
                totalKills++
                cls?.let { c -> if (c.id == "mage") hp = minOf(hp + 1, maxHp) }   // 法师被动占位
                onEnemyCleared()
            }
        }
    }

    private fun onEnemyCleared() {
        val room = currentRoom ?: return
        if (!room.cleared && dummies.isEmpty()) {
            room.cleared = true
            locked = false
            clearedRooms++
            if (room.type == RoomType.BOSS) {
                if (floor >= MAX_FLOOR) {
                    phase = Phase.VICTORY
                } else {
                    floor++
                    buildFloor()
                }
            }
        }
    }

    private fun moveAxis(dx: Float, dy: Float) {
        val nx = px + dx
        val ny = py + dy
        if (allowed(nx, ny)) { px = nx; py = ny }
    }

    // ---------- 碰撞：开放矩形并集 ----------
    /** 房间 interior 矩形（未收边） */
    fun roomLeft(r: Room) = r.gx * GRID_X - ROOM_W / 2
    fun roomTop(r: Room) = r.gy * GRID_Y - ROOM_H / 2

    /**
     * 门（走廊）是否通行：清房锁门机制——在未清房间内战斗时全部封闭；
     * 起点房/已清房间自由通行（进入未清房即锁门，清完解锁）。
     */
    fun doorOpen(a: Room, b: Room) = !locked

    private fun allowed(x: Float, y: Float): Boolean {
        val r = PLAYER_R
        // 房间
        for (room in rooms) {
            val l = roomLeft(room); val t = roomTop(room)
            if (x >= l + r && x <= l + ROOM_W - r && y >= t + r && y <= t + ROOM_H - r) return true
        }
        // 走廊（仅开门方向）
        for (room in rooms) {
            for ((d, n) in room.neighbors) {
                if (!doorOpen(room, n)) continue
                // 只处理右/下方向，避免重复
                if (d == Dir.LEFT || d == Dir.UP) continue
                if (corridorContains(room, n, x, y, r)) return true
            }
        }
        return false
    }

    private fun corridorContains(a: Room, b: Room, x: Float, y: Float, r: Float): Boolean {
        return if (a.gy == b.gy) {   // 横向走廊
            val l = minOf(roomLeft(a) + ROOM_W, roomLeft(b) + ROOM_W) - DOOR_PROBE
            val rr = maxOf(roomLeft(a), roomLeft(b)) + DOOR_PROBE
            val cy = a.gy * GRID_Y
            x >= l + r && x <= rr - r && y >= cy - DOOR_H / 2 + r && y <= cy + DOOR_H / 2 - r
        } else {                     // 纵向走廊
            val t = minOf(roomTop(a) + ROOM_H, roomTop(b) + ROOM_H) - DOOR_PROBE
            val bb = maxOf(roomTop(a), roomTop(b)) + DOOR_PROBE
            val cx = a.gx * GRID_X
            y >= t + r && y <= bb - r && x >= cx - DOOR_H / 2 + r && x <= cx + DOOR_H / 2 - r
        }
    }

    // ---------- 渲染辅助 ----------
    /** 当前房相对起点的探索进度（HUD 显示"第 m / n 间"） */
    fun roomProgress(): Pair<Int, Int> = clearedRooms.coerceAtLeast(1) to rooms.size

    // ---------- 暂停 ----------
    fun pause() { if (phase == Phase.EXPLORING) phase = Phase.PAUSED }
    fun resume() { if (phase == Phase.PAUSED) phase = Phase.EXPLORING }

    fun reset() {
        phase = Phase.READY
        floor = 1
        rooms = emptyList()
        startRoom = null
        currentRoom = null
        clearedRooms = 0
        totalKills = 0
        runTimeSec = 0
        timeAcc = 0f
        cls = null
        hp = 100; maxHp = 100
        px = 0f; py = 0f
        facing = 0f; walkPhase = 0f; moving = false
        dummies.clear()
        joyActive = false; joyX = 0f; joyY = 0f
    }

    /** 测试辅助：模拟器验证时快速定位房间中心（仅调试用，不参与玩法） */
    fun debugTeleport(r: Room) {
        px = roomLeft(r) + ROOM_W / 2
        py = roomTop(r) + ROOM_H / 2
        enterRoom(r)
    }

    /** 玩家武器随职业的绘制参数（渲染用） */
    fun weaponAngle(): Float = facing + sin(walkPhase * 0.5f) * 0.2f
    fun cosFacing() = cos(facing)
    fun sinFacing() = sin(facing)
}
