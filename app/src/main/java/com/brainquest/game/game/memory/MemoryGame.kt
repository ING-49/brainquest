package com.brainquest.game.game.memory

import kotlinx.serialization.Serializable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.random.Random

@Serializable
data class MemoryPair(val left: String, val right: String, val note: String = "")

@Serializable
data class MemorySet(val name: String, val pairs: List<MemoryPair>)

/** 记忆翻牌：卡面内容来自知识对的左右两半，翻开两张互为一对即消除 */
class MemoryGame(
    val set: MemorySet,
    val rows: Int,
    val cols: Int,
    private val rng: Random = Random.Default,
) {
    data class Card(val pairIndex: Int, val side: Int) {
        var flipped = false
        var matched = false
    }

    val cards: List<Card>
    var moves = 0
        private set
    var matchedPairs = 0
        private set
    val totalPairs: Int = rows * cols / 2
    val finished: Boolean get() = matchedPairs >= totalPairs

    /** 状态版本号：UI 读取以触发重组 */
    var version by mutableIntStateOf(0)
        private set

    private var firstPick: Int? = null

    init {
        val chosen = set.pairs.shuffled(rng).take(totalPairs)
        val deck = mutableListOf<Card>()
        chosen.forEachIndexed { idx, pair ->
            deck.add(Card(idx, 0))
            deck.add(Card(idx, 1))
        }
        cards = deck.shuffled(rng)
        version++
    }

    fun cardText(index: Int): String {
        val card = cards[index]
        val pair = set.pairs[card.pairIndex]
        return if (card.side == 0) pair.left else pair.right
    }

    fun pairNote(index: Int): String = set.pairs[cards[index].pairIndex].note

    fun isMatched(index: Int): Boolean = cards[index].matched
    fun isFlipped(index: Int): Boolean = cards[index].flipped || cards[index].matched

    /** 点击卡牌；返回 "flip"（翻开第一张）/ "match"（配对成功）/ "miss"（配对失败）/ "none"（无效） */
    fun tap(index: Int): String {
        val card = cards[index]
        if (card.matched || card.flipped) return "none"
        if (firstPick == null) {
            card.flipped = true
            firstPick = index
            version++
            return "flip"
        }
        val first = cards[firstPick!!]
        card.flipped = true
        moves++
        val result = if (first.pairIndex == card.pairIndex && first.side != card.side) {
            first.matched = true
            card.matched = true
            first.flipped = false; card.flipped = false
            matchedPairs++
            firstPick = null
            "match"
        } else {
            "miss"
        }
        version++
        return result
    }

    /** 配错后由界面调用：翻开的两张翻回去 */
    fun resetFlipped() {
        cards.forEach { if (!it.matched) it.flipped = false }
        firstPick = null
        version++
    }

    fun secondCardIndex(): Int = cards.indexOfFirst { it.flipped && it != cards[firstPick ?: -1] }

    /** 得分：步数越少越高 */
    fun stars(): Int = when {
        moves <= totalPairs + 1 -> 3
        moves <= totalPairs * 2 -> 2
        else -> 1
    }
}
