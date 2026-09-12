package com.brainquest.game.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playerDataStore by preferencesDataStore(name = "brainquest_player")

/** 玩家存档：DataStore 单键 JSON 持久化 */
class SaveStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("player_json")

    val playerFlow: Flow<PlayerState?> = context.playerDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<PlayerState>(it) }.getOrNull() }
    }

    suspend fun load(): PlayerState {
        val stored = playerFlow.first()
        return stored ?: PlayerState()
    }

    suspend fun save(state: PlayerState) {
        context.playerDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(state)
        }
    }
}
