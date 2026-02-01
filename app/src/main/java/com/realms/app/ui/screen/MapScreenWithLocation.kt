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
import com.realms.app.data.realms.FriendDto
import com.realms.app.data.realms.FriendRequestDto
import com.realms.app.data.realms.NearbyUserDto
import com.realms.app.data.realms.UserProfileDto
import com.realms.app.data.realms.RealmsNetwork
import com.realms.app.data.realms.SearchUserDto
import com.realms.app.data.weather.WeatherNetwork
import com.realms.app.data.weather.WeatherUiModel
import com.realms.app.ui.profile.BasicProfileUi
import com.realms.app.ui.profile.ProfileBottomSheetBasic
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.realms.app.R
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars


@Composable
fun MapScreenWithLocation(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // =========================
    // POPUP friend marker + profile fetch cache
    // =========================
    var selectedUser by remember { mutableStateOf<NearbyUserDto?>(null) }
    var showUserPopup by remember { mutableStateOf(false) }

    // profilo completo caricato via /users/{id}/profile
    var selectedUserProfile by remember { mutableStateOf<UserProfileDto?>(null) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }

    // cache profili già fetchati
    val profileCache = remember { androidx.compose.runtime.mutableStateMapOf<String, UserProfileDto>() }


    // UI-only navigation: Edit Profile screen
    var showEditProfile by remember { mutableStateOf(false) }
    var editProfileLoading by remember { mutableStateOf(false) }
    var editProfileError by remember { mutableStateOf<String?>(null) }


    // FOLLOW state
    var followOn by remember { mutableStateOf(true) }
    // Bearing telefono (0..360). Lo usiamo per ruotare la camera quando FOLLOW è attivo.
    var phoneBearing by remember { mutableStateOf(0f) }


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
    // ORIENTATION (rotation vector -> azimuth -> bearing)
    // =========================
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            // Se manca, puoi fallbackare su accelerometer+magnetometer (più verboso).
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Rotation vector -> rotation matrix -> orientation (azimuth)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    // orientation[0] = azimuth in radianti (-pi..pi)
                    val azimuthRad = orientation[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()

                    // Normalizza 0..360
                    if (azimuthDeg < 0f) azimuthDeg += 360f

                    phoneBearing = azimuthDeg
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
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

    LaunchedEffect(followOn, userLatLng, phoneBearing, mapLoaded, didCenterOnce) {
        if (!followOn) return@LaunchedEffect
        if (!mapLoaded) return@LaunchedEffect
        if (!didCenterOnce) return@LaunchedEffect

        val current = cameraPositionState.position
        val updated = CameraPosition.Builder(current)
            .target(userLatLng)
            .tilt(fixedTilt)
            .bearing(phoneBearing) // <<< QUI
            .build()

        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(updated),
            durationMs = 200 // un po' più reattivo
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
    // =========================
    LaunchedEffect(Unit) {
        while (true) {
            val lat = userLatLng.latitude
            val lon = userLatLng.longitude

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
    // 5) PROFILE + FRIENDS + REQUESTS + SEARCH
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

    // >>>> TENIAMO LISTA AMICI IN STATE (così count non resta 0)
    var friends by remember { mutableStateOf<List<FriendDto>>(emptyList()) }
    val friendsCount = friends.size

    var incomingRequests by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }

    // search state
    var searchResults by remember { mutableStateOf<List<SearchUserDto>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var lastSearchQuery by remember { mutableStateOf("") }

    suspend fun refreshFriendsAndIncoming() {
        RealmsNetwork.repository.getFriends()
            .onSuccess { list -> friends = list }

        RealmsNetwork.repository.getIncomingFriendRequests()
            .onSuccess { list -> incomingRequests = list }
    }

    // load iniziale: me + friends + incoming
    LaunchedEffect(Unit) {
        RealmsNetwork.repository.getMe()
            .onSuccess { me ->
                profile = BasicProfileUi(
                    username = me.username,
                    firstName = me.firstName,
                    lastName = me.lastName,
                    bio = me.bio
                )
            }

        refreshFriendsAndIncoming()
    }

    // polling incoming requests (badge live)
    LaunchedEffect(Unit) {
        while (true) {
            RealmsNetwork.repository.getIncomingFriendRequests()
                .onSuccess { list -> incomingRequests = list }
            delay(12_000)
        }
    }

    fun handleSearchQuery(q: String) {
        val trimmed = q.trim()
        lastSearchQuery = trimmed

        searchJob?.cancel()
        if (trimmed.isBlank()) {
            searchResults = emptyList()
            return
        }

        searchJob = scope.launch {
            delay(250) // debounce
            if (lastSearchQuery != trimmed) return@launch

            RealmsNetwork.repository.searchUsers(trimmed)
                .onSuccess { list ->
                    if (lastSearchQuery == trimmed) searchResults = list
                }
                .onFailure {
                    if (lastSearchQuery == trimmed) searchResults = emptyList()
                }
        }
    }

    if (showEditProfile) {
        EditProfileScreen(
            initialUsername = profile.username,
            initialFirstName = profile.firstName,
            initialLastName = profile.lastName,
            initialBio = profile.bio,
            initialProfilePictureUrl = null,
            onSave = { newUsername, newFirstName, newLastName, newBio, _ ->
                editProfileError = null
                editProfileLoading = true

                scope.launch {
                    val result = RealmsNetwork.repository.updateMe(
                        username = newUsername,
                        firstName = newFirstName,
                        lastName = newLastName,
                        bio = newBio,
                        profilePhotoUrl = null // TODO: quando aggiungi foto
                    )

                    result
                        .onSuccess {
                            // Aggiorna UI locale (così vedi subito il cambio)
                            profile = profile.copy(
                                username = newUsername,
                                firstName = newFirstName,
                                lastName = newLastName,
                                bio = newBio
                            )
                            showEditProfile = false
                        }
                        .onFailure { e ->
                            editProfileError =
                                if (e.message?.contains("username already taken", ignoreCase = true) == true)
                                    "Username già preso"
                                else
                                    (e.message ?: "Errore aggiornamento profilo")
                        }

                    editProfileLoading = false
                }
            },
            onCancel = { showEditProfile = false }
        )
    } else
    ProfileBottomSheetBasic(
        profile = profile,
        onLoadUserProfile = { userId ->
            RealmsNetwork.repository.getUserProfile(userId)
        },

        friendsCount = friendsCount,
        friends = friends,

        incomingRequests = incomingRequests,
        onOpenSearch = { },

        onSendFriendRequest = { toUserId ->
            RealmsNetwork.repository.sendFriendRequest(toUserId)
        },

        onRemoveFriend = { friendUserId ->
            RealmsNetwork.repository.removeFriend(friendUserId).also {
                if (it.isSuccess) refreshFriendsAndIncoming()
            }
        },

        onAcceptIncoming = { requestId ->
            RealmsNetwork.repository.acceptFriendRequest(requestId).also {
                if (it.isSuccess) refreshFriendsAndIncoming()
            }
        },

        onRejectIncoming = { requestId ->
            RealmsNetwork.repository.rejectFriendRequest(requestId).also {
                if (it.isSuccess) refreshFriendsAndIncoming()
            }
        },

        searchResults = searchResults,
        onSearchQuery = { q -> handleSearchQuery(q) },

        contentBehind = { openUserProfile ->
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

                    nearbyUsers.forEach { u ->
                        val pos = LatLng(u.latitude, u.longitude)

                        val haptic = LocalHapticFeedback.current

                        val tint = remember(u.userId) { pickMarkerColor(u.userId) }
                        val icon = remember(u.userId, tint) {
                            bitmapDescriptorFromVector(context, R.drawable.ic_realm_pin, tint)
                        }

                        Marker(
                            state = MarkerState(position = pos),
                            title = u.userId,
                            icon = icon,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // vibrazione super light

                                selectedUser = u
                                showUserPopup = true

                                val uid = u.userId
                                profileError = null

                                // Se già in cache -> usa subito
                                val cached = profileCache[uid]
                                if (cached != null) {
                                    selectedUserProfile = cached
                                } else {
                                    selectedUserProfile = null
                                    profileLoading = true
                                    scope.launch {
                                        val res = RealmsNetwork.repository.getUserProfile(uid)
                                        res.onSuccess { dto ->
                                            profileCache[uid] = dto
                                            selectedUserProfile = dto
                                        }.onFailure { e ->
                                            profileError = e.message ?: "getUserProfile failed"
                                        }
                                        profileLoading = false
                                    }
                                }
                                true
                            }
                        )
                    }
                }

                // =========================
                // TOP BAR (meteo + settings)
                // =========================
                var showSettingsMenu by remember { mutableStateOf(false) }

                val tempText = when {
                    weatherError != null -> "--°"
                    weather != null -> "${weather!!.temperatureC}°"
                    else -> "--°"
                }

                val statusText = when {
                    weatherError != null -> "Offline"
                    weather != null -> weather!!.label
                    else -> "Loading…"
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(90.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 10.dp,
                    shadowElevation = 14.dp,
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .windowInsetsPadding(WindowInsets.statusBars),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tempText,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.weight(1.2f))

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings"
                                )
                            }

                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit profile") },
                                    onClick = {
                                        showSettingsMenu = false
                                        showEditProfile = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Logout") },
                                    onClick = {
                                        showSettingsMenu = false
                                        onLogout()
                                    }
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        followOn = true
                        if (mapLoaded) {
                            val current = cameraPositionState.position
                            val updated = CameraPosition.Builder(current)
                                .target(userLatLng)
                                .zoom(initialZoom)
                                .tilt(fixedTilt)
                                .bearing(phoneBearing)
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

                if (showUserPopup && selectedUser != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color(0x66000000))
                            .clickable {
                                showUserPopup = false
                                selectedUser = null
                                // non pulisco cache
                            },
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
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // HEADER: placeholder avatar + username
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("👤", color = Color.White)
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        val uname = selectedUserProfile?.username
                                        val titleText = when {
                                            profileLoading -> "Loading..."
                                            !uname.isNullOrBlank() -> "@$uname"
                                            else -> "@${selectedUser!!.userId.take(8)}"
                                        }

                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Error (se c'è)
                                    if (profileError != null) {
                                        Text(
                                            text = profileError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    // 3 BOTTONI (TODO azioni)
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val uid = selectedUser!!.userId
                                                showUserPopup = false
                                                selectedUser = null

                                                openUserProfile(uid)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Go to profile")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    // Primo center (una sola volta)
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

    // Clamp: attivo solo dopo il primo center e solo se follow è OFF
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

// Helpers (clamp)
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
    val lat3 =
        asinSafe(sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(bearing))
    val lon3 = lon1 + atan2(
        sin(bearing) * sin(angDist) * cos(lat1),
        cos(angDist) - sin(lat1) * sin(lat3)
    )

    return LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3))
}

private fun asinSafe(v: Double): Double =
    kotlin.math.asin(v.coerceIn(-1.0, 1.0))

// Fused helpers (one-shot)
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

// Fused helpers (real-time flow)
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


private fun pickMarkerColor(userId: String): Int {
    // palette tua (modifica liberamente)
    val palette = listOf(
        0xFF2EE6C5.toInt(),
        0xFF7C5CFF.toInt(),
        0xFFFFC93C.toInt(),
        0xFFFF5C8A.toInt(),
        0xFF4DA3FF.toInt(),
        0xFF6EEB83.toInt(),
        0xFFFF8F3F.toInt(),
    )
    val idx = abs(userId.hashCode()) % palette.size
    return palette[idx]
}

private fun bitmapDescriptorFromVector(
    context: Context,
    @DrawableRes vectorResId: Int,
    tintColor: Int,
    scale: Float = 1.25f // 1.2–1.3 circa
): BitmapDescriptor {
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId)
        ?: return BitmapDescriptorFactory.defaultMarker()

    drawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

    val width = (drawable.intrinsicWidth.coerceAtLeast(1) * scale).toInt()
    val height = (drawable.intrinsicHeight.coerceAtLeast(1) * scale).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
