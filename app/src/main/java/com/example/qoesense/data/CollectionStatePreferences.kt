package com.example.qoesense.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "collection_state_prefs")

/**
 * Whether background collection is meant to be running, independent of whether the service
 * process happens to be alive right now. [com.example.qoesense.service.BootReceiver] reads
 * this to decide whether to restart the foreground service after a reboot.
 */
class CollectionStatePreferences(private val context: Context) {

    private object Keys {
        val COLLECTION_ENABLED = booleanPreferencesKey("collection_enabled")
        val LAST_UPLOAD_TIMESTAMP_MS = longPreferencesKey("last_upload_timestamp_ms")
    }

    val isCollectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.COLLECTION_ENABLED] ?: false }

    suspend fun setCollectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.COLLECTION_ENABLED] = enabled }
    }

    val lastUploadTimestampMs: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[Keys.LAST_UPLOAD_TIMESTAMP_MS] }

    suspend fun setLastUploadTimestamp(timestampMs: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_UPLOAD_TIMESTAMP_MS] = timestampMs }
    }
}
