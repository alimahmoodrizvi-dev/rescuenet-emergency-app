package com.rescuenet.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rescuenet.app.data.remote.BackendApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rescuenet_settings")

data class AppSettings(
    val darkMode: Boolean,
    val largeText: Boolean,
    val highContrast: Boolean,
    val backendBaseUrl: String,
)

/** Persists the accessibility/appearance settings from Part 18 so they survive app restarts —
 *  the previous mock version reset to defaults every launch. */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            largeText = prefs[Keys.LARGE_TEXT] ?: false,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL] ?: BackendApiClient.DEFAULT_BASE_URL,
        )
    }

    suspend fun setDarkMode(value: Boolean) = context.dataStore.edit { it[Keys.DARK_MODE] = value }
    suspend fun setLargeText(value: Boolean) = context.dataStore.edit { it[Keys.LARGE_TEXT] = value }
    suspend fun setHighContrast(value: Boolean) = context.dataStore.edit { it[Keys.HIGH_CONTRAST] = value }
    suspend fun setBackendBaseUrl(value: String) = context.dataStore.edit { it[Keys.BACKEND_BASE_URL] = value }
}
