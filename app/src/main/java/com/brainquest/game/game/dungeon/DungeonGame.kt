package com.brainquest.game.game.dungeon

import com.brainquest.game.game.core.CombatEngine
import com.brainquest.game.game.core.Weapon
import com.brainquest.game.game.dungeon.model.ClassDef
import com.brainquest.game.game.dungeon.Equipment
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.EnemyKind
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.math.pow
import kotlin.random.Random

/**
 * 地牢幸存者：状态机与地牢结构（纯 Kotlin，零 Compose 依赖）。
 * 战斗（移动/武器/碰撞/元素/升级）托管给 [CombatEngine]；本类负责：
 * 层数/房间/门（清房锁门）/按房型布置敌人/进层推进/局内统计。
 *
 * 世界坐标系：房间放在网格 (gx,gy) 上，相邻房间走廊（门）相连；房型不同大小不同（Room.w/h，ROOM_W/H 为战斗房基准）。
 */
class DungeonGame {

    enum class Phase { READY, CLASS_SELECT, EXPLORING, LEVELUP, LOOT, SHOP, EVENT, PAUSED, GAMEOVER, VICTORY, TRANSITION }

    companion object {
        const val ROOM_W = 2000f   // 标准战斗房宽（基准值，实际用 Room.w）
        const val ROOM_H = 1300f   // 标准战斗房高
        const val GRID_X = 2400f   // 房间横向间距（含走廊）
        const val GRID_Y = 1700f   // 纵向间距
        const val BASE_ZOOM = 1.3f // 世界基础缩放：屏幕只看世界的一部分（房间远大于视口，相机跟随人物居中）
        const val DOOR_H = 200f    // 门/走廊宽度（加宽后穿门更顺滑）
        const val DOOR_PROBE = 44f // 走廊端头向房间内伸的长度（保证与房间收边区无缝穿门）
        const val WALL = 26f       // 墙厚（绘制）
        const val MAX_FLOOR = 5
    }

    val engine = CombatEngine()

    // ---------- 装备 ----------
    val slots = HashMap<Equipment.Slot, Equipment.Item>()   // 六槽
    val drops = ArrayList<Drop>(8)                          // 地上的掉落物
    private var appliedBonus = Equipment.Bonus()            // 已应用到引擎的增量（换装时做差）

    /** 地上的装备掉落物 */
    class Drop(var x: Float, var y: Float, val item: Equipment.Item) {
        var alive = true
        var t = 0f
    }

    /** 商店货架上的一件商品（gear 预生成货件在 item） */
    class ShopGood(val id: String, val name: String, val desc: String, val cost: Int) {
        var sold = false
        var item: Equipment.Item? = null
    }

    // ---------- 对局状态 ----------
    var phase = Phase.READY; private set
    var floor = 1; private set
    var rooms: List<Room> = emptyList(); private set
    var startRoom: Room? = null; private set
    var currentRoom: Room? = null; private set
    var clearedRooms = 0; private set
    var floorCleared = 0; private set   // 本层已清房数（HUD 显示用）
    var totalKills = 0; private set
    var coins = 0; private set
    var shopGoods: List<ShopGood> = emptyList(); private set
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
    private var apStuck = 0f           // 看门狗计时
    private var lastLogSec = -1
    private var apOrbT = 0f
    private var navStuckT = 0f
    private var navLastX = 0f; private var navLastY = 0f
    private var navStuckRoom: Room? = null
    private var navStuckN = 0
    private val apSkipped = HashSet<Room>()   // 本层导航反复失败的房间（跳过）

    // ---------- 玩家职业（权威属性在 engine） ----------
    var cls: ClassDef? = null; private set
    /** DEBUG/局外成长：永久升级（选职业前由 Screen 注入，startRun 应用） */
    var pendingPerks: Map<String, Int> = emptyMap()

    // ---------- 视口（平滑相机：前瞻偏移 + Boss 拉远 + 房间边界） ----------
    var viewW = 2000f; var viewH = 1080f   // 横屏视口（onSizeChanged 会覆盖）
    var camX = 0f; private set
    var camY = 0f; private set
    var camZoom = 1f; private set          // >1 = 拉远（Boss 战看更多）
    private var camInit = false
    var hpGhost = 100f                     // 血条残影值（渲染用）
    // 传送门（Boss 清完后出现；走近交互进入下一层）与过场
    var portal: Pair<Float, Float>? = null; private set
    var portalNear = false; private set    // 靠近可交互（攻击键变「进入」）
    var transition = 0f; private set       // >0 = 过场进行中（秒）
    // 宝箱房 / 商店房实体（进房绑定当前房的房心实体，离房置空）
    var chest: Pair<Float, Float>? = null; private set
    var chestNear = false; private set
    var shop: Pair<Float, Float>? = null; private set
    var shopNear = false; private set
    private val chestSpots = HashMap<Room, Pair<Float, Float>>()
    private val openedChests = HashSet<Room>()
    private val shopSpots = HashMap<Room, Pair<Float, Float>>()
    private val apShopDone = HashSet<Room>()   // autopilot 已开过店的房（防开→关→开死循环）
    // 模式：无尽（Boss 后不结算，传送门一直往下走，难度指数上升）；大厅层选择，跨局保留
    var endless = false

    private fun tickCamera(dt: Float) {
        val bossHere = engine.enemies.any { it.bossFloor > 0 && it.alive }
        val targetZoom = if (bossHere) 1.15f else 1f
        camZoom += (targetZoom - camZoom) * (dt * 4f).coerceIn(0f, 1f)
        val eff = camZoom * BASE_ZOOM
        // 虚拟画布中心对准人物（渲染层再围绕屏幕中心做 eff 倍缩放——人物必然落在屏幕正中，
        // 不可再除以 eff，否则两种缩放叠加会把画面推向左上）
        val tx = engine.px - viewW / 2f
        val ty = engine.py - viewH / 2f
        if (!camInit) { camX = tx; camY = ty; camInit = true }
        else {
            val k = (dt * 8f).coerceIn(0f, 1f)
            camX += (tx - camX) * k; camY += (ty - camY) * k
        }
        // 相机一律跟随人物居中（房间外露出的深色底是正常背景）——人物永远在屏幕正中
    }

    // ---------- 流程 ----------
    // ---------- 大厅 ----------
    var lobbyClassId = "knight"   // 大厅当前预览职业

    fun cycleClass(dir: Int) {
        val ids = ClassDef.ALL.map { it.id }
        val i = ids.indexOf(lobbyClassId).coerceAtLeast(0)
        lobbyClassId = ids[(i + dir + ids.size) % ids.size]
    }

    fun startFromLobby() {
        if (phase != Phase.READY) return
        cls = ClassDef.byId(lobbyClassId)
        startRun()
    }

    fun toClassSelect() { if (phase == Phase.READY) phase = Phase.CLASS_SELECT }

    /** 退出本局回到游戏大厅（不清 App 返回栈） */
    fun exitToLobby() {
        reset()
        phase = Phase.READY
    }

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
        hpGhost = engine.maxHp.toFloat()
        camInit = false
        // 永久升级（局外成长）：生命/攻击/移速
        val perks = pendingPerks
        engine.setMaxHp(engine.maxHp + 8 * (perks["hp"] ?: 0))
        engine.buffAttack(1 * (perks["atk"] ?: 0))
        engine.buffSpeed(4f * (perks["spd"] ?: 0))
        engine.passiveId = c.id
        engine.weapon = when (c.id) {
            "knight" -> Weapon.MeleeSlash()
            "mage" -> Weapon.Fireball()
            else -> Weapon.RapidShot()
        }
        // 职业专属主动技能：开局自带、固定不变，冷却各职业不同
        engine.setSkill(c.skillId)
        engine.skillCdMax = when (c.skillId) {
            "whirlwind" -> 10f
            "blizzard" -> 13f
            else -> 9f
        }
        engine.canPass = canPass
        engine.onEnemyKilled = { e ->
            totalKills++
            if (c.id == "mage") engine.heal(1)   // 法师被动：击杀回 1 血
            // 金币掉落：精英 +8 / Boss +25（商店与重铸的收入来源）
            val coinDrop = if (e.bossFloor > 0) 25 else if (e.elite) 8 else 0
            if (coinDrop > 0) {
                coins += coinDrop
                engine.events.add(CombatEngine.FxEvent(e.x, e.y - e.r - 14f, "金币 +$coinDrop", false, null, 2))
            }
            // 掉落：普通 10%、精英必掉史诗、Boss 必掉传说
            val item = when {
                e.r > 30f -> Equipment.generateBoss(floor, rng)
                e.elite -> Equipment.generateElite(floor, rng)
                rng.nextFloat() < 0.10f -> Equipment.generate(floor, rng)
                else -> null
            }
            if (item != null) drops.add(Drop(e.x, e.y, item))
        }
        buildFloor()
        engine.begin()
        phase = Phase.EXPLORING
    }

    private fun buildFloor() {
        rng = Random(floor * 7919 + clearedRooms + totalKills)
        apNext = null; apPhase = 0   // 换层：上一层楼的导航承诺全部作废
        apSkipped.clear(); navStuckT = 0f; navStuckN = 0; navStuckRoom = null
        portal = null; portalNear = false; transition = 0f
        chest = null; chestNear = false
        shop = null; shopNear = false; shopGoods = emptyList()
        chestSpots.clear(); openedChests.clear(); shopSpots.clear(); apShopDone.clear()
        engine.enemies.clear(); engine.bullets.clear()
        val result = DungeonGenerator.generate(floor, rng)
        rooms = result.rooms
        floorCleared = 0
        startRoom = result.start
        engine.px = roomLeft(result.start) + result.start.w / 2
        engine.py = roomTop(result.start) + result.start.h / 2
        enterRoom(result.start)
    }

    private fun enterRoom(room: Room) {
        currentRoom = room
        if (!room.cleared && room.type == RoomType.SHOP) {
            // 商店免战：进门即可通行（清房计数统一走下方 visited 首入块，避免双计）
            room.cleared = true
        }
        locked = !room.cleared
        if (locked) {
            // 玩家可能正跨在门槛上（房间判定矩形与收边区之间）：推入房内可站立区
            engine.px = engine.px.coerceIn(roomLeft(room) + engine.playerR + 2f, roomLeft(room) + room.w - engine.playerR - 2f)
            engine.py = engine.py.coerceIn(roomTop(room) + engine.playerR + 2f, roomTop(room) + room.h - engine.playerR - 2f)
        }
        if (!room.visited) {
            room.visited = true
            if (room.cleared) { clearedRooms++; floorCleared++ }
        }
        // 场地 = 本房矩形（敌人不出房）；跨房实体保留（预刷新敌人待机中），子弹清空
        engine.bullets.clear()
        engine.arenaLeft = roomLeft(room) + 30f
        engine.arenaTop = roomTop(room) + 30f
        engine.arenaRight = roomLeft(room) + room.w - 30f
        engine.arenaBottom = roomTop(room) + room.h - 30f
        if (!room.cleared && !room.populated) populateRoom(room)
        // 绑定本房交互实体（宝箱开过不再出现；宝箱房 populate 在上一步完成）
        chest = chestSpots[room]?.takeIf { room !in openedChests }
        chestNear = false
        shop = shopSpots[room]
        shopNear = false
        // 激活本房待机敌人（进房即战）
        for (e in engine.enemies) {
            if (e.dormant && roomContains(room, e.x, e.y)) { e.dormant = false; entranceFxIfPending(e) }
        }
        // 预刷新：相邻未清房提前布置敌人（待机可见）
        for (n in room.neighbors.values) {
            if (!n.cleared && !n.populated) populateRoom(n)
        }
    }

    /** 敌人坐标是否在房间矩形内 */
    private fun roomContains(r: Room, x: Float, y: Float): Boolean {
        val l = roomLeft(r); val t = roomTop(r)
        return x >= l && x <= l + r.w && y >= t && y <= t + r.h
    }

    /** 当前房剩余活敌（预刷新的其他房敌人不计入清房判定） */
    private fun roomEnemiesLeft(r: Room): Int =
        engine.enemies.count { it.alive && !it.dormant && roomContains(r, it.x, it.y) }

    /** 按房型布置敌人（数量/强度随层数成长）；幂等，出生为待机态，进房才激活 */
    /** dormant→激活瞬间的出场特效（Boss 专属：震屏+冲击环+出场音效，一次性；邻房预刷新时不放） */
    private fun entranceFxIfPending(e: CombatEngine.Enemy) {
        if (!e.entrancePending || e.dormant) return
        e.entrancePending = false
        engine.addShake(10f)
        engine.events.add(CombatEngine.FxEvent(e.x, e.y, "", false, null, 4))
    }

    private fun populateRoom(room: Room) {
        if (room.populated) return
        room.populated = true
        android.util.Log.d("DGROOM", "populate floor=$floor ${room.type} at ${room.gx},${room.gy}")
        val endlessK = if (floor > MAX_FLOOR) 1.15f.pow(floor - MAX_FLOOR) else 1f   // 无尽第 5 层后指数加难
        val scaleHp = (1f + 0.40f * (floor - 1)) * endlessK
        val scaleDmg = (1f + 0.2f * (floor - 1)) * endlessK

        fun spawn(kind: EnemyKind, elite: Boolean, x: Float, y: Float, big: Boolean = false) {
            // 新怪特化：盾卫高血高伤慢速；自爆史莱姆脆但痛；弓手略脆
            val kindHp = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.9f
                EnemyKind.BOOM_SLIME -> 0.8f
                else -> 1f
            }
            val kindDmg = when (kind) {
                EnemyKind.SHIELD_GUARD -> 1.35f
                EnemyKind.BOOM_SLIME -> 1.5f
                EnemyKind.BONE_ARCHER -> 0.9f
                else -> 1f
            }
            val baseHp = (if (big) 260f else 30f) * scaleHp * (if (elite) 2.2f else 1f) * kindHp
            val speed = (when (kind) {
                EnemyKind.BAT -> 95f
                EnemyKind.BONE_ARCHER -> 78f
                EnemyKind.BOOM_SLIME -> 85f
                EnemyKind.SLIME -> 55f
                EnemyKind.CASTER -> 60f
                EnemyKind.SHIELD_GUARD -> 45f
                else -> 68f
            }) + floor * 4f
            val r = when {
                big -> 48f
                elite -> 27f
                kind == EnemyKind.SHIELD_GUARD -> 24f
                kind == EnemyKind.BONE_ARCHER -> 16f
                kind == EnemyKind.BOOM_SLIME -> 15f
                kind == EnemyKind.SLIME -> 17f
                else -> 18f
            }   // 整体加大 ~15%：更有怪物体积感
            val e = CombatEngine.Enemy(
                x, y, r, baseHp, baseHp, speed,
                (if (big) 12f else 6f) * scaleDmg * (if (elite) 1.4f else 1f) * kindDmg,
                kind, elite, xpValue = if (elite) 3 else 1,
            )
            if (elite) {
                // 精英词缀：狂暴/护盾/分裂/吸血/再生/霜环
                val affix = CombatEngine.Affix.entries.random(rng)
                e.affix = affix
                when (affix) {
                    CombatEngine.Affix.RAGE -> e.speed *= 1.35f
                    CombatEngine.Affix.SHIELD -> e.shieldHp = baseHp * 0.4f
                    CombatEngine.Affix.SPLIT -> {}
                    else -> {}
                }
            }
            e.dormant = true   // 预刷新待机：进房激活
            engine.spawnLater(engine.enemies.size * 0.12f, e)
        }

        fun spot(minDist: Float): Pair<Float, Float> {
            for (t in 0 until 20) {
                val x = roomLeft(room) + 120f + rng.nextFloat() * (room.w - 240f)
                val y = roomTop(room) + 100f + rng.nextFloat() * (room.h - 200f)
                if (kotlin.math.abs(x - engine.px) + kotlin.math.abs(y - engine.py) > minDist) return x to y
            }
            return roomLeft(room) + room.w / 2 to roomTop(room) + 140f
        }

        when (room.type) {
            RoomType.ELITE -> {
                repeat(2) { val (x, y) = spot(220f); spawn(EnemyKind.SKELETON, elite = true, x, y) }
                repeat(3) {
                    val kind = listOf(EnemyKind.SKELETON, EnemyKind.BAT, EnemyKind.BONE_ARCHER, EnemyKind.BOOM_SLIME).random(rng)
                    val (x, y) = spot(220f); spawn(kind, elite = false, x, y)
                }
            }
            RoomType.BOSS -> {
                // 每层 Boss：大体型 + 多阶段（<30% 狂暴）+ 每层不同机制（见 engine.bossAI）
                val boss = CombatEngine.Enemy(
                    roomLeft(room) + room.w / 2, roomTop(room) + room.h / 2 - 40f,
                    46f, 400f * scaleHp, 400f * scaleHp, 62f + floor * 3f,
                    11f * scaleDmg, EnemyKind.DUMMY, elite = false, xpValue = 8,
                )
                boss.bossFloor = floor
                boss.dormant = true   // 预刷新待机：玩家进房才激活（否则落地即追击，隔房参战/被隔房斩杀）
                boss.entrancePending = true   // 出场特效（震屏+冲击环）推迟到激活瞬间——populate 发生在邻房预刷新，提前放会误震邻房
                engine.spawnLater(0.4f, boss)
            }
            RoomType.CHEST -> {
                // 房心放宝箱：开启前锁门，开启掉装备+金币（见 openChest）
                chestSpots[room] = roomLeft(room) + room.w / 2 to roomTop(room) + room.h / 2
            }
            RoomType.SHOP -> {
                // 房心放货摊：免战可穿过，走近交互购物
                shopSpots[room] = roomLeft(room) + room.w / 2 to roomTop(room) + room.h / 2
                val gear = Equipment.generate(floor, rng)
                shopGoods = listOf(
                    ShopGood("gear", gear.name, gear.describe(), 30 + 10 * floor).also { it.item = gear },
                    ShopGood("heal", "行囊干粮", "立即回复 50% 生命", 20 + 5 * floor),
                    ShopGood("reroll", "词条重铸", "选一件已穿戴装备，重随其词条（保留品质）", 15 + 5 * floor),
                )
            }
            RoomType.BATTLE -> {
                val n = 5 + rng.nextInt(2) + (floor - 1)   // 大房间：首层 5-6 只，逐层+1
                repeat(n) {
                    val kind = when {
                        floor >= 3 && rng.nextInt(7) == 0 -> EnemyKind.SHIELD_GUARD   // 盾卫第 3 层起
                        floor >= 2 && rng.nextInt(6) == 0 -> EnemyKind.BONE_ARCHER    // 弓手第 2 层起
                        floor >= 2 && rng.nextInt(6) == 0 -> EnemyKind.BOOM_SLIME     // 自爆第 2 层起
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

    /** 传送门交互：靠近 + 确认。标准模式最终层=进入即结算；其余进下层（无尽一直往下） */
    fun enterPortal() {
        if (phase != Phase.EXPLORING || portal == null || !portalNear) return
        if (floor >= MAX_FLOOR && !endless) {
            phase = Phase.VICTORY   // 结算发生在走进传送门之后（而非 Boss 倒下瞬间）
            return
        }
        transition = 1.2f
        phase = Phase.TRANSITION
        engine.joyActive = false
    }

    /** 宝箱交互：靠近 + 确认开箱（掉装备与金币，随即清房开门） */
    fun openChest() {
        if (phase != Phase.EXPLORING) return
        val room = currentRoom ?: return
        val c = chest ?: return
        if (!chestNear) return
        chest = null
        chestNear = false
        openedChests.add(room)
        val gain = 12 + 6 * floor
        coins += gain
        engine.events.add(CombatEngine.FxEvent(c.first, c.second - 40f, "金币 +$gain", false, null, 2))
        engine.events.add(CombatEngine.FxEvent(c.first, c.second, "", false, null, 10))   // 金色冲击环
        val item = Equipment.generate(floor, rng,
            if (rng.nextFloat() < 0.20f + 0.04f * floor) Equipment.Rarity.EPIC else Equipment.Rarity.RARE)
        drops.add(Drop(c.first + 46f, c.second + 24f, item))
        room.cleared = true
        locked = false
        clearedRooms++
        floorCleared++
    }

    /** 商店交互：靠近打开货架（世界暂停） */
    fun openShop() {
        if (phase != Phase.EXPLORING || shop == null || !shopNear) return
        phase = Phase.SHOP
        engine.joyActive = false
        engine.attackHeld = false
    }

    fun closeShop() { if (phase == Phase.SHOP) phase = Phase.EXPLORING }

    /** 购买商品（reroll 走 rerollSlot 两步；金币不足或已售出为空操作） */
    fun buyGood(index: Int) {
        if (phase != Phase.SHOP) return
        val g = shopGoods.getOrNull(index) ?: return
        if (g.sold || coins < g.cost) return
        when (g.id) {
            "gear" -> {
                val item = g.item ?: return
                coins -= g.cost
                g.sold = true
                forceEquip(item)
            }
            "heal" -> {
                coins -= g.cost
                g.sold = true
                engine.heal((engine.maxHp * 0.5f).toInt())
                engine.events.add(CombatEngine.FxEvent(engine.px, engine.py, "", false, null, 6))
            }
        }
    }

    /** 词条重铸（商店第二步：UI 先让玩家选槽位）；空槽或金币不足为空操作 */
    fun rerollSlot(slot: Equipment.Slot) {
        if (phase != Phase.SHOP) return
        val g = shopGoods.firstOrNull { it.id == "reroll" } ?: return
        if (g.sold || coins < g.cost) return
        val cur = slots[slot] ?: return
        val fresh = Equipment.reroll(cur, rng)
        coins -= g.cost
        g.sold = true
        slots[slot] = fresh
        reapplyBonus(cur, fresh)
        engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 50f, "${fresh.name} 重铸！", false, null, 2))
    }

    /** 强制换装（商店购买）：无条件替换该槽，旧件半价分解 */
    private fun forceEquip(item: Equipment.Item) {
        val old = slots[item.slot]
        if (old != null) {
            val gain = Equipment.salvageValue(old) / 2
            coins += gain
            engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 40f, "分解 +$gain 金", false, null, 2))
        }
        slots[item.slot] = item
        reapplyBonus(old, item)
        engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 60f, "${item.name}！", false, null, 2))
    }

    fun tick(dtRaw: Float) {
        if (phase != Phase.EXPLORING && phase != Phase.LEVELUP && phase != Phase.GAMEOVER && phase != Phase.TRANSITION) return
        val dt = dtRaw.coerceIn(0f, 0.05f)
        if ((runTimeSec * 2) != lastLogSec) {
            lastLogSec = runTimeSec * 2
            val boss = engine.enemies.firstOrNull { it.bossFloor > 0 }
            android.util.Log.d("DGBG", "st phase=$phase locked=$locked room=${currentRoom?.gx},${currentRoom?.gy} en=${engine.enemies.size} pend=${engine.hasPendingSpawns()} lvl=${engine.level} hp=${engine.hp} dt=$dt dtRaw=$dtRaw apNext=${apNext?.gx},${apNext?.gy} apP=$apPhase enP=${engine.phase} pUp=${engine.pendingUpgrades.size} boss=${boss?.let { "a=${it.alive} d=${it.dormant} hp=%.0f@%.0f,%.0f".format(it.hp, it.x, it.y) } ?: "none"} portal=${portal != null} portalNear=$portalNear skip=${apSkipped.size}")
        }
        if (phase == Phase.EXPLORING) {
            timeAcc += dt
            if (timeAcc >= 1f) { runTimeSec += 1; timeAcc -= 1f }
        }
        if (autopilot) {
            if (phase == Phase.LEVELUP) {
                apWait += dt
                if (apWait > 0.6f) {
                    val first = engine.pendingUpgrades.firstOrNull()
                    if (first == null) {
                        // 兜底：升级队列意外为空 → 直接回探索，防止卡死
                        engine.forcePlaying()
                        phase = Phase.EXPLORING
                    } else chooseUpgrade(first.id)
                    apWait = 0f
                }
            } else {
                apWait = 0f
                // 导航停滞看门狗：未锁门时位置 3 秒几乎不动 → 重规划；同一目标 3 次停滞 → 本层跳过
                if (!locked) {
                    val moved = kotlin.math.abs(engine.px - navLastX) + kotlin.math.abs(engine.py - navLastY)
                    navStuckT = if (moved < 2f) navStuckT + dt else 0f
                    navLastX = engine.px; navLastY = engine.py
                    if (navStuckT > 3f) {
                        val tgt = apNext
                        if (tgt != null && tgt === navStuckRoom) navStuckN++ else { navStuckRoom = tgt; navStuckN = 1 }
                        android.util.Log.d("DGAIP", "nav stuck n=$navStuckN tgt=${tgt?.gx},${tgt?.gy} at %.0f,%.0f pass=%s"
                            .format(engine.px, engine.py, canPass(engine.px, engine.py)))
                        if (tgt != null && navStuckN >= 3) apSkipped.add(tgt)
                        apNext = null; apPhase = 0; navStuckT = 0f
                    }
                }
                autopilotSteer(dt)
                // 战斗中技能好了就用
                if (locked && engine.enemies.isNotEmpty() && engine.skillCd <= 0f) engine.useSkill()
            }
        }
        // 过场：黑幕期间冻结世界，倒计时结束进下层（技能=职业专属，无需过层选取）
        if (phase == Phase.TRANSITION) {
            transition -= dt
            if (transition <= 0f) {
                floor++
                buildFloor()
                phase = Phase.EXPLORING
            }
            return
        }
        // 传送门接近检测
        portal?.let { pt ->
            portalNear = kotlin.math.hypot(engine.px - pt.first, engine.py - pt.second) < 95f
        }
        // 宝箱/货摊接近检测
        chest?.let { c -> chestNear = kotlin.math.hypot(engine.px - c.first, engine.py - c.second) < 80f }
        shop?.let { s -> shopNear = kotlin.math.hypot(engine.px - s.first, engine.py - s.second) < 95f }
        engine.tick(dt)
        tickCamera(dt)
        // 血条白色残影：hpGhost 慢速跟随真实 hp（掉血时白色部分延迟消失）
        hpGhost += (engine.hp - hpGhost) * (dt * 4f).coerceIn(0f, 1f)
        if (kotlin.math.abs(engine.hp - hpGhost) < 0.5f) hpGhost = engine.hp.toFloat()
        // 看门狗（自动驾驶）：锁门房里敌人已清光却没触发清房 → 强制开门，防任何边角状态卡死
        if (autopilot && phase == Phase.EXPLORING) {
            val roomNow = currentRoom
            if (locked && roomNow != null && (roomNow.type != RoomType.CHEST || chest == null) &&
                roomEnemiesLeft(roomNow) == 0 && !engine.hasPendingSpawns()) {
                apStuck += dt
                if (apStuck > 1.5f) {
                    roomNow.cleared = true
                    android.util.Log.d("DGROOM", "WATCHDOG clears ${roomNow.type} ${roomNow.gx},${roomNow.gy}")
                    locked = false
                    clearedRooms++
                    floorCleared++
                    if (roomNow.type == RoomType.BOSS) {
                        portal = roomLeft(roomNow) + roomNow.w / 2 to roomTop(roomNow) + roomNow.h / 2
                    }
                    apNext = null; apPhase = 0; apStuck = 0f
                }
            } else apStuck = 0f
        }

        // 引擎状态 → 地牢状态
        if (engine.phase == CombatEngine.Phase.LEVELUP && phase == Phase.EXPLORING) {
            phase = Phase.LEVELUP
        } else if (engine.phase == CombatEngine.Phase.GAMEOVER && phase == Phase.EXPLORING) {
            phase = Phase.GAMEOVER
        }

        // 掉落物：动画 + 触碰拾取（自动穿戴/分解）
        val dropIt = drops.iterator()
        while (dropIt.hasNext()) {
            val d = dropIt.next()
            d.t += dt
            val dx = d.x - engine.px; val dy = d.y - engine.py
            val rr = engine.playerR + 16f
            if (dx * dx + dy * dy <= rr * rr) {
                d.alive = false
                dropIt.remove()
                pickupEquip(d.item)
            }
        }

        // 本房待机敌持续激活（延迟刷怪落地时玩家已进房：进门瞬间的激活会漏掉它们）
        currentRoom?.let { cur ->
            for (e in engine.enemies) {
                if (e.dormant && roomContains(cur, e.x, e.y)) { e.dormant = false; entranceFxIfPending(e) }
            }
        }
        // 走进新房间
        if (phase == Phase.EXPLORING) {
            val here = rooms.firstOrNull { r ->
                val l = roomLeft(r); val t = roomTop(r)
                engine.px >= l && engine.px <= l + r.w && engine.py >= t && engine.py <= t + r.h
            }
            if (here != null && here !== currentRoom) enterRoom(here)
            // 清房判定（本房延迟刷怪全落地且清空才开门；宝箱房必须开箱，chest 为空的异常房自动放行兜底）
            val room = currentRoom
            if (room != null && locked && (room.type != RoomType.CHEST || chest == null) &&
                roomEnemiesLeft(room) == 0 && !engine.hasPendingSpawns()) {
                room.cleared = true
                android.util.Log.d("DGROOM", "clear-check clears ${room.type} ${room.gx},${room.gy}")
                locked = false
                clearedRooms++
                floorCleared++
                if (room.type == RoomType.BOSS) {
                    // Boss 后一律生成传送门：走进传送门才结算（最终层）/进下层（无尽）
                    portal = roomLeft(room) + room.w / 2 to roomTop(room) + room.h / 2
                }
            }
        }
    }

    /** DEBUG：自动驾驶的摇杆决策——带承诺的导航状态机（阈值切换会自激振荡，必须记状态） */
    private fun autopilotSteer(dt: Float) {
        val room = currentRoom ?: return
        // 宝箱房：走向宝箱开箱（锁门无怪，必须先于锁门战斗分支，否则 bot 会站住不动）
        val chestNow = chest
        if (chestNow != null) {
            if (chestNear) {
                engine.joyActive = false
                openChest()
            } else apSteerTo(chestNow.first, chestNow.second)
            return
        }
        // 商店房：走到货摊开一次店（世界冻结供外部脚本截图/购物；autopilot 由 Screen 侧自动关店，
        // 关后靠 apShopDone 不再重开，否则站在货摊边会陷入 开→关→开 死循环）
        val shopNow = shop
        val shopRoom = currentRoom
        if (shopNow != null && shopRoom?.type == RoomType.SHOP && phase == Phase.EXPLORING && shopRoom !in apShopDone) {
            if (shopNear) {
                engine.joyActive = false
                apShopDone.add(shopRoom)
                openShop()
            } else apSteerTo(shopNow.first, shopNow.second)
            return
        }
        if (locked) {
            // 战斗走位：近战贴脸保证命中与朝向；远程保持 200~420 距离风筝
            val enemy = engine.enemies.filter { it.alive && !it.dormant }.minByOrNull {
                (it.x - engine.px) * (it.x - engine.px) + (it.y - engine.py) * (it.y - engine.py)
            }
            if (enemy == null) { engine.joyActive = false; return }
            val dx = enemy.x - engine.px; val dy = enemy.y - engine.py
            val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val melee = engine.weapon is Weapon.MeleeSlash
            val fleeFar = engine.hp < engine.maxHp * 0.4f   // 低血：更早拉开距离
            val pull = when {
                melee -> d - 55f                       // 近战：贴到攻击距离
                d < (if (fleeFar) 300f else 200f) -> (d - (if (fleeFar) 300f else 200f)) * 1.5f
                d > 420f -> (d - 420f) * 1.5f          // 太远：靠近
                else -> 0f
            }
            if (melee) {
                // 残血：背离最近敌人撤退并混向房心，避免站桩换血暴毙
                if (engine.hp < engine.maxHp * 0.35f) {
                    val cx = (engine.arenaLeft + engine.arenaRight) / 2
                    val cy = (engine.arenaTop + engine.arenaBottom) / 2
                    apSteerTo((engine.px * 2 - enemy.x + cx) / 2, (engine.py * 2 - enemy.y + cy) / 2)
                    return
                }
                // 近战：停在 55px 站位点（不冲进怪堆——穿过敌群时朝向逐帧翻转 180°，
                // 挥砍永远背对目标；站定后敌人在接触环上、正面朝向，扇形必中）
                if (d < 75f) {
                    engine.joyActive = false
                    engine.faceTo(enemy.x, enemy.y)   // 站定输出：facing 只随移动更新，必须原地转向锁定目标
                } else {
                    apSteerTo(engine.px + dx / d * (d - 55f), engine.py + dy / d * (d - 55f))
                }
                return
            }
            var gx = engine.px + dx / d * pull
            var gy = engine.py + dy / d * pull
            if (kotlin.math.abs(pull) < 20f) { engine.joyActive = false; return }
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

        // 传送门交互：靠近且本层没有未清房（宝箱/商店先扫完）才进下层；跨房移动交给 BFS 导航
        if (portal != null && portalNear && !apRemainRooms()) {
            engine.joyActive = false
            enterPortal()
            return
        }

        // 0) 战斗结束的房间：先扫一圈经验球（升级必须捡球）；只追可达的球（怪死在墙缝外会留下够不到的球）
        if (apPhase == 2) {
            val orb = engine.orbs.filter { it.alive && canPass(it.x, it.y) }.minByOrNull {
                (it.x - engine.px) * (it.x - engine.px) + (it.y - engine.py) * (it.y - engine.py)
            }
            if (orb != null) {
                apOrbT += dt
                if (apOrbT > 6f) {
                    // 看门狗：球卡在不可达位置（墙缝/门外）→ 放弃拾取继续导航
                    android.util.Log.d("DGAIP", "orb giveup at %.0f,%.0f player=%.0f,%.0f pass=%s"
                        .format(orb.x, orb.y, engine.px, engine.py, canPass(orb.x, orb.y)))
                    apPhase = 0; apOrbT = 0f; engine.joyActive = false
                    return
                }
                apSteerTo(orb.x, orb.y)
                return
            }
            apPhase = 0; apOrbT = 0f   // 没球了，继续导航
        }

        // 1) 无航程 → 选最近的未清房并规划首段；全清则把传送门所在房当作目标（先扫房后下层由此自然成立）
        if (apNext == null) {
            val target = nearestUncleared(room)
                ?: portal?.let { pt -> rooms.firstOrNull { roomContains(it, pt.first, pt.second) } }
                ?: run {
                    android.util.Log.d("DGAIP", "no target: uncleared=${rooms.filter { !it.cleared && it.type != RoomType.START }.map { "${it.type}@${it.gx},${it.gy}" }} from=${room.gx},${room.gy}")
                    engine.joyActive = false
                    return
                }
            val path = apBfsPath(room, target)
            apNext = if (path.isEmpty()) target else path.first()
            apPhase = 0
        }
        val next = apNext ?: return

        // 2) 已进入 next 房：承诺完成，重规划（传送门房：无未清房时径直走向传送门交互）
        if (room === next) {
            val pt = portal
            if (pt != null && rooms.firstOrNull { roomContains(it, pt.first, pt.second) } === room) {
                if (!apRemainRooms()) {
                    if (portalNear) {
                        engine.joyActive = false
                        enterPortal()
                    } else apSteerTo(pt.first, pt.second)
                    return
                }
                if (room.cleared) { apPhase = 2; apOrbT = 0f }   // 还有未清房：先拾球，继续导航
                apNext = null
                return
            }
            if (room.cleared) { apPhase = 2; apOrbT = 0f }   // 清完：先拾球
            apNext = null
            return
        }

        // 3) 阶段执行（承诺制：进入下一阶段只看位置越过与否，不看与目标的距离）
        when (apPhase) {
            0 -> {
                val door = doorCenter(room, next)
                apSteerTo(door.first, door.second)
                // 越过门所在墙：房间序号变化由 enterRoom 完成；若仍在本房但已越过门心，切阶段。
                // 容差 8f：到位松杆（d<0.5f）后玩家可能停在门心前极近处，精确比较会永假
                val crossed = if (room.gy == next.gy) {
                    (next.gx > room.gx && engine.px >= door.first - 8f) || (next.gx < room.gx && engine.px <= door.first + 8f)
                } else {
                    (next.gy > room.gy && engine.py >= door.second - 8f) || (next.gy < room.gy && engine.py <= door.second + 8f)
                }
                if (crossed) apPhase = 1
            }
            else -> {
                val rc = roomCenter(next)
                apSteerTo(rc.first, rc.second)
            }
        }
    }


    /** 本层还有没有值得跑的未清房（跳过房不算——决定 bot 是否进传送门） */
    private fun apRemainRooms(): Boolean =
        rooms.any { !it.cleared && it.type != RoomType.START && it !in apSkipped }

    private fun apSteerTo(x: Float, y: Float) {
        val dx = x - engine.px; val dy = y - engine.py
        val d = kotlin.math.hypot(dx, dy)
        // 到位即松杆；否则 joy 恒为单位向量——不能用 coerceAtLeast(1f) 做分母：
        // 那会把 <1px 的剩余距离压成亚单位 joy，速度指数衰减成渐近逼近，
        // 直接移动（无惯性）下永远差 float 最后一步，crossed 精确比较永不翻转 → 门口永滞
        if (d < 0.5f) { engine.joyActive = false; return }
        engine.joyActive = true
        engine.joyX = dx / d
        engine.joyY = dy / d
    }

    private fun nearestUncleared(from: Room): Room? {
        var best: Room? = null
        var bestD = Float.MAX_VALUE
        for (r in rooms) {
            if (r.cleared || r.type == RoomType.START || r in apSkipped) continue
            if (apBfsPath(from, r).isEmpty()) continue   // 图不连通的孤立房 → 跳过，防止朝墙死推
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

    /** 拾取装备：空槽穿上；已有则评分比较，胜者穿戴、败者分解（金币 + 飘字） */
    private fun pickupEquip(item: Equipment.Item) {
        val cur = slots[item.slot]
        val coinGain = Equipment.salvageValue(item)
        if (cur == null || item.score > cur.score) {
            slots[item.slot] = item
            if (cur != null) {
                val half = Equipment.salvageValue(cur) / 2
                coins += half
                engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 40f,
                    "分解 +$half 金", false, null, 2))
            }
            engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 60f,
                "${item.name}！", false, null, 2))
            reapplyBonus(cur, item)
        } else {
            coins += coinGain
            engine.events.add(CombatEngine.FxEvent(engine.px, engine.py - 40f,
                "${item.name} 分解 +$coinGain 金", false, null, 2))
        }
    }

    /** 换装后把装备增量重新应用到引擎（差量式） */
    private fun reapplyBonus(oldItem: Equipment.Item?, newItem: Equipment.Item) {
        val oldB = Equipment.Bonus()
        if (oldItem != null) oldB.fromItem(oldItem)
        val newB = Equipment.aggregate(slots)
        engine.buffAttack(newB.atk - oldB.atk)
        engine.setMaxHp(engine.maxHp + newB.hp - oldB.hp)
        engine.buffSpeed((newB.spd - oldB.spd).toFloat())
        engine.buffCrit((newB.crit - oldB.crit) / 100f)
        engine.buffPickupMult(1f + (newB.pickup - oldB.pickup) / 100f)
        engine.buffElem((newB.elem - oldB.elem) / 100f)
    }

    fun chooseUpgrade(id: String) {
        if (phase != Phase.LEVELUP) return
        engine.chooseUpgrade(id)
        if (engine.phase == CombatEngine.Phase.PLAYING) phase = Phase.EXPLORING
    }

    // ---------- 世界几何 ----------
    fun roomLeft(r: Room) = r.gx * GRID_X - r.w / 2
    fun roomTop(r: Room) = r.gy * GRID_Y - r.h / 2

    /** 门是否通行：清房锁门机制（在未清房间战斗时全部封闭） */
    fun doorOpen(a: Room, b: Room) = !locked

    /** 墙体碰撞回调（引擎移动玩家时查询）：开放矩形并集（房间 + 开门走廊） */
    val canPass: (Float, Float) -> Boolean = handler@{ x, y ->
        val r = engine.playerR
        for (room in rooms) {
            val l = roomLeft(room); val t = roomTop(room)
            if (x >= l + r && x <= l + room.w - r && y >= t + r && y <= t + room.h - r) return@handler true
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
            val l = minOf(roomLeft(a) + a.w, roomLeft(b) + b.w) - DOOR_PROBE
            val rr = maxOf(roomLeft(a), roomLeft(b)) + DOOR_PROBE
            val cy = a.gy * GRID_Y
            x >= l + r && x <= rr - r && y >= cy - DOOR_H / 2 + r && y <= cy + DOOR_H / 2 - r
        } else {
            // 纵向走廊：上房的底边 → 下房的顶边（端头各内伸 PROBE 与房间收边区重叠）
            val t = minOf(roomTop(a) + a.h, roomTop(b) + b.h) - DOOR_PROBE
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
        coins = 0
        shopGoods = emptyList()
        slots.clear()
        drops.clear()
        appliedBonus = Equipment.Bonus()
        chest = null; chestNear = false
        shop = null; shopNear = false
        chestSpots.clear(); openedChests.clear(); shopSpots.clear(); apShopDone.clear()
        runTimeSec = 0
        timeAcc = 0f
        cls = null
        locked = false
        apNext = null
        apPhase = 0
        engine.reset()
    }
}
