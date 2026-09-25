package com.garagedoor.app.ui

import com.garagedoor.app.data.GarageStatus
import com.garagedoor.app.data.AppSettings

data class GarageUiState(
    val settings: AppSettings = AppSettings(),
    val status: GarageStatus? = null,
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
)
