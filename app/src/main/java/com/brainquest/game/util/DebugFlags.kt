package com.brainquest.game.util

/** 调试开关（仅自动化验收用；正常启动恒为 false，不进入任何玩法路径） */
object DebugFlags {
    @Volatile var autopilot: Boolean = false
    /** 上帝模式：不掉血（回归跑全程用，否则 bot 会死在前几层看不全流程） */
    @Volatile var god: Boolean = false
}
