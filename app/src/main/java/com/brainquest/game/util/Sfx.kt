package com.brainquest.game.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class SfxType { CORRECT, WRONG, CLICK, WIN, LOSE, MERGE, FLIP }

/** 轻量音效（ToneGenerator，无需音频资源）+ 震动反馈 */
object Sfx {

    fun play(context: Context, enabled: Boolean, type: SfxType) {
        if (!enabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            val params = when (type) {
                SfxType.CORRECT -> SfxParams(ToneGenerator.TONE_PROP_BEEP, 120)
                SfxType.WRONG -> SfxParams(ToneGenerator.TONE_PROP_NACK, 200)
                SfxType.CLICK -> SfxParams(ToneGenerator.TONE_PROP_ACK, 60)
                SfxType.WIN -> SfxParams(ToneGenerator.TONE_CDMA_CONFIRM, 350)
                SfxType.LOSE -> SfxParams(ToneGenerator.TONE_SUP_ERROR, 300)
                SfxType.MERGE -> SfxParams(ToneGenerator.TONE_PROP_BEEP2, 100)
                SfxType.FLIP -> SfxParams(ToneGenerator.TONE_PROP_ACK, 40)
            }
            tone.startTone(params.tone, params.durationMs)
            android.os.Handler(context.mainLooper).postDelayed({ tone.release() }, params.durationMs + 200L)
        }
    }

    private data class SfxParams(val tone: Int, val durationMs: Int)

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
