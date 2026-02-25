package com.realms.app.ui.screen

import androidx.compose.runtime.Composable

@Composable
fun MapScreen(
    onLogout: () -> Unit
) {
    LocationPermissionGate(
        onGranted = { MapScreenWithLocation(onLogout = onLogout) },
        onFallback = { MapScreenFallback() }
    )
}

