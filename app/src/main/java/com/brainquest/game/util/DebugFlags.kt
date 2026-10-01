package com.brainquest.game.util

/** 调试开关（仅自动化验收用；正常启动恒为 false，不进入任何玩法路径） */
object DebugFlags {
    @Volatile var autopilot: Boolean = false
}
