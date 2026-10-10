package com.brainquest.game.game.dungeon

import android.content.Context
import android.content.SharedPreferences

/**
 * 地牢设置（批12）：SharedPreferences「bg_dungeon」持久化 + 内存缓存，战斗内实时生效。
 * 独立于 PlayerState（不进云存档，纯本地体验偏好）。
 */
object DungeonSettings {

    private var prefs: SharedPreferences? = null

    private fun p(context: Context): SharedPreferences =
        prefs ?: context.getSharedPreferences("bg_dungeon", Context.MODE_PRIVATE).also { prefs = it }

    /** BGM 音量 0-100（默认 32） */
    fun bgmVolume(context: Context): Int = p(context).getInt("bgm_volume", 32)
    fun setBgmVolume(context: Context, v: Int) = p(context).edit().putInt("bgm_volume", v.coerceIn(0, 100)).apply()

    /** 伤害数字飘字（默认开） */
    fun damageNumbers(context: Context): Boolean = p(context).getBoolean("damage_numbers", true)
    fun setDamageNumbers(context: Context, on: Boolean) = p(context).edit().putBoolean("damage_numbers", on).apply()

    /** 震屏强度 0=关 1=弱 2=强（默认强） */
    fun shakeLevel(context: Context): Int = p(context).getInt("shake_level", 2)
    fun setShakeLevel(context: Context, v: Int) = p(context).edit().putInt("shake_level", v.coerceIn(0, 2)).apply()
    fun shakeFactor(context: Context): Float = when (shakeLevel(context)) { 0 -> 0f; 1 -> 0.5f; else -> 1f }

    /** 暴击缩放 punch-zoom（默认开） */
    fun critZoom(context: Context): Boolean = p(context).getBoolean("crit_zoom", true)
    fun setCritZoom(context: Context, on: Boolean) = p(context).edit().putBoolean("crit_zoom", on).apply()

    /** 粒子密度 0=低 1=中 2=高（默认高；低端机降效） */
    fun particleDensity(context: Context): Int = p(context).getInt("particle_density", 2)
    fun setParticleDensity(context: Context, v: Int) = p(context).edit().putInt("particle_density", v.coerceIn(0, 2)).apply()
    fun particleCap(context: Context): Int = when (particleDensity(context)) { 0 -> 120; 1 -> 210; else -> 300 }

    fun resetTutorial(context: Context) = p(context).edit().putBoolean("dg_tutorial_shown", false).apply()
}
