package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.remote.BackendApiClient
import com.rescuenet.app.data.settings.AppSettings
import com.rescuenet.app.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            AppSettings(darkMode = false, largeText = false, highContrast = false, backendBaseUrl = BackendApiClient.DEFAULT_BASE_URL),
        )

    fun setDarkMode(value: Boolean) = viewModelScope.launch { settingsDataStore.setDarkMode(value) }
    fun setLargeText(value: Boolean) = viewModelScope.launch { settingsDataStore.setLargeText(value) }
    fun setHighContrast(value: Boolean) = viewModelScope.launch { settingsDataStore.setHighContrast(value) }
    fun setBackendBaseUrl(value: String) = viewModelScope.launch { settingsDataStore.setBackendBaseUrl(value) }
}
