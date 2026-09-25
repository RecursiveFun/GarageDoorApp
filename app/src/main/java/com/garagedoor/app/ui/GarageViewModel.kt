package com.garagedoor.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.garagedoor.app.data.GarageRepository
import com.garagedoor.app.data.LatLngPoint
import com.garagedoor.app.data.SettingsRepository
import com.garagedoor.app.geofence.GeofenceRegistrar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GarageViewModel(
    private val settingsRepository: SettingsRepository,
    private val garageRepository: GarageRepository,
    private val geofenceRegistrar: GeofenceRegistrar,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarageUiState())
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun saveCredentials(baseUrl: String, apiToken: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            settingsRepository.saveApiCredentials(baseUrl, apiToken)
            testConnection(onDone)
        }
    }

    fun testConnection(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            garageRepository.fetchStatus()
                .onSuccess { status ->
                    _uiState.update { it.copy(status = status, statusMessage = null, isLoading = false) }
                    onDone(null)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(statusMessage = error.message, isLoading = false) }
                    onDone(error.message)
                }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            garageRepository.fetchStatus()
                .onSuccess { status ->
                    _uiState.update { it.copy(status = status, statusMessage = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(statusMessage = error.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun triggerManual(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            garageRepository.trigger("manual")
                .onSuccess { response ->
                    if (response.ok) onDone(null) else onDone(response.message ?: "Trigger rejected")
                }
                .onFailure { onDone(it.message) }
            _uiState.update { it.copy(isLoading = false) }
            refreshStatus()
        }
    }

    fun savePolygon(
        points: List<LatLngPoint>,
        currentlyInside: Boolean?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            if (points.size < 3) {
                onDone("Draw at least 3 corners")
                return@launch
            }
            settingsRepository.savePolygon(points, currentlyInside)
            onDone(null)
        }
    }

    fun finishSetup(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            if (!settings.hasCredentials) {
                onDone("Add API credentials first")
                return@launch
            }
            if (!settings.hasPolygon) {
                onDone("Draw a boundary first")
                return@launch
            }
            settingsRepository.setSetupComplete(true)
            runCatching { geofenceRegistrar.registerIfConfigured() }
                .onSuccess { onDone(null) }
                .onFailure { onDone(it.message) }
        }
    }

    fun setAutoOpen(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoOpenEnabled(enabled)
            geofenceRegistrar.registerIfConfigured()
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val garageRepository: GarageRepository,
        private val geofenceRegistrar: GeofenceRegistrar,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GarageViewModel(settingsRepository, garageRepository, geofenceRegistrar) as T
        }
    }
}
