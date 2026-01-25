package com.realms.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.realms.app.data.weather.WeatherNetwork
import com.realms.app.data.weather.WeatherUiModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
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

    // FOLLOW state
    var followOn by remember { mutableStateOf(true) }

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

    // =========================
    // 1) LOCATION UPDATES (real-time)
    // =========================
    // Frequenza aggiornamento posizione (modifica qui se vuoi)
    val locationIntervalMs = 5_000L

    LaunchedEffect(hasLocationPermission, hasFine) {
        if (!hasLocationPermission) return@LaunchedEffect

        val fused = LocationServices.getFusedLocationProviderClient(context)

        // Prima: prendi subito una posizione (per non aspettare il primo update)
        val last = fused.awaitLastLocationSafe()
        val first = last ?: fused.awaitCurrentLocationSafe(hasFine)
        if (first != null) {
            val newPos = LatLng(first.latitude, first.longitude)
            userLatLng = newPos
            pendingCenter = newPos
        }

        // Poi: flusso continuo
        fused.locationUpdatesFlow(
            hasFine = hasFine,
            intervalMs = locationIntervalMs
        ).collectLatest { loc ->
            userLatLng = LatLng(loc.latitude, loc.longitude)
        }
    }

    // =========================
    // 2) FOLLOW LOGIC
    // =========================

    // Se l'utente muove la mappa con gesture -> follow OFF
    LaunchedEffect(cameraPositionState.cameraMoveStartedReason) {
        if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            followOn = false
        }
    }

    // Quando follow è ON: ad ogni update di userLatLng, anima la camera verso l'utente
    // (solo dopo che la mappa è pronta e dopo il primo center)
    LaunchedEffect(followOn, userLatLng, mapLoaded, didCenterOnce) {
        if (!followOn) return@LaunchedEffect
        if (!mapLoaded) return@LaunchedEffect
        if (!didCenterOnce) return@LaunchedEffect

        val current = cameraPositionState.position
        val updated = CameraPosition.Builder(current)
            .target(userLatLng)
            .tilt(fixedTilt)
            .build()

        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(updated),
            durationMs = 450
        )
    }

    // =========================
    // 3) METEO (ogni 15s)
    // =========================
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
                    weather = null
                    weatherError = e.message ?: "Weather error"
                    Log.e("Weather", "ERR: $weatherError", e)
                }

            delay(15_000)
        }
    }

    // =========================
    // UI
    // =========================
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

        // ✅ Follow button (bottom-right)
        FloatingActionButton(
            onClick = {
                followOn = true
                // forza anche un "center" immediato, così non aspetti l'animazione next update
                if (mapLoaded) {
                    val current = cameraPositionState.position
                    val updated = CameraPosition.Builder(current)
                        .target(userLatLng)
                        .zoom(initialZoom)
                        .tilt(fixedTilt)
                        .build()
                    // move immediato per essere “snap”
                    cameraPositionState.move(CameraUpdateFactory.newCameraPosition(updated))
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Follow")
        }
    }

    // =========================
    // Primo center (una sola volta)
    // =========================
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

    // =========================
    // Clamp: attivo solo dopo il primo center e solo se follow è OFF
    // =========================
    LaunchedEffect(cameraPositionState.isMoving, userLatLng, didCenterOnce, followOn) {
        if (!didCenterOnce) return@LaunchedEffect
        if (followOn) return@LaunchedEffect
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
// Fused helpers (one-shot)
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

// -------------------------
// Fused helpers (real-time flow)
// -------------------------

@SuppressLint("MissingPermission")
private fun com.google.android.gms.location.FusedLocationProviderClient.locationUpdatesFlow(
    hasFine: Boolean,
    intervalMs: Long
) = callbackFlow<Location> {

    val priority =
        if (hasFine) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    val request = LocationRequest.Builder(priority, intervalMs)
        .setMinUpdateIntervalMillis(intervalMs)
        .setWaitForAccurateLocation(false)
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            trySend(loc).isSuccess
        }
    }

    requestLocationUpdates(request, callback, Looper.getMainLooper())

    awaitClose {
        removeLocationUpdates(callback)
    }
}
