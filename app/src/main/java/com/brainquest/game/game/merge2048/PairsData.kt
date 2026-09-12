package com.brainquest.game.game.merge2048

import android.content.Context
import kotlinx.serialization.json.Json

/** 2048 配对数据加载（assets/pairs/merge_<mode>.json，支持热更包覆盖） */
object PairsData {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, mode: String): PairTiers? {
        // 优先读热更包目录，读不到回退 assets
        val packFile = java.io.File(context.filesDir, "content/pairs/merge_$mode.json")
        if (packFile.isFile) {
            runCatching { return json.decodeFromString<PairTiers>(packFile.readText()) }
        }
        return runCatching {
            val text = context.assets.open("pairs/merge_$mode.json").bufferedReader().use { it.readText() }
            json.decodeFromString<PairTiers>(text)
        }.getOrNull()
    }

    val modes = listOf("english" to "英语单词", "math" to "速算配对")
}
