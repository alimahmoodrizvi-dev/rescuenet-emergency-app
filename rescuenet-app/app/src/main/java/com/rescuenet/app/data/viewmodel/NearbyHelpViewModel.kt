package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.location.DeviceLocation
import com.rescuenet.app.data.remote.NearbyPlaceDto
import com.rescuenet.app.data.repository.HelpRepository
import com.rescuenet.app.data.repository.HelpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NearbyUiState {
    object Loading : NearbyUiState
    data class Failed(val kind: HelpResult.Kind, val message: String) : NearbyUiState
    data class Loaded(
        val origin: DeviceLocation,
        val radiusKm: Double,
        val osmAvailable: Boolean,
        val places: List<NearbyPlaceDto>,
    ) : NearbyUiState
}

@HiltViewModel
class NearbyHelpViewModel @Inject constructor(
    private val helpRepository: HelpRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<NearbyUiState>(NearbyUiState.Loading)
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = NearbyUiState.Loading
            when (val locResult = helpRepository.currentLocation()) {
                is HelpResult.Failure -> {
                    _state.value = NearbyUiState.Failed(locResult.kind, locResult.message)
                }
                is HelpResult.Success -> {
                    val location = locResult.value
                    when (val placesResult = helpRepository.nearby(location, DEFAULT_RADIUS_KM)) {
                        is HelpResult.Failure -> {
                            _state.value = NearbyUiState.Failed(placesResult.kind, placesResult.message)
                        }
                        is HelpResult.Success -> {
                            val dto = placesResult.value
                            _state.value = NearbyUiState.Loaded(
                                origin = location,
                                radiusKm = dto.radiusKm,
                                osmAvailable = dto.osmAvailable,
                                places = dto.places,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_RADIUS_KM = 10.0
    }
}
