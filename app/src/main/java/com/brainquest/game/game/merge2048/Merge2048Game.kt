package com.brainquest.game.game.merge2048

import kotlinx.serialization.Serializable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** 2048 知识配对：瓷砖持有「配对内容」与「等级」，等级相同且互为同一对的左右两半才能合并 */
@Serializable
data class PairTile(
    val tier: Int,        // 1→显示2^1，即基础块
    val pairId: String,   // 属于哪一对
    val side: Int,        // 0=左半（词条） 1=右半（释义）
) {
    val value: Int get() = 1 shl tier
}

@Serializable
data class MergePair(val id: String, val left: String, val right: String)

@Serializable
data class PairTiers(val name: String, val tiers: List<List<MergePair>>)

class Merge2048Game(
    val tiers: List<List<MergePair>>,
    private val rng: Random = Random.Default,
    size: Int = 4,
) {
    val size = size
    var grid: Array<Array<PairTile?>> = Array(size) { arrayOfNulls(size) }
        private set

    /** 状态版本号：每次棋盘/数值变化 +1，UI 读取它以触发重组 */
    var version by mutableIntStateOf(0)
        private set

    var score = 0
        private set
    var gameOver = false
        private set
    var win = false
        private set
    var merges = 0
        private set
    var mistakes = 0
        private set
    var lastWrongText: String? = null
        private set
    var highestTier = 0
        private set

    init {
        require(tiers.isNotEmpty())
        spawn(); spawn()
        version++
    }

    val maxTier: Int get() = tiers.size

    fun tileText(t: PairTile): String {
        val pool = tiers[(t.tier - 1).coerceIn(0, tiers.size - 1)]
        val pair = pool.find { it.id == t.pairId } ?: pool.first()
        return if (t.side == 0) pair.left else pair.right
    }

    private fun poolFor(tier: Int): List<MergePair> =
        tiers[(tier - 1).coerceIn(0, tiers.size - 1)]

    private fun randomTile(tier: Int = 1): PairTile {
        val pool = poolFor(tier)
        val pair = pool.random(rng)
        return PairTile(tier, pair.id, rng.nextInt(2))
    }

    private fun emptyCells(): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (grid[r][c] == null) list.add(r to c)
        }
        return list
    }

    private fun spawn() {
        val cells = emptyCells()
        if (cells.isEmpty()) return
        val (r, c) = cells.random(rng)
        grid[r][c] = randomTile()
    }

    /** 判断两块瓷砖能否合并：同一对 + 相反两侧 + 同一等级 */
    fun canMerge(a: PairTile, b: PairTile): Boolean =
        a.tier == b.tier && a.pairId == b.pairId && a.side != b.side

    fun move(dir: Dir): Boolean {
        if (gameOver) return false
        val before = grid.map { it.map { t -> t?.hashCode() }.joinToString() }
        var movedAny = false
        lastWrongText = null

        for (line in lines(dir)) {
            val cells = line.map { (r, c) -> r to c }
            val tiles = cells.map { grid[it.first][it.second] }
            val merged = compactMerge(tiles)
            cells.forEachIndexed { i, pos -> grid[pos.first][pos.second] = merged[i] }
            if (merged != tiles) movedAny = true
        }
        if (!movedAny) return false
        spawn()
        checkEnd()
        version++
        return true
    }

    /** 单行压缩合并：一次移动中每块瓷砖最多合并一次 */
    private fun compactMerge(tiles: List<PairTile?>): List<PairTile?> {
        val out = mutableListOf<PairTile?>()
        var i = 0
        while (i < tiles.size) {
            val cur = tiles[i]
            if (cur == null) { i++; continue }
            // 找下一个非空
            var j = i + 1
            while (j < tiles.size && tiles[j] == null) j++
            val next = if (j < tiles.size) tiles[j] else null
            when {
                next != null && canMerge(cur, next) -> {
                    // 合并：升级到 tier+1 的新瓷砖（随机取更高等级一对的其中一半）
                    val newTier = cur.tier + 1
                    if (newTier > highestTier) highestTier = newTier
                    score += newTier * newTier
                    merges++
                    out.add(randomTile(newTier))
                    i = j + 1
                    if (newTier >= maxTier && !win) win = true
                }
                next != null && cur.tier == next.tier && cur.pairId != next.pairId -> {
                    // 同级不同对：不允许合并，提示配错
                    lastWrongText = "「${tileText(cur)}」≠「${tileText(next)}」"
                    mistakes++
                    out.add(cur)
                    i++
                }
                else -> { out.add(cur); i++ }
            }
        }
        while (out.size < size) out.add(null)
        return out
    }

    private fun checkEnd() {
        if (emptyCells().isEmpty() && !anyMergePossible()) gameOver = true
    }

    private fun anyMergePossible(): Boolean {
        for (r in 0 until size) for (c in 0 until size) {
            val t = grid[r][c] ?: continue
            if (c + 1 < size && grid[r][c + 1]?.let { canMerge(t, it) } == true) return true
            if (r + 1 < size && grid[r + 1][c]?.let { canMerge(t, it) } == true) return true
        }
        return false
    }

    // 方向 -> 遍历线（每条线是格子坐标序列，从挤压方向的一端开始）
    private fun lines(dir: Dir): List<List<Pair<Int, Int>>> {
        val result = mutableListOf<List<Pair<Int, Int>>>()
        for (k in 0 until size) {
            val line = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until size) {
                val (r, c) = when (dir) {
                    Dir.LEFT -> k to i
                    Dir.RIGHT -> k to (size - 1 - i)
                    Dir.UP -> i to k
                    Dir.DOWN -> (size - 1 - i) to k
                }
                line.add(r to c)
            }
            result.add(line)
        }
        return result
    }
}

enum class Dir { LEFT, RIGHT, UP, DOWN }
