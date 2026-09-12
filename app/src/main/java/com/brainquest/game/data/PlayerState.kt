package com.brainquest.game.data

import kotlinx.serialization.Serializable

/** 错题本条目 */
@Serializable
data class WrongEntry(
    val question: com.brainquest.game.data.question.Question,
    val chosen: Int, // -1 表示超时未答
    val time: Long,
    val mastered: Boolean = false,
)

/** 每日挑战结果 */
@Serializable
data class DailyResult(
    val correct: Int,
    val total: Int,
    val coins: Int = 0,
)

/** 玩家全部存档状态（DataStore 中以 JSON 持久化） */
@Serializable
data class PlayerState(
    val nickname: String = "小勇者",
    val avatar: String = "🧑‍🎓",
    val coins: Int = 120,
    val xp: Int = 0,
    val streakDays: Int = 0,
    val lastCheckIn: String = "",
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val items: Map<String, Int> = mapOf(Items.ITEM_HINT to 3, Items.ITEM_SKIP to 1, Items.ITEM_REVIVE to 1),
    val ownedThemes: List<String> = listOf("classic"),
    val ownedAvatars: List<String> = listOf("🧑‍🎓", "🐻", "🐱"),
    val activeTheme: String = "classic",
    val levelStars: Map<String, Int> = emptyMap(),
    val bestScores: Map<String, Int> = emptyMap(),
    val achievements: Map<String, Long> = emptyMap(),
    val wrongBook: List<WrongEntry> = emptyList(),
    val dailyResults: Map<String, DailyResult> = emptyMap(),
    val dailyStreak: Int = 0,
    val contentVersions: Map<String, Int> = emptyMap(),
    val updateServerUrl: String = "https://github.com/ING-49/brainquest/releases/latest/download",
    val soundOn: Boolean = true,
    val hapticsOn: Boolean = true,
    val hardMode: Boolean = false, // 🎓 大学考研模式：开启后每日挑战出大学科目高难题
) {
    val totalAnswered: Int get() = totalCorrect + totalWrong
    val accuracy: Float get() = if (totalAnswered == 0) 0f else totalCorrect.toFloat() / totalAnswered
    val levelsCleared: Int get() = levelStars.count { it.value > 0 }
    val totalStars: Int get() = levelStars.values.sum()
}

object Items {
    const val ITEM_HINT = "hint"
    const val ITEM_SKIP = "skip"
    const val ITEM_REVIVE = "revive"

    data class Def(val id: String, val name: String, val emoji: String, val price: Int, val desc: String)

    val all = listOf(
        Def(ITEM_HINT, "智慧提示", "💡", 40, "战斗中排除一个错误选项"),
        Def(ITEM_SKIP, "闪现跳过", "🌀", 60, "跳过当前题目不受伤害"),
        Def(ITEM_REVIVE, "复活药水", "🧪", 120, "战败后原地复活，恢复60%生命"),
    )

    fun byId(id: String): Def = all.first { it.id == id }
}

// ---------- 等级曲线 ----------

/** 升到 lvl 级所需累计经验：2级100，3级300，4级600… */
fun xpForLevel(lvl: Int): Int = 50 * (lvl - 1) * lvl

fun levelForXp(xp: Int): Int {
    var lvl = 1
    while (xp >= xpForLevel(lvl + 1)) lvl++
    return lvl
}

/** 返回当前等级内进度 (当前, 需要) */
fun xpProgress(xp: Int): Pair<Int, Int> {
    val lvl = levelForXp(xp)
    val base = xpForLevel(lvl)
    val next = xpForLevel(lvl + 1)
    return (xp - base) to (next - base)
}
