package com.brainquest.game.data

/**
 * 每日任务：每天（日期变化时）自动重置进度与领取状态。
 * 进度键与目标/奖励集中在此，UI 与 AppViewModel 共用。
 */
object DailyTasks {
    data class Def(val id: String, val emoji: String, val desc: String, val goal: Int, val reward: Int)

    val all = listOf(
        Def("solve10", "🎯", "答对 10 道题", 10, 30),
        Def("klotski1", "🧱", "完成一局华容道", 1, 40),
        Def("review3", "📖", "复习答对 3 道错题", 3, 30),
    )

    fun byId(id: String): Def? = all.find { it.id == id }
}
