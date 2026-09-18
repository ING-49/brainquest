package com.brainquest.game.game.quizbattle

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.brainquest.game.data.GenRulesConfig
import com.brainquest.game.data.question.Question
import com.brainquest.game.data.question.QuestionBank
import com.brainquest.game.data.question.Subjects
import kotlin.random.Random

data class Enemy(
    val name: String,
    val emoji: String,
    val maxHp: Int,
    val isBoss: Boolean,
)

/** 速算英雄：回合答题战斗。字段使用 Compose 快照状态以驱动 UI 刷新 */
class BattleState(
    val subject: String,
    val level: Int,
    private val bank: QuestionBank,
    private val rng: Random = Random.Default,
) {
    // 已出题跟踪（去重）与错题重出队列
    private val usedIds = mutableSetOf<String>()
    private val usedTexts = mutableSetOf<String>()
    private val reAskedIds = mutableSetOf<String>()   // 重出过的题不再重出
    private var reAskPending: Question? = null        // 答错的题，低概率再出一次

    val isBoss: Boolean = level >= 8
    val enemy: Enemy

    var playerHp by mutableIntStateOf(100)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var maxCombo by mutableIntStateOf(0)
        private set
    var mistakes by mutableIntStateOf(0)
        private set
    var answered by mutableIntStateOf(0)
        private set
    var correctCount by mutableIntStateOf(0)
        private set
    var questionIndex by mutableIntStateOf(0)
        private set
    var question by mutableStateOf<Question?>(null)
        private set
    var usedHints by mutableIntStateOf(0)
        private set
    var enemyHp by mutableIntStateOf(0)
        private set

    // 被提示排除的选项下标（快照列表）
    val eliminated = mutableStateListOf<Int>()

    val questionDifficulty: Int = (level / 2 + 1).coerceIn(1, 5)
    val timeLimitSec: Int = if (isBoss) 12 else (20 - level).coerceAtLeast(10)

    private val enemyNames = listOf("哥布林", "史莱姆", "小恶魔", "蝙蝠兵", "石头人", "暗影法师", "骷髅武士")
    private val bossNames = mapOf(
        Subjects.MATH to "魔王·拉格朗日",
        Subjects.LOGIC to "谜题领主·斯芬克斯",
        Subjects.ENGLISH to "词汇巨龙·巴别塔",
        Subjects.SCIENCE to "混沌博士·熵",
        Subjects.CODING to "Bug之主·零day",
        Subjects.ADV_MATH to "ε-δ 恶魔",
        Subjects.LIN_ALG to "奇异矩阵幽灵",
        Subjects.PROBABILITY to "随机预言者",
        Subjects.RF_CIRCUITS to "谐振魔王·Q",
        Subjects.COMMUNICATION to "噪声之王·香农",
    )

    init {
        enemy = if (isBoss) {
            Enemy(bossNames[subject] ?: "知识魔王", "👹", 260 + level * 20, true)
        } else {
            val name = enemyNames[(level - 1) % enemyNames.size]
            val prefix = when (subject) {
                Subjects.MATH -> "算术"
                Subjects.LOGIC -> "谜题"
                Subjects.ENGLISH -> "单词"
                Subjects.SCIENCE -> "元素"
                Subjects.CODING -> "字节"
                Subjects.ADV_MATH -> "微积分"
                Subjects.LIN_ALG -> "矩阵"
                Subjects.PROBABILITY -> "概率"
                Subjects.RF_CIRCUITS -> "射频"
                Subjects.COMMUNICATION -> "信号"
                else -> "知识"
            }
            val emoji = when (level % 4) {
                0 -> "👺"
                1 -> "🧟"
                2 -> "🦇"
                else -> "👾"
            }
            Enemy("$prefix${name}·Lv$level", emoji, 55 + level * 22, false)
        }
        enemyHp = enemy.maxHp
        nextQuestion()
        question?.let { usedIds.add(it.id); usedTexts.add(it.question) }
    }

    val enemyHpMax: Int get() = enemy.maxHp

    /** 进入下一题 */
    fun nextQuestion() {
        questionIndex++
        eliminated.clear()
        // 小概率插入刚才答错的题（最多重出一次）
        val pending = reAskPending
        if (pending != null && rng.nextInt(100) < 20) {
            reAskPending = null
            question = pending
            return
        }
        // 正常出题：排除已用
        var q = bank.pickExcluding(subject, questionDifficulty, usedIds, usedTexts, rng)
        if (q == null) q = bank.pick(subject, questionDifficulty)  // 池子耗尽才允许重复
        question = q
        q?.let { usedIds.add(it.id); usedTexts.add(it.question) }
    }

    /** 答题结算，返回伤害事件（正数=对敌伤害，负数=自身受伤） */
    fun answer(chosen: Int): Int {
        val q = question ?: return 0
        answered++
        eliminated.clear()
        return if (chosen == q.answer) {
            correctCount++
            combo++
            if (combo > maxCombo) maxCombo = combo
            val dmg = with(GenRulesConfig.current) { baseDamage + level * perLevelDamage + combo * comboDamage }
            enemyHp = (enemyHp - dmg).coerceAtLeast(0)
            dmg
        } else {
            combo = 0
            mistakes++
            val atk = enemyAttack()
            playerHp = (playerHp - atk).coerceAtLeast(0)
            // 答错的题 25% 概率安排重出一次（已重出过的不重复安排）
            if (reAskPending == null && q.id !in reAskedIds && rng.nextInt(100) < 25) {
                reAskPending = q
                reAskedIds.add(q.id)
            }
            -atk
        }
    }

    /** 超时按答错处理 */
    fun timeout(): Int = answer(-1)

    /** 填空题作答：文本归一化后与答案比较 */
    fun answerFill(input: String): Int {
        val q = question ?: return 0
        val expected = q.options.firstOrNull()?.trim() ?: return 0
        val ok = normalizeNum(input) == normalizeNum(expected)
        return answer(if (ok) q.answer else -1)
    }

    /** 每题时限：填空题多给 10 秒 */
    fun timeLimitFor(q: Question?): Int =
        if (q?.type == "fill") timeLimitSec + GenRulesConfig.current.fillTimeBonus else timeLimitSec

    private fun normalizeNum(s: String): String =
        s.trim().replace("，", "").replace(",", "").removeSuffix("。").let {
            it.toDoubleOrNull()?.let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() } ?: it
        }

    fun enemyAttack(): Int {
        val base = 10 + level * 3
        return if (isBoss) (base * 1.6).toInt() else base
    }

    fun useHint(): Boolean {
        val q = question ?: return false
        val remaining = q.options.indices.filter { it != q.answer && it !in eliminated }
        if (remaining.isEmpty()) return false
        eliminated.add(remaining.random(rng))
        usedHints++
        return true
    }

    fun revive() {
        playerHp = 60
    }

    val victory: Boolean get() = enemyHp <= 0
    val defeated: Boolean get() = playerHp <= 0

    fun stars(): Int = when {
        mistakes == 0 -> 3
        playerHp >= 60 -> 2
        else -> 1
    }

    fun coins(): Int = (15 + level * 8 + stars() * 5) * (if (isBoss) 2 else 1)
    fun xp(): Int = (12 + level * 6) * (if (isBoss) 2 else 1)

    companion object {
        const val LEVEL_COUNT = 8
    }
}
