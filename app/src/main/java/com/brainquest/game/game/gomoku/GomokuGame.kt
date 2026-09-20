package com.brainquest.game.game.gomoku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 五子棋（人机对战）：玩家执黑先手，AI 执白。
 * AI 为本地启发式评估（棋型打分 + 攻守加权，困难档加 2 层前瞻），完全离线。
 */
class GomokuGame(val size: Int = 15, var difficulty: Int = 1) {

    var version by mutableIntStateOf(0)
        private set

    private val board = Array(size) { IntArray(size) }   // 0 空 / 1 玩家(黑) / 2 AI(白)
    private val history = mutableListOf<Pair<Int, Int>>()

    var moves by mutableIntStateOf(0)
        private set
    var winner by mutableIntStateOf(0)                    // 0 未分 / 1 玩家胜 / 2 AI 胜
        private set
    var winLine by mutableStateOf<List<Pair<Int, Int>>>(emptyList())
        private set
    var lastMove by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var thinking by mutableStateOf(false)
        private set

    fun cell(r: Int, c: Int): Int = board[r][c]

    val finished: Boolean get() = winner != 0
    /** 轮到玩家（黑）落子 */
    val playerTurn: Boolean get() = winner == 0 && moves % 2 == 0

    fun playerPlace(r: Int, c: Int): Boolean =
        if (playerTurn) place(r, c, 1) else false

    private fun place(r: Int, c: Int, who: Int): Boolean {
        if (winner != 0 || r !in 0 until size || c !in 0 until size || board[r][c] != 0) return false
        board[r][c] = who
        history.add(r to c)
        moves++
        lastMove = r to c
        val line = winLineThrough(r, c, who)
        if (line != null) {
            winner = who
            winLine = line
        }
        version++
        return true
    }

    /** AI 走一步（界面在延时后调用，避免瞬间落子太突兀） */
    fun aiTurn() {
        if (winner != 0 || moves % 2 == 0) {
            thinking = false
            return
        }
        val mv = chooseAiMove()
        thinking = false
        if (mv != null) place(mv.first, mv.second, 2)
        version++
    }

    /** 界面在延时等待 AI 落子前调用：显示"思考中"，期间悔棋可取消这次思考 */
    fun beginThinking() {
        if (winner != 0 || moves % 2 == 0 || thinking) return
        thinking = true
        version++
    }

    /** 悔棋：回到玩家上一手落子之前（电脑应的那手一并撤销），撤完必轮到玩家 */
    fun undo() {
        if (history.isEmpty()) return
        // 偶数手 = 电脑刚应过，连它那手一起撤；奇数手 = 我那手还没被应，只撤一手
        var steps = if (moves % 2 == 0) 2 else 1
        while (steps > 0 && history.isNotEmpty()) {
            val (r, c) = history.removeAt(history.size - 1)
            board[r][c] = 0
            moves--
            steps--
        }
        winner = 0
        winLine = emptyList()
        lastMove = history.lastOrNull()
        thinking = false
        version++
    }

    fun reset() {
        for (r in 0 until size) for (c in 0 until size) board[r][c] = 0
        history.clear()
        moves = 0
        winner = 0
        winLine = emptyList()
        lastMove = null
        thinking = false
        version++
    }

    // ---------- 胜负判定 ----------

    private val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    private fun winLineThrough(r: Int, c: Int, who: Int): List<Pair<Int, Int>>? {
        for ((dr, dc) in dirs) {
            val line = mutableListOf(r to c)
            var rr = r + dr; var cc = c + dc
            while (rr in 0 until size && cc in 0 until size && board[rr][cc] == who) {
                line.add(rr to cc); rr += dr; cc += dc
            }
            rr = r - dr; cc = c - dc
            while (rr in 0 until size && cc in 0 until size && board[rr][cc] == who) {
                line.add(0, rr to cc); rr -= dr; cc -= dc
            }
            if (line.size >= 5) return line
        }
        return null
    }

    // ---------- AI ----------

    /** 候选点：已有棋子 2 格邻域内（首个落子取天元） */
    private fun candidates(): List<Pair<Int, Int>> {
        val any = history.isNotEmpty()
        if (!any) return listOf(size / 2 to size / 2)
        val out = LinkedHashSet<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (board[r][c] != 0) continue
                var near = false
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val rr = r + dr; val cc = c + dc
                        if (rr in 0 until size && cc in 0 until size && board[rr][cc] != 0) { near = true; break }
                    }
                    if (near) break
                }
                if (near) out.add(r to c)
            }
        }
        return out.toList()
    }

    /** 在 (r,c) 落 who 子的棋型分（四方向累加） */
    private fun scoreAt(r: Int, c: Int, who: Int): Int {
        var total = 0
        for ((dr, dc) in dirs) {
            var count = 1
            var open = 0
            var rr = r + dr; var cc = c + dc
            while (rr in 0 until size && cc in 0 until size && board[rr][cc] == who) {
                count++; rr += dr; cc += dc
            }
            if (rr in 0 until size && cc in 0 until size && board[rr][cc] == 0) open++
            rr = r - dr; cc = c - dc
            while (rr in 0 until size && cc in 0 until size && board[rr][cc] == who) {
                count++; rr -= dr; cc -= dc
            }
            if (rr in 0 until size && cc in 0 until size && board[rr][cc] == 0) open++
            total += patternValue(count, open)
        }
        return total
    }

    private fun patternValue(count: Int, open: Int): Int = when {
        count >= 5 -> 1_000_000
        count == 4 && open == 2 -> 100_000
        count == 4 && open == 1 -> 10_000
        count == 3 && open == 2 -> 8_000
        count == 3 && open == 1 -> 800
        count == 2 && open == 2 -> 500
        count == 2 && open == 1 -> 100
        count == 1 && open == 2 -> 20
        else -> 5
    }

    private fun chooseAiMove(): Pair<Int, Int>? {
        val cands = candidates()
        if (cands.isEmpty()) return null

        data class Scored(val rc: Pair<Int, Int>, val atk: Int, val def: Int)
        val scored = cands.map { (r, c) -> Scored(r to c, scoreAt(r, c, 2), scoreAt(r, c, 1)) }

        // 1) 自己能成五 → 直接赢
        scored.firstOrNull { it.atk >= 1_000_000 }?.let { return it.rc }
        // 2) 对手能成五 → 必须堵
        scored.filter { it.def >= 1_000_000 }.maxByOrNull { it.def }?.let { return it.rc }

        val (attackW, defendW) = when (difficulty) {
            0 -> 1.0 to 0.6      // 简单：偏进攻，容易漏防
            1 -> 1.1 to 1.0      // 普通：攻守均衡
            else -> 1.1 to 1.0   // 困难：均衡 + 前瞻
        }
        val ranked = scored.sortedByDescending { it.atk * attackW + it.def * defendW }

        if (difficulty == 0 && ranked.size > 1) {
            // 简单档：前 3 名里随机，制造失误空间
            return ranked.take(3).random().rc
        }
        if (difficulty < 2) return ranked.first().rc

        // 困难档：对前 5 手做 2 层前瞻，选让对手最佳回应最差的一手
        var best = ranked.first()
        var bestRisk = Int.MAX_VALUE
        for (cand in ranked.take(5)) {
            val (r, c) = cand.rc
            board[r][c] = 2
            var oppBest = 0
            for ((rr, cc) in candidates()) {
                if (board[rr][cc] != 0) continue
                val s = scoreAt(rr, cc, 1)
                if (s > oppBest) oppBest = s
                if (oppBest >= 1_000_000) break
            }
            board[r][c] = 0
            val risk = oppBest - cand.atk / 50
            if (risk < bestRisk) {
                bestRisk = risk
                best = cand
            }
        }
        return best.rc
    }
}
