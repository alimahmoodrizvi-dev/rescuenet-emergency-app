package com.rescuenet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.viewmodel.SettingsViewModel
import com.rescuenet.app.ui.navigation.RescueNetNavGraph
import com.rescuenet.app.ui.theme.RescueNetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()

            RescueNetTheme(
                darkTheme = settings.darkMode || systemDark,
                largeText = settings.largeText,
                highContrast = settings.highContrast,
            ) {
                RescueNetNavGraph(
                    darkMode = settings.darkMode, onDarkModeChange = settingsViewModel::setDarkMode,
                    largeText = settings.largeText, onLargeTextChange = settingsViewModel::setLargeText,
                    highContrast = settings.highContrast, onHighContrastChange = settingsViewModel::setHighContrast,
                    backendBaseUrl = settings.backendBaseUrl, onBackendBaseUrlChange = settingsViewModel::setBackendBaseUrl,
                )
            }
        }
    }
}
