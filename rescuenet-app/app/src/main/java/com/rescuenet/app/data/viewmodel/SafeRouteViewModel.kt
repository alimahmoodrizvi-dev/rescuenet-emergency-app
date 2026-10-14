package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.model.RouteDestination
import com.rescuenet.app.data.remote.NearbyPlaceDto
import com.rescuenet.app.data.remote.SafeRouteDto
import com.rescuenet.app.data.repository.HelpRepository
import com.rescuenet.app.data.repository.HelpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RouteUiState {
    object Loading : RouteUiState
    data class Failed(val kind: HelpResult.Kind, val message: String) : RouteUiState
    data class Loaded(val route: SafeRouteDto) : RouteUiState
}

@HiltViewModel
class SafeRouteViewModel @Inject constructor(
    private val helpRepository: HelpRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RouteUiState>(RouteUiState.Loading)
    val state: StateFlow<RouteUiState> = _state.asStateFlow()

    private val _choices = MutableStateFlow<List<NearbyPlaceDto>>(emptyList())
    val choices: StateFlow<List<NearbyPlaceDto>> = _choices.asStateFlow()

    fun load(destination: RouteDestination?, mode: String) {
        viewModelScope.launch {
            _state.value = RouteUiState.Loading
            when (val locResult = helpRepository.currentLocation()) {
                is HelpResult.Failure -> {
                    _state.value = RouteUiState.Failed(locResult.kind, locResult.message)
                }
                is HelpResult.Success -> {
                    val location = locResult.value
                    when (val routeResult = helpRepository.safeRoute(location, destination, mode)) {
                        is HelpResult.Failure -> {
                            _state.value = RouteUiState.Failed(routeResult.kind, routeResult.message)
                        }
                        is HelpResult.Success -> {
                            _state.value = RouteUiState.Loaded(routeResult.value)
                        }
                    }
                }
            }
        }
    }

    fun loadChoices() {
        viewModelScope.launch {
            when (val locResult = helpRepository.currentLocation()) {
                is HelpResult.Success -> {
                    when (val placesResult = helpRepository.nearby(locResult.value, DEFAULT_RADIUS_KM)) {
                        is HelpResult.Success -> {
                            _choices.value = placesResult.value.places
                        }
                        is HelpResult.Failure -> {
                            _choices.value = emptyList()
                        }
                    }
                }
                is HelpResult.Failure -> {
                    _choices.value = emptyList()
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_RADIUS_KM = 10.0
    }
}
