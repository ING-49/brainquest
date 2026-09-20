package com.brainquest.game.data

import com.brainquest.game.data.question.Subjects

/** 成就定义：check 为纯函数条件，玩家状态变化后统一检查 */
data class AchievementDef(
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,
    val reward: Int,
    val check: (PlayerState) -> Boolean,
)

object Achievements {
    val all = listOf(
        AchievementDef("first_win", "首战告捷", "通过第一个关卡", "🎖️", 50) { it.levelsCleared >= 1 },
        AchievementDef("clear_10", "小试牛刀", "累计通过 10 个关卡", "⚔️", 80) { it.levelsCleared >= 10 },
        AchievementDef("clear_30", "身经百战", "累计通过 30 个关卡", "🛡️", 200) { it.levelsCleared >= 30 },
        AchievementDef("correct_50", "百步穿杨", "累计答对 50 题", "🎯", 60) { it.totalCorrect >= 50 },
        AchievementDef("correct_200", "题海无涯", "累计答对 200 题", "🏹", 150) { it.totalCorrect >= 200 },
        AchievementDef("stars_30", "摘星者", "累计获得 30 颗星", "⭐", 120) { it.totalStars >= 30 },
        AchievementDef("streak_3", "三日之约", "每日挑战连续 3 天", "🔥", 80) { it.dailyStreak >= 3 },
        AchievementDef("streak_7", "七日之誓", "每日挑战连续 7 天", "🌟", 200) { it.dailyStreak >= 7 },
        AchievementDef("rich", "富甲一方", "持有金币达到 1000", "💰", 100) { it.coins >= 1000 },
        AchievementDef("level_10", "渐入佳境", "玩家等级达到 10 级", "🚀", 150) { levelForXp(it.xp) >= 10 },
        AchievementDef("wrong_10", "温故知新", "错题本收录 10 道题", "📖", 50) { it.wrongBook.size >= 10 },
        AchievementDef("scholar", "博学多才", "在全部科目都通过关卡", "🎓", 300) {
            val subjects = it.levelStars.keys.map { key -> key.substringBefore("_") }
            Subjects.all.all { s -> subjects.any { k -> k == subjectKey(s) } }
        },
        AchievementDef("klotski_clear", "智取华容", "华容道通关任意一关", "🧱", 80) {
            it.bestScores.entries.any { (k, v) -> k.startsWith("hrd_") && v > 0 }
        },
        AchievementDef("gomoku_win", "棋逢对手", "五子棋战胜电脑", "⚫", 60) { (it.bestScores["gomoku_wins"] ?: 0) >= 1 },
        AchievementDef("gomoku_streak3", "连战连捷", "五子棋连胜 3 场", "🏅", 150) {
            (it.bestScores["gomoku_best_streak"] ?: 0) >= 3
        },
        AchievementDef("snake_30", "蛇行三十", "贪吃蛇得分达到 30", "🐍", 100) { (it.bestScores["snake_best"] ?: 0) >= 30 },
    )

    fun byId(id: String): AchievementDef? = all.find { it.id == id }
}

/** 关卡存档 key：<科目key>_<关卡号> */
fun subjectKey(subject: String): String = when (subject) {
    "英语单词" -> "en"
    "科学百科" -> "sci"
    "编程基础" -> "code"
    "高等数学" -> "adv"
    "线性代数" -> "la"
    "概率论" -> "prob"
    "高频电子线路" -> "rf"
    "通信原理" -> "comm"
    "数学口算" -> "math"
    "逻辑推理" -> "logic"
    else -> subject.take(2)
}

fun subjectFromKey(key: String): String = Subjects.all.firstOrNull { subjectKey(it) == key } ?: key
