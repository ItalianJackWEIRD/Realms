package com.realms.app.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import android.location.Location
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.maps.CameraUpdateFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
            .tilt(fixedTilt)
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
        properties = properties,
        googleMapOptionsFactory = {
            GoogleMapOptions().mapId("62f2fd91384b16b631ee0872")
        }
    ) {
        Circle(
            center = userLatLng,
            radius = 500.0,
            strokeWidth = 7f,
            strokeColor = androidx.compose.ui.graphics.Color(0xFF1B1B1B), // bordo azzurrino
            fillColor = androidx.compose.ui.graphics.Color(0x55FDF6EC)

        )
    }

    val radiusMeters = 500.0

    LaunchedEffect(cameraPositionState.isMoving) {
        // quando smetti di muovere la camera (rilasci il dito)
        if (!cameraPositionState.isMoving) {
            val center = userLatLng
            val target = cameraPositionState.position.target

            val d = distanceMeters(center, target).toDouble()
            if (d > radiusMeters) {
                val clamped = clampToCircleEdge(center, target, radiusMeters)

                // Manteniamo zoom/tilt/bearing attuali, cambiamo solo target
                val current = cameraPositionState.position
                val corrected = CameraPosition.Builder(current)
                    .target(clamped)
                    .build()

                cameraPositionState.animate(
                    update = CameraUpdateFactory.newCameraPosition(corrected),
                    durationMs = 200
                )
            }
        }
    }

}

private fun distanceMeters(a: LatLng, b: LatLng): Float {
    val results = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
    return results[0]
}

/**
 * Ritorna il punto sul bordo del cerchio (center, radiusMeters) nella direzione center -> target.
 * Approssimazione molto buona per 500m.
 */
private fun clampToCircleEdge(center: LatLng, target: LatLng, radiusMeters: Double): LatLng {
    val earthRadius = 6378137.0 // metri (WGS84)

    val lat1 = Math.toRadians(center.latitude)
    val lon1 = Math.toRadians(center.longitude)
    val lat2 = Math.toRadians(target.latitude)
    val lon2 = Math.toRadians(target.longitude)

    // bearing iniziale center -> target
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = atan2(y, x) // radianti

    // Destinazione a distanza radiusMeters lungo quel bearing
    val angDist = radiusMeters / earthRadius
    val lat3 = asinSafe(sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(bearing))
    val lon3 = lon1 + atan2(
        sin(bearing) * sin(angDist) * cos(lat1),
        cos(angDist) - sin(lat1) * sin(lat3)
    )

    return LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3))
}

private fun asinSafe(v: Double): Double =
    kotlin.math.asin(v.coerceIn(-1.0, 1.0))

