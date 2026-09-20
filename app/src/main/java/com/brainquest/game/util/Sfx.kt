package com.brainquest.game.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 音效类型。CORRECT/WRONG/CLICK 沿用原有单音（答题手感不变）；
 * 小游戏音色用 DTMF 音阶拼成短音序（滑动/落子/吃豆/撞击/开局/胜负）。
 */
enum class SfxType {
    CORRECT, WRONG, CLICK, WIN, LOSE,
    SELECT, MOVE, PLACE, PLACE_AI, EAT, CRASH, START,
}

/** 轻量音效（ToneGenerator 音序，无需音频资源）+ 震动反馈 */
object Sfx {

    private const val VOLUME = 65

    /** 同类型最小间隔，避免连续滑动时音效叠成一团 */
    private const val THROTTLE_MS = 60L

    private data class Note(val tone: Int, val ms: Int, val gapMs: Int = 40)

    private enum class HapticKind { NONE, LIGHT, STRONG }

    /** 上一次播放时刻（仅主线程访问） */
    private val lastPlayed = HashMap<SfxType, Long>()

    /** 仅音效（兼容旧调用点） */
    fun play(context: Context, enabled: Boolean, type: SfxType) =
        play(context, enabled, false, type)

    /** 音效 + 触感：hapticsOn 对应设置里的「震动反馈」 */
    fun play(context: Context, soundOn: Boolean, hapticsOn: Boolean, type: SfxType) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastPlayed[type] ?: 0L) < THROTTLE_MS) return
        lastPlayed[type] = now

        if (hapticsOn) {
            when (hapticOf(type)) {
                HapticKind.NONE -> Unit
                HapticKind.LIGHT -> haptic(context, true, strong = false)
                HapticKind.STRONG -> haptic(context, true, strong = true)
            }
        }
        if (!soundOn) return

        val notes = notesOf(type)
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            val handler = Handler(context.mainLooper)
            var at = 0L
            for (n in notes) {
                handler.postDelayed({ runCatching { gen.startTone(n.tone, n.ms) } }, at)
                at += (n.ms + n.gapMs).toLong()
            }
            handler.postDelayed({ runCatching { gen.release() } }, at + 150L)
        }
    }

    /** DTMF 音阶提供不同音高（ToneGenerator 内置，不占包体） */
    private fun notesOf(type: SfxType): List<Note> = when (type) {
        SfxType.CORRECT -> listOf(Note(ToneGenerator.TONE_PROP_BEEP, 120))
        SfxType.WRONG -> listOf(Note(ToneGenerator.TONE_PROP_NACK, 200))
        SfxType.CLICK -> listOf(Note(ToneGenerator.TONE_PROP_ACK, 60))
        SfxType.SELECT -> listOf(Note(ToneGenerator.TONE_PROP_BEEP, 40))
        SfxType.MOVE -> listOf(Note(ToneGenerator.TONE_DTMF_4, 45))
        SfxType.PLACE -> listOf(Note(ToneGenerator.TONE_DTMF_5, 60))
        SfxType.PLACE_AI -> listOf(Note(ToneGenerator.TONE_DTMF_2, 60))
        SfxType.EAT -> listOf(
            Note(ToneGenerator.TONE_DTMF_3, 55, 30),
            Note(ToneGenerator.TONE_DTMF_6, 70),
        )
        SfxType.CRASH -> listOf(
            Note(ToneGenerator.TONE_DTMF_9, 80, 20),
            Note(ToneGenerator.TONE_PROP_NACK, 200),
        )
        SfxType.START -> listOf(
            Note(ToneGenerator.TONE_DTMF_1, 70, 30),
            Note(ToneGenerator.TONE_DTMF_2, 70, 30),
            Note(ToneGenerator.TONE_DTMF_3, 110),
        )
        SfxType.WIN -> listOf(
            Note(ToneGenerator.TONE_DTMF_1, 90, 40),
            Note(ToneGenerator.TONE_DTMF_5, 90, 40),
            Note(ToneGenerator.TONE_DTMF_9, 160),
        )
        SfxType.LOSE -> listOf(
            Note(ToneGenerator.TONE_DTMF_9, 120, 40),
            Note(ToneGenerator.TONE_DTMF_6, 120, 40),
            Note(ToneGenerator.TONE_DTMF_3, 240),
        )
    }

    private fun hapticOf(type: SfxType): HapticKind = when (type) {
        SfxType.MOVE, SfxType.SELECT, SfxType.CLICK -> HapticKind.NONE
        SfxType.CRASH, SfxType.WIN, SfxType.LOSE -> HapticKind.STRONG
        else -> HapticKind.LIGHT
    }

    fun haptic(context: Context, enabled: Boolean, strong: Boolean = false) {
        if (!enabled) return
        runCatching {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createOneShot(if (strong) 60L else 25L, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }
    }
}
