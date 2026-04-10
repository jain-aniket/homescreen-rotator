package com.wallpaper.rotator.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaper_rotator_prefs")

class PreferencesManager(private val context: Context) {

    private companion object {
        val ROTATION_INTERVAL = floatPreferencesKey("rotation_interval_hours")
        val ROTATE_ON_UNLOCK = booleanPreferencesKey("rotate_on_unlock")
        val ROTATE_ON_BOOT = booleanPreferencesKey("rotate_on_boot")
        val LAST_ROTATED_INDEX = intPreferencesKey("last_rotated_index")
    }

    val rotationIntervalHours: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[ROTATION_INTERVAL] ?: 6f
    }

    val rotateOnUnlock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ROTATE_ON_UNLOCK] ?: false
    }

    val rotateOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ROTATE_ON_BOOT] ?: false
    }

    val lastRotatedIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_ROTATED_INDEX] ?: 0
    }

    suspend fun setRotationInterval(hours: Float) {
        context.dataStore.edit { it[ROTATION_INTERVAL] = hours }
    }

    suspend fun setRotateOnUnlock(enabled: Boolean) {
        context.dataStore.edit { it[ROTATE_ON_UNLOCK] = enabled }
    }

    suspend fun setRotateOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[ROTATE_ON_BOOT] = enabled }
    }

    suspend fun setLastRotatedIndex(index: Int) {
        context.dataStore.edit { it[LAST_ROTATED_INDEX] = index }
    }
}
