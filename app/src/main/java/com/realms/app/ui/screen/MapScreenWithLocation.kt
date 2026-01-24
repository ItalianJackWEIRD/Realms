package com.realms.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import android.util.Log
import com.realms.app.data.weather.WeatherNetwork
import com.realms.app.data.weather.WeatherUiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapScreenWithLocation(
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // Permessi (ricalcolati ad ogni recomposition)
    val hasFine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    val hasCoarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    val hasLocationPermission = hasFine || hasCoarse

    // Stato posizione e meteo: parte da Roma
    var userLatLng by remember { mutableStateOf(LatLng(41.9028, 12.4964)) }
    var weather by remember { mutableStateOf<WeatherUiModel?>(null) }
    var weatherError by remember { mutableStateOf<String?>(null) }


    // Config mappa
    val initialZoom = 18f
    val fixedTilt = 60f
    val minZoom = 17f
    val maxZoom = 20f
    val radiusMeters = 500.0

    // Center una sola volta quando mappa è pronta
    var mapLoaded by remember { mutableStateOf(false) }
    var didCenterOnce by remember { mutableStateOf(false) }
    var pendingCenter by remember { mutableStateOf<LatLng?>(null) }

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
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    }

    val properties = remember(hasLocationPermission) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            minZoomPreference = minZoom,
            maxZoomPreference = maxZoom,
            isBuildingEnabled = true
        )
    }

    // Marker “Tu” stabile
    val myMarkerState = remember { MarkerState(position = userLatLng) }
    LaunchedEffect(userLatLng) {
        myMarkerState.position = userLatLng
    }

    // 2.2: prendi posizione reale UNA volta (last -> current)
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect

        val fused = LocationServices.getFusedLocationProviderClient(context)

        val last = fused.awaitLastLocationSafe()
        val loc = last ?: fused.awaitCurrentLocationSafe(hasFine)

        if (loc != null) {
            val newPos = LatLng(loc.latitude, loc.longitude)
            userLatLng = newPos
            pendingCenter = newPos
        }
    }

    // METEO
    LaunchedEffect(Unit) {
        while (true) {
            val lat = userLatLng.latitude
            val lon = userLatLng.longitude

            val result = WeatherNetwork.repository.getWeatherFor(lat, lon)
            result
                .onSuccess {
                    weather = it
                    weatherError = null
                    Log.d("Weather", "OK: ${it.temperatureC}°C · ${it.label} @ ${it.fetchedAtIso}")
                }
                .onFailure { e ->
                    weather = null // ✅ così l'overlay non mostra dati vecchi
                    weatherError = e.message ?: "Weather error"
                    Log.e("Weather", "ERR: $weatherError", e)
                }


            delay(15_000)
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = properties,
            googleMapOptionsFactory = {
                GoogleMapOptions().mapId("62f2fd91384b16b631ee0872")
            },
            onMapLoaded = { mapLoaded = true }
        ) {
            Circle(
                center = userLatLng,
                radius = radiusMeters,
                strokeWidth = 7f,
                strokeColor = androidx.compose.ui.graphics.Color(0xFF1B1B1B),
                fillColor = androidx.compose.ui.graphics.Color(0x55FDF6EC)
            )

            Marker(
                state = myMarkerState,
                title = "Tu"
            )
        }

        // ✅ Logout overlay (top-right)
        Button(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text("Logout")
        }

        // ✅ Weather overlay (top-left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            val text = when {
                weatherError != null -> "Meteo: offline"
                weather != null -> "${weather!!.temperatureC}°C · ${weather!!.label}"
                else -> "Meteo: loading..."
            }

            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

    }

    // Centro camera una volta sola (dopo mapLoaded)
    LaunchedEffect(mapLoaded, pendingCenter) {
        val target = pendingCenter ?: return@LaunchedEffect
        if (!mapLoaded || didCenterOnce) return@LaunchedEffect

        val cam = CameraPosition.Builder()
            .target(target)
            .zoom(initialZoom)
            .tilt(fixedTilt)
            .bearing(0f)
            .build()

        cameraPositionState.move(CameraUpdateFactory.newCameraPosition(cam))

        didCenterOnce = true
        pendingCenter = null
    }

    // Clamp: attivo solo dopo il primo center
    LaunchedEffect(cameraPositionState.isMoving, userLatLng, didCenterOnce) {
        if (!didCenterOnce) return@LaunchedEffect
        if (cameraPositionState.isMoving) return@LaunchedEffect

        val center = userLatLng
        val target = cameraPositionState.position.target

        val d = distanceMeters(center, target).toDouble()
        if (d > radiusMeters) {
            val clamped = clampToCircleEdge(center, target, radiusMeters)

            val current = cameraPositionState.position
            val corrected = CameraPosition.Builder(current)
                .target(clamped)
                .tilt(fixedTilt)
                .build()

            cameraPositionState.animate(
                update = CameraUpdateFactory.newCameraPosition(corrected),
                durationMs = 200
            )
        }
    }
}

// -------------------------
// Helpers (clamp)
// -------------------------

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

// -------------------------
// Fused helpers (2.2)
// -------------------------

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
