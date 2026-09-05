package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { userRepository.ensureProfileExists() }
    }

    fun completeOnboarding(languagePref: String, onDone: () -> Unit) {
        viewModelScope.launch {
            userRepository.completeOnboarding(languagePref)
            onDone()
        }
    }
}
