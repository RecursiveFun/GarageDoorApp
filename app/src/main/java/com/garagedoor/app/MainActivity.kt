package com.garagedoor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagedoor.app.data.GarageRepository
import com.garagedoor.app.data.SettingsRepository
import com.garagedoor.app.geofence.GeofenceRegistrar
import com.garagedoor.app.ui.GarageDoorTheme
import com.garagedoor.app.ui.GarageMapScreen
import com.garagedoor.app.ui.GarageViewModel
import com.garagedoor.app.ui.RequestGaragePermissions
import com.garagedoor.app.ui.screenInsetsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var geofenceRegistrar: GeofenceRegistrar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(applicationContext)
        val garageRepository = GarageRepository(applicationContext)
        geofenceRegistrar = GeofenceRegistrar(applicationContext)
        val factory = GarageViewModel.Factory(settingsRepository, garageRepository, geofenceRegistrar)

        setContent {
            GarageDoorTheme {
                val viewModel: GarageViewModel = viewModel(factory = factory)
                var permissionsReady by rememberSaveable { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current

                RequestGaragePermissions { granted ->
                    permissionsReady = granted
                }

                LaunchedEffect(permissionsReady) {
                    if (!permissionsReady) return@LaunchedEffect
                    withContext(Dispatchers.IO) {
                        runCatching { geofenceRegistrar.registerIfConfigured() }
                    }
                }

                DisposableEffect(lifecycleOwner, permissionsReady) {
                    if (!permissionsReady) {
                        return@DisposableEffect onDispose { }
                    }
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                runCatching { geofenceRegistrar.ensureMonitoringActive() }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (!permissionsReady) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .screenInsetsPadding(),
                    ) {
                        Text(
                            "Allow location all the time and notifications so auto-open can stay armed when the app is closed.",
                        )
                    }
                } else {
                    GarageMapScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { geofenceRegistrar.registerIfConfigured() }
        }
    }
}
