package com.brainquest.game.game.dungeon

import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.random.Random

/**
 * 地牢生成器：网格图随机扩展 6-12 个房间，四方向连通（由构造保证）。
 * 距起点最远的房间为 Boss 房；其余按权重分配 战斗45% / 精英15% / 宝箱10% / 商店10%（余量并入战斗）。
 */
object DungeonGenerator {

    class Result(
        val rooms: List<Room>,
        val start: Room,
        val boss: Room,
        val floor: Int,
    )

    fun generate(floor: Int, rng: Random = Random): Result {
        val target = 6 + rng.nextInt(7)   // 6..12
        val grid = HashMap<Pair<Int, Int>, Room>()
        val rooms = ArrayList<Room>(target)
        var nextId = 0

        fun roomAt(gx: Int, gy: Int) = grid[gy to gx]   // 键 = (行,列) 避免与 x/y 混淆

        fun place(gx: Int, gy: Int, type: RoomType): Room {
            val r = Room(nextId++, gx, gy, type)
            grid[gy to gx] = r
            rooms.add(r)
            // 邻接表双向登记
            for (d in Dir.entries) {
                roomAt(gx + d.dx, gy + d.dy)?.let { n ->
                    r.neighbors[d] = n
                    n.neighbors[opposite(d)] = r
                }
            }
            return r
        }

        val start = place(0, 0, RoomType.START)
        var frontier = listOf(start)

        // 随机扩展：每次从已有房间随机挑一个、随机方向尝试放新房间
        while (rooms.size < target) {
            val base = frontier.random(rng)
            val d = Dir.entries.random(rng)
            val nx = base.gx + d.dx
            val ny = base.gy + d.dy
            if (roomAt(nx, ny) == null) frontier = frontier + place(nx, ny, RoomType.BATTLE)
            // frontier 偶尔收紧：全部用 rooms 也行，frontier 保偏向树状生长
            if (rng.nextInt(4) == 0) frontier = rooms
        }

        // Boss 房 = 距起点最远（曼哈顿距离，平手取 id 大者）
        val bossRoom = rooms.filter { it !== start }.maxBy { abs(it.gx) + abs(it.gy) * 2 + it.id * 0.01 }
        bossRoom.type = RoomType.BOSS

        // 其余房间按权重分配类型
        val pool = rooms.filter { it !== start && it !== bossRoom }
        pool.shuffled(rng).forEach { r ->
            val roll = rng.nextFloat()
            r.type = when {
                roll < 0.15f -> RoomType.ELITE
                roll < 0.25f -> RoomType.CHEST
                roll < 0.35f -> RoomType.SHOP
                else -> RoomType.BATTLE
            }
        }

        return Result(rooms, start, bossRoom, floor)
    }

    private fun opposite(d: Dir) = when (d) {
        Dir.UP -> Dir.DOWN
        Dir.DOWN -> Dir.UP
        Dir.LEFT -> Dir.RIGHT
        Dir.RIGHT -> Dir.LEFT
    }

    private fun abs(v: Int) = if (v < 0) -v else v
}
