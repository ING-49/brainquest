package com.brainquest.game.game.memory

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class MemorySetsFile(val sets: List<MemorySet> = emptyList())

/** 翻牌知识对数据加载（assets/pairs/memory_sets.json，支持热更包覆盖） */
object MemoryData {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadSets(context: Context): List<MemorySet> {
        val packFile = java.io.File(context.filesDir, "content/pairs/memory_sets.json")
        if (packFile.isFile) {
            runCatching {
                return json.decodeFromString<MemorySetsFile>(packFile.readText()).sets
            }
        }
        return runCatching {
            val text = context.assets.open("pairs/memory_sets.json").bufferedReader().use { it.readText() }
            json.decodeFromString<MemorySetsFile>(text).sets
        }.getOrDefault(emptyList())
    }
}
