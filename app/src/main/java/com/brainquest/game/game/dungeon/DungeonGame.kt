package com.brainquest.game.game.dungeon

import com.brainquest.game.game.core.CombatEngine
import com.brainquest.game.game.core.Weapon
import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.EnemyKind
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.random.Random

/**
 * 地牢幸存者：状态机与地牢结构（纯 Kotlin，零 Compose 依赖）。
 * 战斗（移动/武器/碰撞/元素/升级）托管给 [CombatEngine]；本类负责：
 * 层数/房间/门（清房锁门）/按房型布置敌人/进层推进/局内统计。
 *
 * 世界坐标系：房间内部 ROOM_W×ROOM_H 放在网格 (gx,gy) 上，相邻房间走廊（门）相连。
 */
class DungeonGame {

    enum class Phase { READY, CLASS_SELECT, EXPLORING, LEVELUP, LOOT, SHOP, EVENT, PAUSED, GAMEOVER, VICTORY }

    companion object {
        const val ROOM_W = 1000f
        const val ROOM_H = 640f
        const val GRID_X = 1400f   // 房间横向间距（含走廊）
        const val GRID_Y = 960f    // 纵向间距
        const val DOOR_H = 150f    // 门/走廊宽度
        const val DOOR_PROBE = 44f // 走廊端头向房间内伸的长度（保证与房间收边区无缝穿门）
        const val WALL = 26f       // 墙厚（绘制）
        const val MAX_FLOOR = 5
    }

    val engine = CombatEngine()

    // ---------- 对局状态 ----------
    var phase = Phase.READY; private set
    var floor = 1; private set
    var rooms: List<Room> = emptyList(); private set
    var startRoom: Room? = null; private set
    var currentRoom: Room? = null; private set
    var clearedRooms = 0; private set
    var floorCleared = 0; private set   // 本层已清房数（HUD 显示用）
    var totalKills = 0; private set
    var runTimeSec = 0; private set
    private var timeAcc = 0f
    private var rng = Random(0)
    private var locked = false   // 锁门：在未清房间战斗时所有走廊封闭

    /**
     * DEBUG 自动驾驶（仅自动化验收；DebugFlags.autopilot 时由 Screen 打开）：
     * 有全图真值，直接导航到最近未清房、顺路拾经验球、战斗时站桩、升级自动选第一项。
     */
    var autopilot = false
    private var apWait = 0f
    private var apNext: Room? = null   // 导航承诺：下一间要进的房
    private var apPhase = 0            // 0=去门口 1=穿门 2=拾球

    // ---------- 玩家职业（权威属性在 engine） ----------
    var cls: ClassDef? = null; private set

    // ---------- 视口 ----------
    var viewW = 1080f; var viewH = 2000f
    val camX: Float get() = engine.px - viewW / 2
    val camY: Float get() = engine.py - viewH / 2

    // ---------- 流程 ----------
    fun toClassSelect() { if (phase == Phase.READY) phase = Phase.CLASS_SELECT }

    fun selectClass(id: String) {
        if (phase != Phase.CLASS_SELECT) return
        cls = ClassDef.byId(id)
        startRun()
    }

    private fun startRun() {
        val c = cls ?: return
        floor = 1
        totalKills = 0
        runTimeSec = 0
        timeAcc = 0f
        engine.reset()
        engine.setStats(c.maxHp, c.attack, c.attackInterval, c.speed)
        engine.passiveId = c.id
        engine.weapon = when (c.id) {
            "knight" -> Weapon.MeleeSlash()
            "mage" -> Weapon.Fireball()
            else -> Weapon.RapidShot()
        }
        engine.canPass = canPass
        engine.onEnemyKilled = { _ ->
            totalKills++
            if (c.id == "mage") engine.heal(1)   // 法师被动：击杀回 1 血
        }
        buildFloor()
        engine.begin()
        phase = Phase.EXPLORING
    }

    private fun buildFloor() {
        rng = Random(floor * 7919 + clearedRooms + totalKills)
        apNext = null; apPhase = 0   // 换层：上一层楼的导航承诺全部作废
        val result = DungeonGenerator.generate(floor, rng)
        rooms = result.rooms
        floorCleared = 0
        startRoom = result.start
        engine.px = roomLeft(result.start) + ROOM_W / 2
        engine.py = roomTop(result.start) + ROOM_H / 2
        enterRoom(result.start)
    }

    private fun enterRoom(room: Room) {
        currentRoom = room
        if (!room.cleared && (room.type == RoomType.CHEST || room.type == RoomType.SHOP)) {
            // 非战斗房无怪可清：进门即视为可通行（宝箱/商店内容阶段 4/7 实装）
            room.cleared = true
            clearedRooms++
            floorCleared++
        }
        locked = !room.cleared
        if (locked) {
            // 玩家可能正跨在门槛上（房间判定矩形与收边区之间）：推入房内可站立区
            engine.px = engine.px.coerceIn(roomLeft(room) + engine.playerR + 2f, roomLeft(room) + ROOM_W - engine.playerR - 2f)
            engine.py = engine.py.coerceIn(roomTop(room) + engine.playerR + 2f, roomTop(room) + ROOM_H - engine.playerR - 2f)
        }
        if (!room.visited) {
            room.visited = true
            if (room.cleared) { clearedRooms++; floorCleared++ }
        }
        // 场地 = 本房矩形（敌人不出房）；清空上一房实体（经验球保留在世界上）
        engine.enemies.clear()
        engine.bullets.clear()
        engine.arenaLeft = roomLeft(room) + 30f
        engine.arenaTop = roomTop(room) + 30f
        engine.arenaRight = roomLeft(room) + ROOM_W - 30f
        engine.arenaBottom = roomTop(room) + ROOM_H - 30f
        if (!room.cleared) populateRoom(room)
    }

    /** 按房型布置敌人（数量/强度随层数成长） */
    private fun populateRoom(room: Room) {
        val scaleHp = 1f + 0.35f * (floor - 1)
        val scaleDmg = 1f + 0.2f * (floor - 1)

        fun spawn(kind: EnemyKind, elite: Boolean, x: Float, y: Float, big: Boolean = false) {
            val baseHp = (if (big) 260f else 24f) * scaleHp * (if (elite) 2.2f else 1f)
            val speed = (when (kind) {
                EnemyKind.BAT -> 95f
                EnemyKind.SLIME -> 55f
                EnemyKind.CASTER -> 60f
                else -> 68f
            }) + floor * 4f
            val r = when {
                big -> 42f
                elite -> 24f
                kind == EnemyKind.SLIME -> 15f
                else -> 16f
            }
            engine.spawnLater(engine.enemies.size * 0.12f, CombatEngine.Enemy(
                x, y, r, baseHp, baseHp, speed,
                (if (big) 12f else 7f) * scaleDmg * (if (elite) 1.4f else 1f),
                kind, elite, xpValue = if (elite) 3 else 1,
            ))
        }

        fun spot(minDist: Float): Pair<Float, Float> {
            for (t in 0 until 20) {
                val x = roomLeft(room) + 120f + rng.nextFloat() * (ROOM_W - 240f)
                val y = roomTop(room) + 100f + rng.nextFloat() * (ROOM_H - 200f)
                if (kotlin.math.abs(x - engine.px) + kotlin.math.abs(y - engine.py) > minDist) return x to y
            }
            return roomLeft(room) + ROOM_W / 2 to roomTop(room) + 140f
        }

        when (room.type) {
            RoomType.ELITE -> {
                repeat(2) { val (x, y) = spot(220f); spawn(EnemyKind.SKELETON, elite = true, x, y) }
                repeat(2) {
                    val kind = if (rng.nextBoolean()) EnemyKind.SLIME else EnemyKind.BAT
                    val (x, y) = spot(220f); spawn(kind, elite = false, x, y)
                }
            }
            RoomType.BOSS -> {
                // 阶段 5 实装多阶段 Boss；当前为大体型占位怪
                spawn(EnemyKind.DUMMY, elite = false, roomLeft(room) + ROOM_W / 2, roomTop(room) + ROOM_H / 2 - 40f, big = true)
            }
            RoomType.BATTLE -> {
                val n = 3 + rng.nextInt(2) + (floor - 1)   // 首层 3-4 只，逐层+1
                repeat(n) {
                    val kind = when {
                        floor >= 2 && rng.nextInt(5) == 0 -> EnemyKind.CASTER
                        rng.nextInt(3) == 0 -> EnemyKind.SKELETON
                        rng.nextInt(3) == 0 -> EnemyKind.BAT
                        else -> EnemyKind.SLIME
                    }
                    val (x, y) = spot(220f)
                    spawn(kind, elite = false, x, y)
                }
            }
            else -> {}
        }
    }

    // ---------- 主循环 ----------
    fun tick(dtRaw: Float) {
        if (phase != Phase.EXPLORING && phase != Phase.LEVELUP && phase != Phase.GAMEOVER) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        if (phase == Phase.EXPLORING) {
            timeAcc += dt
            if (timeAcc >= 1f) { runTimeSec += 1; timeAcc -= 1f }
        }
        if (autopilot) {
            if (phase == Phase.LEVELUP) {
                apWait += dt
                if (apWait > 0.6f) { engine.pendingUpgrades.firstOrNull()?.let { chooseUpgrade(it.id) }; apWait = 0f }
            } else {
                apWait = 0f
                autopilotSteer()
            }
        }
        engine.tick(dt)

        // 引擎状态 → 地牢状态
        if (engine.phase == CombatEngine.Phase.LEVELUP && phase == Phase.EXPLORING) {
            phase = Phase.LEVELUP
        } else if (engine.phase == CombatEngine.Phase.GAMEOVER && phase == Phase.EXPLORING) {
            phase = Phase.GAMEOVER
        }

        // 走进新房间
        if (phase == Phase.EXPLORING) {
            val here = rooms.firstOrNull { r ->
                val l = roomLeft(r); val t = roomTop(r)
                engine.px >= l && engine.px <= l + ROOM_W && engine.py >= t && engine.py <= t + ROOM_H
            }
            if (here != null && here !== currentRoom) enterRoom(here)
            // 清房判定（延迟刷怪全落地且清空才开门）
            val room = currentRoom
            if (room != null && locked && engine.enemies.isEmpty() && !engine.hasPendingSpawns()) {
                room.cleared = true
                locked = false
                clearedRooms++
                floorCleared++
                if (room.type == RoomType.BOSS) {
                    if (floor >= MAX_FLOOR) phase = Phase.VICTORY
                    else { floor++; buildFloor() }
                }
            }
        }
    }

    /** DEBUG：自动驾驶的摇杆决策——带承诺的导航状态机（阈值切换会自激振荡，必须记状态） */
    private fun autopilotSteer() {
        val room = currentRoom ?: return
        if (locked) {
            // 战斗走位：近战贴脸保证命中与朝向；远程保持 200~420 距离风筝
            val enemy = engine.enemies.filter { it.alive }.minByOrNull {
                (it.x - engine.px) * (it.x - engine.px) + (it.y - engine.py) * (it.y - engine.py)
            }
            if (enemy == null) { engine.joyActive = false; return }
            val dx = enemy.x - engine.px; val dy = enemy.y - engine.py
            val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val melee = engine.weapon is Weapon.MeleeSlash
            val pull = when {
                melee -> d - 55f                       // 近战：贴到攻击距离
                d < 200f -> (d - 200f) * 1.5f          // 远程太近：逃离（负 = 远离敌人）
                d > 420f -> (d - 420f) * 1.5f          // 太远：靠近
                else -> 0f
            }
            if (kotlin.math.abs(pull) < 20f) { engine.joyActive = false; return }
            var gx = engine.px + dx / d * pull
            var gy = engine.py + dy / d * pull
            // 靠墙时向房心规避，防止被逼进墙角
            val cx = (engine.arenaLeft + engine.arenaRight) / 2
            val cy = (engine.arenaTop + engine.arenaBottom) / 2
            val edge = 130f
            if (engine.px - engine.arenaLeft < edge) gx += edge - (engine.px - engine.arenaLeft)
            if (engine.arenaRight - engine.px < edge) gx -= edge - (engine.arenaRight - engine.px)
            if (engine.py - engine.arenaTop < edge) gy += edge - (engine.py - engine.arenaTop)
            if (engine.arenaBottom - engine.py < edge) gy -= edge - (engine.arenaBottom - engine.py)
            apSteerTo(gx, gy)
            return
        }

        // 0) 战斗结束的房间：先扫一圈经验球（升级必须捡球）
        if (apPhase == 2) {
            val orb = engine.orbs.filter { it.alive }.minByOrNull {
                (it.x - engine.px) * (it.x - engine.px) + (it.y - engine.py) * (it.y - engine.py)
            }
            if (orb != null) {
                apSteerTo(orb.x, orb.y)
                return
            }
            apPhase = 0   // 没球了，继续导航
        }

        // 1) 无航程 → 选最近的未清房并规划首段
        if (apNext == null) {
            val target = nearestUncleared(room) ?: run {
                engine.joyActive = false
                return
            }
            val path = apBfsPath(room, target)
            apNext = if (path.isEmpty()) target else path.first()
            apPhase = 0
        }
        val next = apNext ?: return

        // 2) 已进入 next 房：承诺完成，重规划
        if (room === next) {
            if (room.cleared) { apPhase = 2 }   // 清完：先拾球
            apNext = null
            return
        }

        // 3) 阶段执行（承诺制：进入下一阶段只看位置越过与否，不看与目标的距离）
        when (apPhase) {
            0 -> {
                val door = doorCenter(room, next)
                apSteerTo(door.first, door.second)
                // 越过门所在墙：房间序号变化由 enterRoom 完成；若仍在本房但已越过门心，切阶段
                val crossed = if (room.gy == next.gy) {
                    (next.gx > room.gx && engine.px >= door.first) || (next.gx < room.gx && engine.px <= door.first)
                } else {
                    (next.gy > room.gy && engine.py >= door.second) || (next.gy < room.gy && engine.py <= door.second)
                }
                if (crossed) apPhase = 1
            }
            else -> {
                val rc = roomCenter(next)
                apSteerTo(rc.first, rc.second)
            }
        }
    }


    private fun apSteerTo(x: Float, y: Float) {
        val dx = x - engine.px; val dy = y - engine.py
        val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        engine.joyActive = true
        engine.joyX = (dx / d).coerceIn(-1f, 1f)
        engine.joyY = (dy / d).coerceIn(-1f, 1f)
    }

    private fun nearestUncleared(from: Room): Room? {
        var best: Room? = null
        var bestD = Float.MAX_VALUE
        for (r in rooms) {
            if (r.cleared || r.type == RoomType.START) continue
            val d = kotlin.math.abs(r.gx - from.gx) + kotlin.math.abs(r.gy - from.gy)
            val df = d.toFloat(); if (df < bestD) { bestD = df; best = r }
        }
        return best
    }

    private fun apBfsPath(from: Room, to: Room): List<Room> {
        if (from === to) return emptyList()
        val prev = HashMap<Room, Room>()
        val queue = ArrayDeque<Room>()
        queue.add(from)
        val seen = HashSet<Room>(listOf(from))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur === to) break
            for (n in cur.neighbors.values) {
                if (seen.add(n)) { prev[n] = cur; queue.add(n) }
            }
        }
        if (to !in seen) return emptyList()
        val path = ArrayList<Room>()
        var cur = to
        while (cur !== from) { path.add(cur); cur = prev[cur]!! }
        return path.reversed()
    }

    private fun doorCenter(a: Room, b: Room): Pair<Float, Float> =
        Pair((a.gx + b.gx) / 2f * GRID_X, (a.gy + b.gy) / 2f * GRID_Y)

    private fun roomCenter(r: Room): Pair<Float, Float> =
        Pair(r.gx * GRID_X, r.gy * GRID_Y)

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        engine.chooseUpgrade(id)
        if (engine.phase == CombatEngine.Phase.PLAYING) phase = Phase.EXPLORING
    }

    // ---------- 世界几何 ----------
    fun roomLeft(r: Room) = r.gx * GRID_X - ROOM_W / 2
    fun roomTop(r: Room) = r.gy * GRID_Y - ROOM_H / 2

    /** 门是否通行：清房锁门机制（在未清房间战斗时全部封闭） */
    fun doorOpen(a: Room, b: Room) = !locked

    /** 墙体碰撞回调（引擎移动玩家时查询）：开放矩形并集（房间 + 开门走廊） */
    val canPass: (Float, Float) -> Boolean = handler@{ x, y ->
        val r = engine.playerR
        for (room in rooms) {
            val l = roomLeft(room); val t = roomTop(room)
            if (x >= l + r && x <= l + ROOM_W - r && y >= t + r && y <= t + ROOM_H - r) return@handler true
        }
        for (room in rooms) {
            for ((d, n) in room.neighbors) {
                if (d == Dir.LEFT || d == Dir.UP) continue
                if (!doorOpen(room, n)) continue
                if (corridorContains(room, n, x, y, r)) return@handler true
            }
        }
        false
    }

    private fun corridorContains(a: Room, b: Room, x: Float, y: Float, r: Float): Boolean {
        return if (a.gy == b.gy) {
            val l = minOf(roomLeft(a) + ROOM_W, roomLeft(b) + ROOM_W) - DOOR_PROBE
            val rr = maxOf(roomLeft(a), roomLeft(b)) + DOOR_PROBE
            val cy = a.gy * GRID_Y
            x >= l + r && x <= rr - r && y >= cy - DOOR_H / 2 + r && y <= cy + DOOR_H / 2 - r
        } else {
            // 纵向走廊：上房的底边 → 下房的顶边（端头各内伸 PROBE 与房间收边区重叠）
            val t = minOf(roomTop(a) + ROOM_H, roomTop(b) + ROOM_H) - DOOR_PROBE
            val bb = maxOf(roomTop(a), roomTop(b)) + DOOR_PROBE
            val cx = a.gx * GRID_X
            y >= t + r && y <= bb - r && x >= cx - DOOR_H / 2 + r && x <= cx + DOOR_H / 2 - r
        }
    }

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
        floorCleared = 0
        totalKills = 0
        runTimeSec = 0
        timeAcc = 0f
        cls = null
        locked = false
        apNext = null
        apPhase = 0
        engine.reset()
    }
}
