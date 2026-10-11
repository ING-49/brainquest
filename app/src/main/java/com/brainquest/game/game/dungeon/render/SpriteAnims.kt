package com.brainquest.game.game.dungeon.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 0x72 Dungeon Tileset II 逐帧动画（CC0；帧源 assets/sprites/0x72/，每角色 idle/run 4 帧 + knight hit 4 帧）。
 * 批17：敌人/玩家全部 sprite 化（真帧动画，替代 Kenney 静态贴图样例）。
 * 懒加载；单个帧缺失不致命（该帧用 f0 顶替）。
 */
object SpriteAnims {

    private val cache = HashMap<String, List<ImageBitmap>>()
    var ready = false; private set

    // 敌人 kind → 精灵名（CombatEngine.EnemyKind 映射）
    val KIND_NAMES = mapOf(
        "SLIME" to "swampy",
        "SKELETON" to "skelet",
        "BONE_ARCHER" to "skelet",
        "BAT" to "imp",
        "CASTER" to "necromancer",
        "SHIELD_GUARD" to "ogre",
        "BOOM_SLIME" to "wogol",
    )

    fun load(assets: android.content.res.AssetManager) {
        if (ready) return
        val names = KIND_NAMES.values + listOf("big_demon", "chort", "big_zombie", "knight_m", "wizzard_m", "elf_m")
        var ok = 0
        for (n in names) for (anim in listOf("idle", "run")) for (f in 0..3) {
            val key = "${n}_$anim"
            try {
                assets.open("sprites/0x72/${key}_anim_f$f.png").use { ins ->
                    cache.getOrPut(key) { mutableListOf() }
                    (cache[key] as MutableList).add(BitmapFactory.decodeStream(ins).asImageBitmap())
                    ok++
                }
            } catch (_: Exception) { }
        }
        // 职业帧：mage/ranger 的 hit（knight_m_hit 已单独处理）
        for (clsN in listOf("wizzard_m", "elf_m")) for (f in 0..3) {
            try {
                assets.open("sprites/0x72/" + clsN + "_hit_anim_f" + f + ".png").use { ins ->
                    cache.getOrPut(clsN + "_hit") { mutableListOf() }
                    (cache[clsN + "_hit"] as MutableList).add(BitmapFactory.decodeStream(ins).asImageBitmap())
                }
            } catch (_: Exception) { }
        }
        // knight hit 帧
        for (f in 0..3) {
            try {
                assets.open("sprites/0x72/knight_m_hit_anim_f$f.png").use { ins ->
                    cache.getOrPut("knight_m_hit") { mutableListOf() }
                    (cache["knight_m_hit"] as MutableList).add(BitmapFactory.decodeStream(ins).asImageBitmap())
                }
            } catch (_: Exception) { }
        }
        ready = ok > 40
        android.util.Log.d("SPRITE", "anims ready=$ready frames=$ok")
    }

    fun frames(name: String, run: Boolean): List<ImageBitmap> =
        cache["${name}_${if (run) "run" else "idle"}"] ?: cache["${name}_idle"] ?: emptyList()

    fun knightHit(): List<ImageBitmap> = cache["knight_m_hit"] ?: emptyList()

    /** 职业专属精灵名（批17：knight_m/wizzard_m/elf_m） */
    fun classSprite(clsId: String) = when (clsId) {
        "mage" -> "wizzard_m"
        "ranger" -> "elf_m"
        else -> "knight_m"
    }

    fun classHit(clsId: String): List<ImageBitmap> = cache[classSprite(clsId) + "_hit"] ?: emptyList()

    fun firstSize(frames: List<ImageBitmap>): Pair<Int, Int> =
        if (frames.isEmpty()) 16 to 16 else Pair(frames[0].width, frames[0].height)
}
