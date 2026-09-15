package com.resonance.music.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The queue song ids (in order), current index, and position last saved by
 *  [SettingsStore.savePlaybackState] — used to resume playback after the
 *  service was killed and a new controller (Android Auto, the notification)
 *  reconnects with nothing loaded. */
data class PersistedPlaybackState(
    val songIds: List<String>,
    val index: Int,
    val positionMs: Long
)

/** App preferences shared between Settings UI and playback (scrobbling, resumption). */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GAPLESS = booleanPreferencesKey("gapless_playback")
        val SCROBBLE = booleanPreferencesKey("scrobble_enabled")
        val QUEUE_SONG_IDS = stringPreferencesKey("queue_song_ids")
        val QUEUE_INDEX = intPreferencesKey("queue_index")
        val QUEUE_POSITION_MS = longPreferencesKey("queue_position_ms")
    }

    val gaplessPlayback: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.GAPLESS] ?: true }
    val scrobbleEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.SCROBBLE] ?: true }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GAPLESS] = enabled }
    }

    suspend fun setScrobbleEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SCROBBLE] = enabled }
    }

    suspend fun savePlaybackState(songIds: List<String>, index: Int, positionMs: Long) {
        context.settingsDataStore.edit {
            it[Keys.QUEUE_SONG_IDS] = songIds.joinToString(",")
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION_MS] = positionMs
        }
    }

    suspend fun getPlaybackState(): PersistedPlaybackState? {
        val prefs = context.settingsDataStore.data.first()
        val ids = prefs[Keys.QUEUE_SONG_IDS]?.split(",")?.filter { it.isNotBlank() }
        if (ids.isNullOrEmpty()) return null
        return PersistedPlaybackState(
            songIds = ids,
            index = prefs[Keys.QUEUE_INDEX] ?: 0,
            positionMs = prefs[Keys.QUEUE_POSITION_MS] ?: 0L
        )
    }
}
