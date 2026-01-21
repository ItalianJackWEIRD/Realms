package com.realms.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapScreenWithLocation() {
    val context = LocalContext.current

    // Permessi (li gestisci già con LocationPermissionGate, ma qui serve per sicurezza)
    val hasFine = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
    val hasCoarse = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
    val hasLocationPermission = hasFine || hasCoarse

    // ✅ Stato: parte da Roma, poi diventa la tua posizione reale
    var userLatLng by remember { mutableStateOf(LatLng(41.9028, 12.4964)) }


    val initialZoom = 18f
    val fixedTilt = 60f
    val minZoom = 17f
    val maxZoom = 20f

    // ✅ Centra la camera UNA sola volta quando trovi la location
    var didCenterOnce by remember { mutableStateOf(false) }

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
            compassEnabled = true,
            myLocationButtonEnabled = true,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    }

    val properties = remember {
        MapProperties(
            isMyLocationEnabled = false, // se vuoi lo "pallino blu" metti true, ma non serve per 2.2
            minZoomPreference = minZoom,
            maxZoomPreference = maxZoom,
            isBuildingEnabled = true
        )
    }

    // ✅ 2.2: posizione reale (lastLocation -> currentLocation)
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect

        val fused = LocationServices.getFusedLocationProviderClient(context)

        val last = fused.awaitLastLocationSafe()
        val loc = last ?: fused.awaitCurrentLocationSafe(hasFine)

        if (loc != null) {
            val newPos = LatLng(loc.latitude, loc.longitude)
            userLatLng = newPos

            if (!didCenterOnce) {
                val newCam = CameraPosition.Builder()
                    .target(newPos)
                    .zoom(initialZoom)
                    .tilt(fixedTilt)
                    .bearing(0f)
                    .build()

                cameraPositionState.animate(
                    update = CameraUpdateFactory.newCameraPosition(newCam),
                    durationMs = 600
                )

                didCenterOnce = true
            }
        }
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
            strokeColor = androidx.compose.ui.graphics.Color(0xFF1B1B1B),
            fillColor = androidx.compose.ui.graphics.Color(0x55FDF6EC)
        )
        Marker(
            state = MarkerState(position = userLatLng),
            title = "Tu"
        )
    }

    val radiusMeters = 500.0

    // Clamp come prima: quando smetti di muovere la camera, la riporta dentro al cerchio
    LaunchedEffect(cameraPositionState.isMoving, userLatLng) {
        if (!cameraPositionState.isMoving) {
            val center = userLatLng
            val target = cameraPositionState.position.target

            val d = distanceMeters(center, target).toDouble()
            if (d > radiusMeters) {
                val clamped = clampToCircleEdge(center, target, radiusMeters)

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

private fun clampToCircleEdge(center: LatLng, target: LatLng, radiusMeters: Double): LatLng {
    val earthRadius = 6378137.0

    val lat1 = Math.toRadians(center.latitude)
    val lon1 = Math.toRadians(center.longitude)
    val lat2 = Math.toRadians(target.latitude)
    val lon2 = Math.toRadians(target.longitude)

    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = atan2(y, x)

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

// ---------- Helpers Fused (suspend) ----------

@SuppressLint("MissingPermission")
private suspend fun com.google.android.gms.location.FusedLocationProviderClient.awaitLastLocationSafe() =
    suspendCancellableCoroutine<Location?> { cont ->
        lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

@SuppressLint("MissingPermission")
private suspend fun com.google.android.gms.location.FusedLocationProviderClient.awaitCurrentLocationSafe(
    hasFine: Boolean
) = suspendCancellableCoroutine<Location?> { cont ->
    val priority =
        if (hasFine) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    getCurrentLocation(priority, null)
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resume(null) }
}
