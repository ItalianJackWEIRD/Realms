package com.realms.app.ui.screen

import androidx.compose.runtime.Composable

@Composable
fun MapScreen() {
    LocationPermissionGate(
        onGranted = { MapScreenWithLocation() },
        onFallback = { MapScreenFallback() }
    )
}
