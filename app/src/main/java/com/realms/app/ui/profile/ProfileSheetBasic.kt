package com.realms.app.ui.profile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.realms.app.data.realms.FriendDto
import com.realms.app.data.realms.FriendRequestDto
import com.realms.app.data.realms.SearchUserDto
import com.realms.app.data.realms.UserProfileDto
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.realms.app.data.realms.RealmsNetwork
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.style.TextAlign


data class BasicProfileUi(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheetBasic(
    profile: BasicProfileUi,
    onLoadUserProfile: suspend (userId: String) -> Result<UserProfileDto>,
    friendsCount: Int,
    friends: List<FriendDto>,

    incomingRequests: List<FriendRequestDto>,

    onOpenSearch: () -> Unit,

    // add friend = manda richiesta
    onSendFriendRequest: suspend (toUserId: String) -> Result<Unit>,

    // remove friend = DELETE friendship
    onRemoveFriend: suspend (friendUserId: String) -> Result<Unit>,

    onAcceptIncoming: suspend (requestId: Long) -> Result<Unit>,
    onRejectIncoming: suspend (requestId: Long) -> Result<Unit>,

    searchResults: List<SearchUserDto>,
    onSearchQuery: (String) -> Unit,

    contentBehind: @Composable (openUserProfile: (String) -> Unit) -> Unit
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    var showSearchDialog by remember { mutableStateOf(false) }
    var showIncomingDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val usernameById = remember { mutableStateMapOf<String, String>() }


    // Popup profilo utente esterno (da search o da friends list)
    var showUserProfileDialog by remember { mutableStateOf(false) }

    // Qui teniamo sempre e solo:
    // - o FriendDto (se è amico)
    // - o SearchUserDto (se viene dal search e non è amico)
    var selectedFriend by remember { mutableStateOf<FriendDto?>(null) }
    var selectedSearchUser by remember { mutableStateOf<SearchUserDto?>(null) }

    var forcedUserId by remember { mutableStateOf<String?>(null) }

    var searchText by remember { mutableStateOf("") }

    // teniamo il nostro id per checks
    var myUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        RealmsNetwork.repository.getMe()
            .onSuccess { me -> myUserId = me.id }
    }


    fun openUserProfileFromSearch(u: SearchUserDto) {
        // se è già amico, preferisci FriendDto (per nome/cognome)
        val match = friends.firstOrNull { it.id == u.id }
        selectedFriend = match
        selectedSearchUser = if (match == null) u else null
        showUserProfileDialog = true
    }

    fun openUserProfileById(userId: String) {
        // non serve username qui: la dialog chiama onLoadUserProfile(userId) e lo ottiene dal backend
        selectedFriend = friends.firstOrNull { it.id == userId } // se ce l'hai già tra gli amici ok
        selectedSearchUser = null // oppure puoi lasciarlo null sempre
        showUserProfileDialog = true
    }


    fun openUserProfileFromFriend(f: FriendDto) {
        selectedFriend = f
        selectedSearchUser = null
        showUserProfileDialog = true
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 170.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(bottom = 24.dp)
            ) {
                // GRIP
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(width = 46.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    )
                }

                ProfileBasicContent(
                    profile = profile,
                    friendsCount = friendsCount,
                    incomingCount = incomingRequests.size,
                    onClickSearch = {
                        showSearchDialog = true
                        onOpenSearch()
                    },
                    onClickIncoming = {
                        showIncomingDialog = true

                        // prendi gli id unici delle richieste
                        val idsToFetch = incomingRequests
                            .map { it.fromUserId }
                            .distinct()
                            .filter { it !in usernameById } // evita richieste duplicate se riapri

                        if (idsToFetch.isNotEmpty()) {
                            scope.launch {
                                runCatching {
                                    RealmsNetwork.repository.getUsernameMap(idsToFetch)
                                }.onSuccess { map ->
                                    usernameById.putAll(map)
                                }
                                // se fallisce: non succede niente, resta il fallback @id...
                            }
                        }
                    },
                    onClickFriends = { showFriendsDialog = true }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    ) { contentBehind(::openUserProfileById) }

    // =========================
    // DIALOG: SEARCH USERS
    // =========================
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Cerca utenti") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            onSearchQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Cerca per username...") }
                    )

                    Spacer(Modifier.height(12.dp))

                    if (searchText.isBlank()) {
                        Text(
                            "Scrivi un username per cercare.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(searchResults, key = { it.id }) { u ->
                                SearchUserRow(
                                    user = u,
                                    onOpen = { openUserProfileFromSearch(u) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Chiudi") }
            }
        )
    }

    // =========================
    // DIALOG: INCOMING REQUESTS
    // =========================
    if (showIncomingDialog) {
        AlertDialog(
            onDismissRequest = { showIncomingDialog = false },
            title = { Text("Richieste amicizia") },
            text = {
                if (incomingRequests.isEmpty()) {
                    Text(
                        "Nessuna richiesta in arrivo.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(incomingRequests, key = { it.id }) { req ->
                            val fromUsername =
                                usernameById[req.fromUserId]
                                    ?: friends.firstOrNull { it.id == req.fromUserId }?.username
                                    ?: ("@" + req.fromUserId.take(8))

                            IncomingRequestRow(
                                req = req,
                                fromUsername = fromUsername,
                                onAccept = onAcceptIncoming,
                                onReject = onRejectIncoming
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIncomingDialog = false }) { Text("Chiudi") }
            }
        )
    }

    // =========================
    // DIALOG: FRIENDS LIST
    // =========================
    if (showFriendsDialog) {
        AlertDialog(
            onDismissRequest = { showFriendsDialog = false },
            title = { Text("Amici") },
            text = {
                if (friends.isEmpty()) {
                    Text(
                        "Ancora nessun amico.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(friends, key = { it.id }) { f ->
                            FriendRow(
                                friend = f,
                                onOpen = { openUserProfileFromFriend(f) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFriendsDialog = false }) { Text("Chiudi") }
            }
        )
    }

    // =========================
    // DIALOG: USER PROFILE (POPUP GRANDE)
    // =========================
    if (showUserProfileDialog) {
        val friend = selectedFriend
        val searchUser = selectedSearchUser
        val userId = forcedUserId ?: friend?.id ?: searchUser?.id

        if (userId == null) {
            showUserProfileDialog = false
        } else {
            var loaded by remember(userId) { mutableStateOf<UserProfileDto?>(null) }
            var loading by remember(userId) { mutableStateOf(true) }

            suspend fun refreshProfile() {
                loading = true
                loaded = null
                onLoadUserProfile(userId).onSuccess { loaded = it }
                loading = false
            }

            LaunchedEffect(userId) { refreshProfile() }

            UserProfileDialog(
                userId = userId,
                myUserId = myUserId,
                loaded = loaded,
                loading = loading,
                onDismiss = {
                    showUserProfileDialog = false
                    forcedUserId = null
                },
                onAdd = onSendFriendRequest,
                onRemove = onRemoveFriend,
                onRefresh = { refreshProfile() }
            )
        }
    }
}

@Composable
private fun ProfileBasicContent(
    profile: BasicProfileUi,
    friendsCount: Int,
    incomingCount: Int,
    onClickSearch: () -> Unit,
    onClickIncoming: () -> Unit,
    onClickFriends: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2ECC71))
                    .clickable { onClickFriends() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friendsCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClickSearch,
                modifier = Modifier.weight(1f)
            ) { Text("Cerca amici") }

            BadgedBox(
                badge = {
                    if (incomingCount > 0) Badge { Text(incomingCount.toString()) }
                }
            ) {
                OutlinedButton(
                    onClick = onClickIncoming,
                    modifier = Modifier.widthIn(min = 120.dp)
                ) { Text("Richieste") }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "${profile.firstName} ${profile.lastName}".trim(),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(6.dp))

        val bioText = profile.bio?.takeIf { it.isNotBlank() } ?: "—"
        Text(
            text = bioText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (bioText == "—")
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Post (in arrivo)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SearchUserRow(
    user: SearchUserDto,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = "@${user.username}",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun IncomingRequestRow(
    req: FriendRequestDto,
    fromUsername: String,
    onAccept: suspend (Long) -> Result<Unit>,
    onReject: suspend (Long) -> Result<Unit>
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = fromUsername,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            enabled = !working,
            onClick = {
                working = true
                scope.launch {
                    onReject(req.id)
                    working = false
                }
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = "Reject",
                tint = Color(0xFFE74C3C)
            )
        }

        IconButton(
            enabled = !working,
            onClick = {
                working = true
                scope.launch {
                    onAccept(req.id)
                    working = false
                }
            }
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Accept",
                tint = Color(0xFF2ECC71)
            )
        }
    }

    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun FriendRow(
    friend: FriendDto,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = "@${friend.username}",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun UserProfileDialog(
    userId: String,
    myUserId: String?,
    loaded: UserProfileDto?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Result<Unit>,
    onRemove: suspend (String) -> Result<Unit>,
    onRefresh: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    40, // ms → breve e secca
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }



    val latestOnRefresh by rememberUpdatedState(onRefresh)

    var working by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    var isFriendState by remember(userId) { mutableStateOf(loaded?.isFriend == true) }
    var pendingState by remember(userId) { mutableStateOf(false) }

    LaunchedEffect(loaded?.isFriend) {
        if (loaded != null) {
            isFriendState = loaded.isFriend
            if (loaded.isFriend) pendingState = false
        }
    }

    // ---- SHAKE TO REFRESH (attivo solo mentre il dialog è visibile) ----
    DisposableEffect(userId, context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // parametri shake
        val shakeThresholdG = 2.7f
        val minShakeIntervalMs = 900L

        var lastShakeTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (accel == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val gX = x / SensorManager.GRAVITY_EARTH
                val gY = y / SensorManager.GRAVITY_EARTH
                val gZ = z / SensorManager.GRAVITY_EARTH

                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                val now = System.currentTimeMillis()
                if (gForce > shakeThresholdG && now - lastShakeTime > minShakeIntervalMs) {
                    lastShakeTime = now

                    if (!working && !loading && !refreshing) {
                        scope.launch {
                            vibrate()

                            // forza un "visual refresh" immediato:
                            refreshing = true
                            feedback = "Refreshing..."
                            latestOnRefresh()
                            feedback = "Refreshed ✓"
                            refreshing = false
                        }
                    }

                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accel != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    // -------------------------------------------------------------------

    val addColor = Color(0xFF2EE6C5)
    val removeColor = Color(0xFF8B1E1E)
    val pendingColor = Color(0xFF4B4B4B)

    val username = loaded?.username ?: ("@" + userId.take(8))
    val fullName = listOfNotNull(loaded?.firstName, loaded?.lastName).joinToString(" ").trim()
    val bioText = loaded?.bio?.takeIf { it.isNotBlank() } ?: "—"
    val friendsCount = loaded?.friendsCount ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "@$username".removePrefix("@@"),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = friendsCount.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (fullName.isNotBlank()) fullName else "—",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (loading) "Caricamento..." else bioText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!loading && bioText == "—")
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                if (refreshing) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Aggiornamento...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                if (feedback != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = feedback!!,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (working || loading || pendingState) return@Button
                        working = true
                        feedback = null

                        scope.launch {
                            val res = if (!isFriendState) onAdd(userId) else onRemove(userId)

                            if (res.isSuccess) {
                                if (!isFriendState) {
                                    pendingState = true
                                    feedback = "Richiesta inviata"
                                } else {
                                    isFriendState = false
                                    pendingState = false
                                    feedback = "Amicizia rimossa"
                                }

                                refreshing = true
                                latestOnRefresh()
                                refreshing = false
                            } else {
                                feedback = "Operazione fallita"
                            }

                            working = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working && !loading && !pendingState,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            pendingState -> pendingColor
                            isFriendState -> removeColor
                            else -> addColor
                        }
                    )
                ) {
                    Text(
                        text = when {
                            loading -> "..."
                            working -> "..."
                            pendingState -> "Pending"
                            isFriendState -> "Rimuovi amico"
                            else -> "Aggiungi amico"
                        },
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(18.dp))

                val isMe = (myUserId != null && userId == myUserId)
                val canSeePosts = isMe || isFriendState

                if (!canSeePosts) {
                    // BLOCCO POST: locked
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "You need to be friends with this person to see their posts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2ECC71)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(26.dp)
                                )
                            }
                        }
                    }
                } else {
                    // BLOCCO POST: normale (placeholder)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = "Post (in arrivo)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(Modifier.height(12.dp))

                        // TODO: lista post
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
