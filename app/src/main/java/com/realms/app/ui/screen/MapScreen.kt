package com.realms.app.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen() {
    val userLatLng = remember { LatLng(41.9028, 12.4964) } // Roma (placeholder)

    val initialZoom = 18f
    val fixedTilt = 60f
    val minZoom = 17f
    val maxZoom = 20f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(userLatLng)
            .zoom(initialZoom)
            .tilt(fixedTilt) // 3D feel
            .bearing(0f)
            .build()
    }

    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    }

    val properties = remember {
        MapProperties(
            isMyLocationEnabled = false,
            minZoomPreference = minZoom,
            maxZoomPreference = maxZoom,
            isBuildingEnabled = true
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings,
        properties = properties
    ) {
        // Cerchio raggio 500m
        Circle(
            center = userLatLng,
            radius = 500.0,
            strokeWidth = 4f
        )
    }
}
