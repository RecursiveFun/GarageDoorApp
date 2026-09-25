package com.garagedoor.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun hasRequiredGaragePermissions(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val background = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!background) return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifications = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!notifications) return false
    }

    return true
}

@Composable
fun RequestGaragePermissions(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    var pendingBackground by remember { mutableStateOf(false) }
    var pendingNotifications by remember { mutableStateOf(false) }

    fun reportIfReady() {
        onResult(hasRequiredGaragePermissions(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val background = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!background) {
                pendingBackground = true
                return@rememberLauncherForActivityResult
            }
        }
        reportIfReady()
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { reportIfReady() }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!fineGranted) {
            onResult(false)
            return@rememberLauncherForActivityResult
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotifications = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingBackground = true
        } else {
            reportIfReady()
        }
    }

    LaunchedEffect(pendingNotifications) {
        if (pendingNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotifications = false
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingBackground) {
        if (pendingBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingBackground = false
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        if (hasRequiredGaragePermissions(context)) {
            onResult(true)
            return@LaunchedEffect
        }

        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine) {
            fineLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return@LaunchedEffect
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifications = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifications) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return@LaunchedEffect
        }

        reportIfReady()
    }
}
