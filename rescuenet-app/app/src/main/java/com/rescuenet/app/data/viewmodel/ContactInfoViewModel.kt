package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.model.PhoneNumbers
import com.rescuenet.app.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The user's own phone number — optional, and only ever shared with rescuers. */
@HiltViewModel
class ContactInfoViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    /** null until the saved value has been read, so the field isn't seeded with a false blank. */
    val myPhone: StateFlow<String?> = settings.myPhone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Blank clears the saved number; a valid number is saved cleaned-up; anything else is ignored
     *  (the field shows an error and keeps what was saved before). */
    fun savePhone(raw: String) {
        viewModelScope.launch {
            if (raw.isBlank()) {
                settings.setMyPhone("")
            } else {
                PhoneNumbers.normalize(raw)?.let { settings.setMyPhone(it) }
            }
        }
    }
}
