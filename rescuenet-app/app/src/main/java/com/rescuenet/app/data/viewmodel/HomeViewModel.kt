package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.local.entity.FamilyMemberEntity
import com.rescuenet.app.data.local.entity.IncidentEntity
import com.rescuenet.app.data.mesh.MeshRelayManager
import com.rescuenet.app.data.model.*
import com.rescuenet.app.data.offline.NetworkConnectivityObserver
import com.rescuenet.app.data.repository.AddMemberResult
import com.rescuenet.app.data.repository.FamilyRepository
import com.rescuenet.app.data.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Phase 3 backed this state with Room + real connectivity; Phase 4 adds the real mesh peer
 * count from MeshRelayManager (BLE + Wi-Fi Direct discovery) in place of the old demo-only
 * node count. The public StateFlow shapes are unchanged, so screens needed no rewrites.
 *
 * `demoOverride`/`demoNearbyNodesOverride` let Demo Mode (Part 16) simulate an internet
 * outage and a crowd of nearby nodes for judges without needing real hardware in the room —
 * when set, they take precedence over the real signals; `null` means "trust the real signal."
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val incidentRepository: IncidentRepository,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val meshRelayManager: MeshRelayManager,
) : ViewModel() {

    private val demoOverride = MutableStateFlow<Boolean?>(null)
    private val demoNearbyNodesOverride = MutableStateFlow<Int?>(null)
    private val lastSyncEpochMs = MutableStateFlow<Long?>(null)

    private val _routeDestination = MutableStateFlow<RouteDestination?>(null)
    val routeDestination: StateFlow<RouteDestination?> = _routeDestination.asStateFlow()

    val networkStatus: StateFlow<NetworkStatusInfo> = combine(
        connectivityObserver.observe(),
        demoOverride,
        meshRelayManager.observeNearbyNodeCount(),
        demoNearbyNodesOverride,
        lastSyncEpochMs,
    ) { realConnected, override, realNearbyNodes, demoNearby, lastSync ->
        val connected = override ?: realConnected
        val nearbyNodes = demoNearby ?: realNearbyNodes
        NetworkStatusInfo(
            internetConnected = connected,
            nearbyNodeCount = if (connected) 0 else nearbyNodes,
            lastSyncLabel = lastSync?.let { formatTime(it) } ?: "Never",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkStatusInfo(true, 0, "—"))

    val myStatus: StateFlow<FamilyMember?> = familyRepository.observeMembers()
        .map { entities -> entities.firstOrNull { it.isSelf }?.toUiModel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val familyMembers: StateFlow<List<FamilyMember>> = familyRepository.observeMembers()
        .map { entities -> entities.filterNot { it.isSelf }.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incidents: StateFlow<List<IncidentEntity>> = incidentRepository.observeIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { familyRepository.seedIfEmpty() }
        viewModelScope.launch {
            connectivityObserver.observe().collect { connected ->
                if (connected) lastSyncEpochMs.value = System.currentTimeMillis()
            }
        }
        // Mesh scanning/advertising runs for the lifetime of the app's foreground use.
        // start() internally no-ops if Bluetooth is off or permissions aren't granted yet
        // (see BleTransportProvider.canUseBluetooth), so this is safe to call unconditionally.
        meshRelayManager.start()
    }

    fun setRouteDestination(destination: RouteDestination?) {
        _routeDestination.value = destination
    }

    fun markSafe() {
        viewModelScope.launch { familyRepository.markSelfSafe() }
    }

    fun addFamilyMember(name: String, onResult: (AddMemberResult) -> Unit) {
        viewModelScope.launch {
            onResult(familyRepository.addMember(name))
        }
    }

    fun removeFamilyMember(id: String) {
        viewModelScope.launch {
            familyRepository.removeMember(id)
        }
    }

    /** Demo-mode hooks (Part 16) — override, not a real radio state change. */
    fun simulateInternetFailure() {
        demoOverride.value = false
        demoNearbyNodesOverride.value = 4
    }

    fun simulateInternetRecovery() {
        demoOverride.value = null // fall back to the real connectivity signal
        demoNearbyNodesOverride.value = null // fall back to the real mesh peer count
        lastSyncEpochMs.value = System.currentTimeMillis()
    }

    override fun onCleared() {
        meshRelayManager.stop()
        super.onCleared()
    }

    private fun formatTime(epochMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun FamilyMemberEntity.toUiModel(): FamilyMember {
    val minutesAgo = ((System.currentTimeMillis() - lastUpdatedEpochMs) / 60000).toInt()
    val label = when {
        minutesAgo < 1 -> "Just now"
        minutesAgo < 60 -> "${minutesAgo}m ago"
        else -> "${minutesAgo / 60}h ago"
    }
    return FamilyMember(id = id, name = name, status = status, lastUpdatedLabel = label)
}
