package com.brainquest.game.game.klotski

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 华容道棋子（坐标 + 尺寸，单位：格） */
data class KBlock(
    val id: Int,
    val name: String,
    val row: Int,
    val col: Int,
    val w: Int,
    val h: Int,
)

/** 关卡：经典布局，棋盘固定 4 列 × 5 行，曹操 2×2 需滑到底部中央出口 (row 3, col 1) */
data class KlotskiLevel(
    val id: String,
    val name: String,
    /** 最优解步数（BFS 校验得出，tools/klotski_verify.py），供玩家参考 */
    val par: Int,
    val blocks: List<KBlock>,
)

object KlotskiLevels {

    private const val CAO = "曹操"

    private fun b(id: Int, name: String, row: Int, col: Int, w: Int, h: Int) = KBlock(id, name, row, col, w, h)

    /** 将拥曹营（最易，最优 24 步） */
    private val jiangYong = listOf(
        b(0, CAO, 1, 1, 2, 2),
        b(1, "张飞", 0, 1, 1, 1),
        b(2, "赵云", 0, 2, 1, 1),
        b(3, "马超", 0, 0, 1, 2),
        b(4, "黄忠", 0, 3, 1, 2),
        b(5, "关羽", 3, 1, 2, 1),
        b(6, "卒", 3, 0, 1, 1),
        b(7, "卒", 3, 3, 1, 1),
        b(8, "卒", 4, 1, 1, 1),
        b(9, "卒", 4, 2, 1, 1),
    )

    /** 齐头并进（最优 47 步） */
    private val qiTou = listOf(
        b(0, CAO, 0, 1, 2, 2),
        b(1, "张飞", 0, 0, 1, 2),
        b(2, "赵云", 0, 3, 1, 2),
        b(3, "关羽", 2, 0, 2, 1),
        b(4, "马超", 2, 2, 1, 2),
        b(5, "黄忠", 3, 3, 1, 1),
        b(6, "卒", 3, 0, 1, 1),
        b(7, "卒", 3, 1, 1, 1),
        b(8, "卒", 4, 0, 1, 1),
        b(9, "卒", 4, 3, 1, 1),
    )

    /** 指挥若定（最优 100 步） */
    private val zhiHui = listOf(
        b(0, CAO, 0, 1, 2, 2),
        b(1, "张飞", 0, 0, 1, 2),
        b(2, "赵云", 0, 3, 1, 2),
        b(3, "马超", 3, 0, 1, 2),
        b(4, "黄忠", 3, 3, 1, 2),
        b(5, "关羽", 2, 1, 2, 1),
        b(6, "卒", 2, 0, 1, 1),
        b(7, "卒", 2, 3, 1, 1),
        b(8, "卒", 3, 1, 1, 1),
        b(9, "卒", 3, 2, 1, 1),
    )

    /** 层层设防（最优 100 步） */
    private val cengCeng = listOf(
        b(0, CAO, 0, 1, 2, 2),
        b(1, "张飞", 0, 0, 1, 2),
        b(2, "赵云", 0, 3, 1, 2),
        b(3, "关羽", 2, 1, 2, 1),
        b(4, "马超", 3, 0, 1, 2),
        b(5, "黄忠", 3, 3, 1, 2),
        b(6, "卒", 2, 0, 1, 1),
        b(7, "卒", 2, 3, 1, 1),
        b(8, "卒", 3, 1, 1, 1),
        b(9, "卒", 3, 2, 1, 1),
    )

    /** 横刀立马（最经典，最优 116 步） */
    private val hengDao = listOf(
        b(0, CAO, 0, 1, 2, 2),
        b(1, "张飞", 0, 0, 1, 2),
        b(2, "赵云", 0, 3, 1, 2),
        b(3, "马超", 2, 0, 1, 2),
        b(4, "黄忠", 2, 3, 1, 2),
        b(5, "关羽", 2, 1, 2, 1),
        b(6, "卒", 3, 1, 1, 1),
        b(7, "卒", 3, 2, 1, 1),
        b(8, "卒", 4, 0, 1, 1),
        b(9, "卒", 4, 3, 1, 1),
    )

    /** 兵分三路（最优 119 步） */
    private val bingFen = listOf(
        b(0, CAO, 0, 1, 2, 2),
        b(1, "张飞", 0, 0, 1, 2),
        b(2, "赵云", 0, 3, 1, 2),
        b(3, "马超", 2, 0, 1, 2),
        b(4, "黄忠", 2, 3, 1, 2),
        b(5, "关羽", 3, 1, 2, 1),
        b(6, "卒", 4, 0, 1, 1),
        b(7, "卒", 4, 1, 1, 1),
        b(8, "卒", 4, 2, 1, 1),
        b(9, "卒", 4, 3, 1, 1),
    )

    /** 按难度（最优步数）从易到难排列 */
    val all = listOf(
        KlotskiLevel("jiangyongcaoying", "将拥曹营", 24, jiangYong),
        KlotskiLevel("qitoubingjin", "齐头并进", 47, qiTou),
        KlotskiLevel("zhihuiruoding", "指挥若定", 100, zhiHui),
        KlotskiLevel("cengcengshefang", "层层设防", 100, cengCeng),
        KlotskiLevel("hengdaolima", "横刀立马", 116, hengDao),
        KlotskiLevel("bingfensanlu", "兵分三路", 119, bingFen),
    )

    const val CAO_NAME = CAO
    const val COLS = 4
    const val ROWS = 5
    /** 曹操需抵达的位置（左上角坐标） */
    const val EXIT_ROW = 3
    const val EXIT_COL = 1
}

/**
 * 华容道核心逻辑：单步移动 + 占位碰撞检测，曹操滑到底部中央即胜。
 * UI 通过读取 version 触发重组（项目统一约定）。
 */
class KlotskiGame(val level: KlotskiLevel) {

    var version by mutableIntStateOf(0)
        private set

    private val blocks = level.blocks.map { it.copy() }.toMutableList()

    /** 已走步数 */
    var moves by mutableIntStateOf(0)
        private set

    /** 选中的棋子（点选后可用方向键移动） */
    var selectedId by mutableIntStateOf(-1)
        private set

    val solved: Boolean
        get() = blocks.any {
            it.name == KlotskiLevels.CAO_NAME &&
                it.row == KlotskiLevels.EXIT_ROW && it.col == KlotskiLevels.EXIT_COL
        }

    fun snapshot(): List<KBlock> = blocks

    fun blockAt(row: Int, col: Int): KBlock? =
        blocks.firstOrNull { row >= it.row && row < it.row + it.h && col >= it.col && col < it.col + it.w }

    fun select(id: Int) {
        selectedId = if (selectedId == id) -1 else id
        version++
    }

    /** 取消选中（点击棋盘空白处） */
    fun clearSelection() {
        if (selectedId == -1) return
        selectedId = -1
        version++
    }

    private fun occupied(ignoreId: Int, row: Int, col: Int, w: Int, h: Int): Boolean {
        if (row < 0 || col < 0) return true
        if (row + h > KlotskiLevels.ROWS || col + w > KlotskiLevels.COLS) return true
        return blocks.any { o ->
            o.id != ignoreId &&
                row < o.row + o.h && o.row < row + h &&
                col < o.col + o.w && o.col < col + w
        }
    }

    fun canMove(id: Int, dr: Int, dc: Int): Boolean {
        val b = blocks.firstOrNull { it.id == id } ?: return false
        return !occupied(id, b.row + dr, b.col + dc, b.w, b.h)
    }

    /** 移动一步；成功返回 true */
    fun move(id: Int, dr: Int, dc: Int): Boolean {
        val idx = blocks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val b = blocks[idx]
        if (!canMove(id, dr, dc)) return false
        blocks[idx] = b.copy(row = b.row + dr, col = b.col + dc)
        moves++
        selectedId = id
        version++
        return true
    }

    fun reset() {
        val fresh = level.blocks.map { it.copy() }
        blocks.clear()
        blocks.addAll(fresh)
        moves = 0
        selectedId = -1
        version++
    }
}
