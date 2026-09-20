package com.brainquest.game.game.snake

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 贪吃蛇核心逻辑：网格前进、吃食物变长、撞墙/咬到自己结束；分数越高速度越快 */
class SnakeGame(val cols: Int = 15, val rows: Int = 20) {

    var version by mutableIntStateOf(0)
        private set

    private val body = ArrayDeque<Pair<Int, Int>>()   // 头部在前

    var dir by mutableStateOf(0 to 1)                 // 当前方向（行, 列）
        private set
    private var pendingDir = 0 to 1

    var food by mutableStateOf(0 to 0)
        private set
    var score by mutableIntStateOf(0)
        private set
    var gameOver by mutableStateOf(false)
        private set

    /** 首次操作后才开始前进（给玩家准备时间） */
    var started by mutableStateOf(false)
        private set

    init {
        reset()
    }

    fun reset() {
        body.clear()
        val mid = rows / 2
        body.add(mid to cols / 2)
        body.add(mid to cols / 2 - 1)
        body.add(mid to cols / 2 - 2)
        dir = 0 to 1
        pendingDir = dir
        score = 0
        gameOver = false
        started = false
        spawnFood()
        version++
    }

    val snake: List<Pair<Int, Int>> get() = body.toList()

    /** 转向（禁止 180° 掉头；按当前方向键可用于起步） */
    fun turn(dr: Int, dc: Int) {
        if (gameOver) return
        if (dr == -dir.first && dc == -dir.second) return
        started = true
        if (dr == dir.first && dc == dir.second) {
            version++
            return
        }
        pendingDir = dr to dc
        version++
    }

    /** 前进一步（由界面 tick 调用） */
    fun step() {
        if (gameOver || !started) return
        dir = pendingDir
        val (hr, hc) = body.first()
        val nr = hr + dir.first
        val nc = hc + dir.second
        if (nr !in 0 until rows || nc !in 0 until cols) {
            gameOver = true; version++; return
        }
        if (body.contains(nr to nc)) {
            gameOver = true; version++; return
        }
        body.addFirst(nr to nc)
        if (nr to nc == food) {
            score++
            spawnFood()
        } else {
            body.removeLast()
        }
        version++
    }

    private fun spawnFood() {
        val free = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!body.contains(r to c)) free.add(r to c)
            }
        }
        food = if (free.isEmpty()) 0 to 0 else free.random()
    }

    /** 当前速度（毫秒/格）：起步慢一些，随分数缓慢加快 */
    val tickMs: Long get() = (300 - score * 6).coerceAtLeast(130).toLong()

    /** 速度档（给 HUD 显示，让加速可感知） */
    val speedLabel: String get() = when {
        tickMs >= 260 -> "慢"
        tickMs >= 200 -> "中"
        else -> "快"
    }
}
