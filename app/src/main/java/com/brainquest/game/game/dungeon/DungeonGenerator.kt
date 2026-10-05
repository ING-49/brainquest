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

    /**
     * 主线一条线：起点 → 线性 5-8 个战斗房（允许拐弯，不与已占格重叠）→ 链尾 Boss；
     * 沿主线随机挂 2-4 个支房（宝箱/商店/精英），形成岔路探索感。
     */
    fun generate(floor: Int, rng: Random = Random): Result {
        val grid = HashMap<Pair<Int, Int>, Room>()
        val rooms = ArrayList<Room>(12)
        var nextId = 0

        fun roomAt(gx: Int, gy: Int) = grid[gy to gx]

        fun place(gx: Int, gy: Int, type: RoomType): Room {
            val r = Room(nextId++, gx, gy, type)
            grid[gy to gx] = r
            rooms.add(r)
            for (d in Dir.entries) {
                roomAt(gx + d.dx, gy + d.dy)?.let { n ->
                    r.neighbors[d] = n
                    n.neighbors[opposite(d)] = r
                }
            }
            return r
        }

        val start = place(0, 0, RoomType.START)
        var cur = start
        var dir = Dir.entries.random(rng)
        val mainLen = 4 + rng.nextInt(2)   // 4..5 个主线战斗房（大房间战斗更久，控制单局 8-15 分钟）

        repeat(mainLen) {
            // 优先延续当前方向，撞占格则换向（不回头）
            val tries = listOf(dir) +
                Dir.entries.filter { it != dir && it != opposite(dir) }.shuffled(rng) +
                listOf(opposite(dir))
            var placed = false
            for (d in tries) {
                val nx = cur.gx + d.dx
                val ny = cur.gy + d.dy
                if (roomAt(nx, ny) == null) {
                    dir = d
                    cur = place(nx, ny, RoomType.BATTLE)
                    placed = true
                    break
                }
            }
            if (!placed) return@repeat   // 被围死：提前收链
        }

        // Boss = 链尾（主线走多远 Boss 就多远）
        val boss = cur
        boss.type = RoomType.BOSS

        // 支房：挂在主线中段房间的空闲邻格
        val branchTypes = listOf(RoomType.CHEST, RoomType.SHOP, RoomType.ELITE)
        val anchors = rooms.filter { it !== start && it !== boss }.shuffled(rng)
        var branches = 0
        for (base in anchors) {
            if (branches >= 2 + rng.nextInt(2)) break
            for (d in Dir.entries.shuffled(rng)) {
                val nx = base.gx + d.dx
                val ny = base.gy + d.dy
                if (roomAt(nx, ny) == null) {
                    place(nx, ny, branchTypes[rng.nextInt(branchTypes.size)])
                    branches++
                    break
                }
            }
        }

        // 房型定尺寸：Boss 最大 / 战斗标准 / 精英略小 / 宝箱·商店·起点紧凑（少空旷感）
        for (r in rooms) {
            when (r.type) {
                RoomType.BOSS -> { r.w = 2100f; r.h = 1350f }
                RoomType.ELITE -> { r.w = 1600f; r.h = 1050f }
                RoomType.CHEST, RoomType.SHOP -> { r.w = 1300f; r.h = 850f }
                RoomType.START -> { r.w = 1400f; r.h = 900f }
                else -> { r.w = 1850f; r.h = 1200f }
            }
        }

        return Result(rooms, start, boss, floor)
    }

private fun opposite(d: Dir) = when (d) {
        Dir.UP -> Dir.DOWN
        Dir.DOWN -> Dir.UP
        Dir.LEFT -> Dir.RIGHT
        Dir.RIGHT -> Dir.LEFT
    }

    private fun abs(v: Int) = if (v < 0) -v else v
}
