package com.brainquest.game.game.dungeon

import android.content.Context
import android.media.MediaPlayer

/**
 * 地牢 BGM：三态循环配乐（探索/战斗/Boss），MediaPlayer 循环播放自合成 WAV（批11 体验层）。
 * 状态不变时不做任何事（每帧调 update 也零开销）；切状态 = release 旧实例再 create（create 有解码延迟，低频切换可接受）。
 * 跟随玩家声音开关；离开界面必须 stop()（DungeonScreen onDispose）。
 */
object DungeonBgm {

    private var mp: MediaPlayer? = null
    private var state = ""

    fun update(context: Context, enabled: Boolean, newState: String, res: Int?) {
        if (!enabled || res == null) { stop(); return }
        if (state == newState) return
        state = newState
        mp?.release()
        mp = try {
            MediaPlayer.create(context, res)?.apply {
                isLooping = true
                setVolume(0.32f, 0.32f)
                start()
            }
        } catch (_: Exception) { null }
    }

    fun stop() {
        state = ""
        mp?.release()
        mp = null
    }
}
