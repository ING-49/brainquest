package com.brainquest.game.game.dungeon

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.brainquest.game.R

/**
 * 地牢音效：SoundPool 播放自合成 WAV（res/raw，共 164KB）。
 * 纪律：同类音效节流（默认 90ms），命中类高频事件不叠音爆。
 * UI 点击音仍走 util/Sfx（ToneGenerator）。
 */
object DungeonSfx {

    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()          // resId -> soundId
    private val ready = HashSet<Int>()             // 已加载的 soundId
    private val lastPlay = HashMap<Int, Long>()    // resId -> 上次播放时刻

    private fun ensure(context: Context): SoundPool? {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready.add(sampleId) }
        val list = listOf(
            R.raw.dg_hit, R.raw.dg_crit, R.raw.dg_shoot, R.raw.dg_hurt, R.raw.dg_pickup,
            R.raw.dg_levelup, R.raw.dg_skill, R.raw.dg_boss, R.raw.dg_victory, R.raw.dg_lose,
            R.raw.dg_whoosh,
        )
        for (res in list) ids[res] = p.load(context, res, 1)
        pool = p
        return p
    }

    fun play(context: Context, enabled: Boolean, res: Int, volume: Float = 0.6f, throttleMs: Long = 90) {
        if (!enabled) return
        val p = ensure(context) ?: return
        val sid = ids[res] ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (lastPlay[res] ?: 0L) < throttleMs) return
        lastPlay[res] = now
        if (sid !in ready) return   // 尚未加载完，跳过本次
        p.play(sid, volume, volume, 1, 0, 1f)
    }
}
