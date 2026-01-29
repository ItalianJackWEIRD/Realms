package com.realms.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.realms.app.ui.profile.BasicProfileUi
import com.realms.app.ui.profile.ProfileBottomSheetBasic
import com.google.firebase.auth.FirebaseAuth
import com.realms.app.data.realms.NearbyUserDto
import com.realms.app.data.realms.RealmsNetwork
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

    // Permessi
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

    // Stato backend Realms
    var backendStatus by remember { mutableStateOf<String?>(null) }
    var nearbyUsers by remember { mutableStateOf<List<NearbyUserDto>>(emptyList()) }

    // Popup debug su click marker
    var selectedUser by remember { mutableStateOf<NearbyUserDto?>(null) }
    var showUserPopup by remember { mutableStateOf(false) }

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
    val locationIntervalMs = 5_000L

    LaunchedEffect(hasLocationPermission, hasFine) {
        if (!hasLocationPermission) return@LaunchedEffect

        val fused = LocationServices.getFusedLocationProviderClient(context)

        val last = fused.awaitLastLocationSafe()
        val first = last ?: fused.awaitCurrentLocationSafe(hasFine)
        if (first != null) {
            val newPos = LatLng(first.latitude, first.longitude)
            userLatLng = newPos
            pendingCenter = newPos
        }

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
    LaunchedEffect(cameraPositionState.cameraMoveStartedReason) {
        if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            followOn = false
        }
    }

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
    // 4) REALMS BACKEND (ogni 15s): update + nearby
    //    - nuovo backend: identity dal Bearer token
    //    - Android NON passa userId
    // =========================
    LaunchedEffect(Unit) {
        while (true) {
            val lat = userLatLng.latitude
            val lon = userLatLng.longitude

            // 1) Update posizione (last-location)
            val upd = RealmsNetwork.repository.updateLocation(
                latitude = lat,
                longitude = lon
            )

            if (upd.isFailure) {
                val msg = upd.exceptionOrNull()?.message ?: "unknown"
                backendStatus = "backend error: $msg"
                Log.e("Realms", "updateLocation failed", upd.exceptionOrNull())
                delay(15_000)
                continue
            }

            // 2) Nearby (nel nuovo backend può essere vuoto finché non hai amici)
            val near = RealmsNetwork.repository.getNearbyUsers(
                latitude = lat,
                longitude = lon,
                radiusMeters = 500
            )

            near
                .onSuccess { list ->
                    nearbyUsers = list
                    backendStatus = "backend ok (${list.size})"
                }
                .onFailure { e ->
                    backendStatus = "backend offline (nearby)"
                    nearbyUsers = emptyList()
                    Log.e("Realms", "getNearbyUsers failed", e)
                }

            delay(15_000)
        }
    }

    // =========================
// UI + Profile bottom sheet (STEP 1)
// =========================
    var profile by remember {
        mutableStateOf(
            BasicProfileUi(
                username = "loading",
                firstName = "",
                lastName = "",
                bio = null
            )
        )
    }

    var friendsCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // 1) profilo dal DB
        RealmsNetwork.repository.getMe()
            .onSuccess { me ->
                profile = BasicProfileUi(
                    username = me.username,
                    firstName = me.firstName,
                    lastName = me.lastName,
                    bio = me.bio
                )
            }

        // 2) count amici (non esiste in DB -> lo calcoliamo)
        RealmsNetwork.repository.getFriends()
            .onSuccess { list ->
                friendsCount = list.size
            }
    }

    ProfileBottomSheetBasic(
        profile = profile,
        friendsCount = friendsCount,
        contentBehind = {
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

                    // Tu
                    Marker(
                        state = myMarkerState,
                        title = "Tu"
                    )

                    // Utenti vicini (per ora spesso vuoto: solo amici)
                    nearbyUsers.forEach { u ->
                        val pos = LatLng(u.latitude, u.longitude)
                        Marker(
                            state = MarkerState(position = pos),
                            title = u.userId,
                            onClick = {
                                selectedUser = u
                                showUserPopup = true
                                true
                            }
                        )
                    }
                }

                // Logout (top-right)
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text("Logout")
                }

                // Weather overlay (top-left)
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

                // Backend overlay (sotto al meteo)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 56.dp),
                    tonalElevation = 6.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = backendStatus ?: "backend: ...",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Follow (bottom-right)
                FloatingActionButton(
                    onClick = {
                        followOn = true
                        if (mapLoaded) {
                            val current = cameraPositionState.position
                            val updated = CameraPosition.Builder(current)
                                .target(userLatLng)
                                .zoom(initialZoom)
                                .tilt(fixedTilt)
                                .build()
                            cameraPositionState.move(CameraUpdateFactory.newCameraPosition(updated))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 175.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Follow")
                }

                // Popup debug al centro con X
                if (showUserPopup && selectedUser != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color(0x66000000))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .wrapContentHeight()
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {

                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clickable {
                                            showUserPopup = false
                                            selectedUser = null
                                        }
                                )

                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("DEBUG USER", style = MaterialTheme.typography.titleMedium)
                                    Text("userId: ${selectedUser!!.userId}")
                                    Text("lat: ${selectedUser!!.latitude}")
                                    Text("lon: ${selectedUser!!.longitude}")
                                    Text("updatedAtUtc: ${selectedUser!!.updatedAtUtc}")
                                }
                            }
                        }
                    }
                }
            }
        }
    )


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
