package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.repository.UserRepository
import com.rescuenet.app.data.voice.VoiceRecognitionEvent
import com.rescuenet.app.data.voice.VoiceRecognitionProvider
import com.rescuenet.app.data.voice.languagePrefToTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VoiceModeUiState {
    object Idle : VoiceModeUiState()
    object Listening : VoiceModeUiState()
    data class Partial(val text: String) : VoiceModeUiState()
    data class Done(val text: String) : VoiceModeUiState()
    data class Failed(val message: String) : VoiceModeUiState()
}

@HiltViewModel
class VoiceModeViewModel @Inject constructor(
    private val voiceRecognitionProvider: VoiceRecognitionProvider,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceModeUiState>(VoiceModeUiState.Idle)
    val state: StateFlow<VoiceModeUiState> = _state

    fun startListening() {
        viewModelScope.launch {
            _state.value = VoiceModeUiState.Listening
            val languageTag = languagePrefToTag(userRepository.ensureProfileExists().languagePref)

            voiceRecognitionProvider.listen(languageTag).collect { event ->
                _state.value = when (event) {
                    is VoiceRecognitionEvent.ReadyForSpeech, VoiceRecognitionEvent.SpeechStarted -> VoiceModeUiState.Listening
                    is VoiceRecognitionEvent.PartialText -> VoiceModeUiState.Partial(event.text)
                    is VoiceRecognitionEvent.FinalText -> VoiceModeUiState.Done(event.text)
                    is VoiceRecognitionEvent.Error -> VoiceModeUiState.Failed(event.message)
                }
            }
        }
    }

    fun reset() {
        _state.value = VoiceModeUiState.Idle
    }
}
