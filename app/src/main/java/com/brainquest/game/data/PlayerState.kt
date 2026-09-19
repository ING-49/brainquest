package com.brainquest.game.data

import kotlinx.serialization.Serializable

/** 错题本条目 */
@Serializable
data class WrongEntry(
    val question: com.brainquest.game.data.question.Question,
    val chosen: Int, // -1 表示超时未答
    val time: Long,
    val mastered: Boolean = false,
    val stage: Int = 0,        // 艾宾浩斯复习阶段 0~5（0=刚答错，5=已掌握）
    val nextReviewAt: Long = 0, // 下次到期复习时间戳（0=立即到期）
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
    val pkWins: Int = 0,           // ⚔️ 联机对战胜利场次
    val pkLosses: Int = 0,         // 联机对战失败场次
    val pkServerUrl: String = "ws://8.148.192.129:8765", // 联机服务器地址（默认公网对战服务器，记忆上次填写）
    val lastGoodSource: String = "", // 最近一次检查更新成功的源（优先复用，利于国内更新）
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
/** 艾宾浩斯间隔（毫秒）：阶段 1→+1天 2→+2天 3→+4天 4→+7天 5→+15天 */
fun reviewIntervalMs(stage: Int): Long = when (stage) {
    1 -> 1L * 24 * 3600 * 1000
    2 -> 2L * 24 * 3600 * 1000
    3 -> 4L * 24 * 3600 * 1000
    4 -> 7L * 24 * 3600 * 1000
    else -> 15L * 24 * 3600 * 1000
}

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
