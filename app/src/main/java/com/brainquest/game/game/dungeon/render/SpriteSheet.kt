package com.brainquest.game.game.dungeon.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Kenney Tiny Dungeon 精灵图（CC0，assets/sprites/tiny_dungeon.png，12×11 块 16px）。
 * 批16 素材样例：Slime(0,9) / Skeleton→死神(3,9) 两怪先行替换；懒加载，加载失败 ready=false 自动 fallback 矢量绘制。
 */
object SpriteSheet {

    private var cache: ImageBitmap? = null
    var ready = false; private set

    const val TILE = 16
    const val COLS = 12
    const val ROWS = 11

    /** id 标识已加载的素材版本（切设置时不需重载） */
    fun load(assets: android.content.res.AssetManager) {
        if (ready) return
        try {
            assets.open("sprites/tiny_dungeon.png").use { ins ->
                cache = BitmapFactory.decodeStream(ins).asImageBitmap()
            }
            ready = cache != null
            android.util.Log.d("SPRITE", "loaded ok=" + ready)
        } catch (e: Exception) {
            android.util.Log.d("SPRITE", "load failed: " + e.message)
            ready = false
        }
    }

    fun bitmap(): ImageBitmap? = cache
}
