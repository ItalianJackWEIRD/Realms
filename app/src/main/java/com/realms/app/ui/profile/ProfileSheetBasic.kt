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
import com.realms.app.ui.posts.PostPopup
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.shape.RoundedCornerShape
import com.realms.app.data.realms.RealmsNetwork
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.style.TextAlign
import com.realms.app.data.realms.MapPostDto
import java.time.Instant
import java.time.Duration
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.google.gson.annotations.SerializedName
import androidx.compose.material.icons.filled.Image

data class BasicProfileUi(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String?,
    @SerializedName("profilePhotoUrl")
    val profilePhotoUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheetBasic(
    profile: BasicProfileUi,
    postRefreshTrigger: Int,
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
    val photoById = remember { mutableStateMapOf<String, String?>() }

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

    //Post sulla mia pagina
    // ===== MY POSTS (per il profilo nella bottom sheet) =====
    var myPosts by remember { mutableStateOf<List<MapPostDto>>(emptyList()) }
    var myPostsLoading by remember { mutableStateOf(false) }
    var myPostsError by remember { mutableStateOf<String?>(null) }

    var mySelectedPost by remember { mutableStateOf<MapPostDto?>(null) }
    var showMyPostPopup by remember { mutableStateOf(false) }

    suspend fun refreshMyPosts() {
        val uid = myUserId ?: return
        myPostsLoading = true
        myPostsError = null
        try {
            myPosts = RealmsNetwork.repository.getUserPosts(userId = uid, max = 100)
        } catch (e: Exception) {
            myPosts = emptyList()
            myPostsError = e.message ?: "getUserPosts failed"
        }
        myPostsLoading = false
    }

    LaunchedEffect(myUserId, postRefreshTrigger) {
        if (myUserId != null) refreshMyPosts()
    }

    suspend fun deleteMyPost(postId: Int) {
        myPostsError = null
        try {
            RealmsNetwork.repository.deletePost(postId)
            refreshMyPosts()
        } catch (e: Exception) {
            myPostsError = e.message ?: "delete post failed"
        }
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
                        val idsToFetch = incomingRequests.map { it.fromUserId }.distinct()

                        scope.launch {
                            idsToFetch.forEach { uid ->
                                if (uid !in photoById) {
                                    RealmsNetwork.repository.getUserProfile(uid).onSuccess { userDto ->
                                        usernameById[uid] = userDto.username
                                        photoById[uid] = userDto.profilePhotoUrl // Salviamo la foto!
                                    }
                                }
                            }
                        }
                    },
                    onClickFriends = { showFriendsDialog = true },

                    // >>> nuove props per posts del mio profilo
                    myPosts = myPosts,
                    myPostsLoading = myPostsLoading,
                    myPostsError = myPostsError,
                    onPostClick = { p ->
                        mySelectedPost = p
                        showMyPostPopup = true
                    }
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
                            val fromUsername = usernameById[req.fromUserId] ?: "@${req.fromUserId.take(8)}"
                            val fromPhoto = photoById[req.fromUserId] // Prendi la foto dalla mappa

                            IncomingRequestRow(
                                req = req,
                                fromUsername = fromUsername,
                                fromPhotoUrl = fromPhoto, // Passala qui
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
                onRefresh = { refreshProfile() },
                openUserProfile = { uid -> openUserProfileById(uid) }
            )
        }
    }

    // ===== POST POPUP (per i post del mio profilo in bottom sheet) =====
    if (showMyPostPopup && mySelectedPost != null) {
        val p = mySelectedPost!!

        val canDelete = true // sono sempre i miei post qui

        PostPopup(
            post = p,
            onDismiss = {
                showMyPostPopup = false
                mySelectedPost = null
            },
            onOpenProfile = { uid ->
                // qui sei già "tu", quindi puoi anche non fare nulla
                // oppure: openUserProfileById(uid)
            },
            canDelete = canDelete,
            onDelete = {
                scope.launch {
                    deleteMyPost(p.id)
                    showMyPostPopup = false
                    mySelectedPost = null
                }
            },
            postsError = myPostsError
        )
    }
}

@Composable
private fun ProfileBasicContent(
    profile: BasicProfileUi,
    friendsCount: Int,
    incomingCount: Int,
    onClickSearch: () -> Unit,
    onClickIncoming: () -> Unit,
    onClickFriends: () -> Unit,

    // >>> aggiunte per mostrare i post nel profilo "mio" (bottom sheet)
    myPosts: List<MapPostDto>,
    myPostsLoading: Boolean,
    myPostsError: String?,
    onPostClick: (MapPostDto) -> Unit
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
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!profile.profilePhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.profilePhotoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("👤", style = MaterialTheme.typography.headlineLarge)
                }
            }

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
            text = "Posts",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(10.dp))

        // Riquadro scrollabile come negli altri profili (duplicato semplice)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 420.dp)
                .background(Color.White)
        ) {
            when {
                myPostsLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                myPostsError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = myPostsError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                myPosts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No posts yet",
                            color = Color(0xFF666666),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(myPosts, key = { it.id }) { p ->
                            UserPostRow(
                                post = p,
                                onClick = { onPostClick(p) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (!user.profilePhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.profilePhotoUrl,
                    contentDescription = "Foto di ${user.username}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Icona di default se l'utente non ha una foto
                Text("👤", style = MaterialTheme.typography.bodyLarge)
            }
        }

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
    fromPhotoUrl: String?,
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
                // Usiamo un grigio leggero come sfondo mentre carica o se è vuoto
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (!fromPhotoUrl.isNullOrBlank()) {
                // Se abbiamo l'URL, Coil scarica e mostra la foto
                AsyncImage(
                    model = fromPhotoUrl,
                    contentDescription = "Foto profilo di $fromUsername",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback: se non c'è la foto, mettiamo l'emoji o un'icona
                Text("👤", style = MaterialTheme.typography.bodyLarge)
            }
        }

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
        // --- BOX AVATAR AMICO ---
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (!friend.profilePhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = friend.profilePhotoUrl,
                    contentDescription = "Foto di ${friend.username}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback se l'amico non ha una foto
                Text("👤")
            }
        }

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
private fun UserPostRow(
    post: MapPostDto,
    onClick: () -> Unit
) {
    val who = post.owner?.username?.let { "@$it" } ?: "@${post.ownerUserId.take(8)}"
    val whenText = timeAgoLabel(post.createdAtUtc)
    val caption = post.caption?.trim().orEmpty()
    val captionShort = if (caption.length > 80) caption.take(80) + "…" else caption

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF2B2B2B).copy(alpha = 0.92f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!post.owner?.profilePhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.owner?.profilePhotoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("👤", color = Color.White)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (whenText.isNotBlank()) "$who · $whenText" else who,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Sostituisci il vecchio blocco Text con questo:
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 1. Mostra l'icona solo se è presente una foto
                    if (!post.photoUrl.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        // Spazio tra icona e il trattino
                        Spacer(Modifier.width(4.dp))
                    }

                    // 2. Il trattino separatore (sempre presente o condizionale)
                    Text(
                        text = "— ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    // 3. Il testo della caption
                    Text(
                        text = if (captionShort.isNotBlank()) captionShort else "No text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false) // Evita che il testo spinga fuori l'icona
                    )
                }
            }
        }
    }
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
    onRefresh: suspend () -> Unit,
    openUserProfile: (String) -> Unit // <<< AGGIUNTO
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

    var userPosts by remember(userId) { mutableStateOf<List<MapPostDto>>(emptyList()) }
    var postsLoading by remember(userId) { mutableStateOf(false) }
    var postsError by remember(userId) { mutableStateOf<String?>(null) }

    var selectedPost by remember { mutableStateOf<MapPostDto?>(null) }
    var showPostPopup by remember { mutableStateOf(false) }

    val isMe = (myUserId != null && userId == myUserId)
    val canSeePosts = isMe || isFriendState

    suspend fun refreshPosts() {
        if (!canSeePosts) return
        postsLoading = true
        postsError = null
        try {
            userPosts = RealmsNetwork.repository.getUserPosts(userId = userId, max = 100)
        } catch (e: Exception) {
            userPosts = emptyList()
            postsError = e.message ?: "getUserPosts failed"
        }
        postsLoading = false
    }

    LaunchedEffect(userId, canSeePosts) {
        if (canSeePosts) refreshPosts()
    }


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
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Usiamo l'URL che arriva dall'oggetto 'loaded'
                        if (!loaded?.profilePhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = loaded?.profilePhotoUrl,
                                contentDescription = "Foto di ${loaded?.username}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Fallback: icona di default se l'utente non ha una foto
                            Text("👤", style = MaterialTheme.typography.headlineLarge)
                        }
                    }

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
                    // LOCK (come avevamo fatto)
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

                            Surface(shape = CircleShape, color = Color(0xFF2ECC71)) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.White,
                                    modifier = Modifier.padding(12.dp).size(26.dp)
                                )
                            }
                        }
                    }
                } else {
                    // LISTA POST
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // titolo (puoi anche toglierlo se vuoi solo riquadro)
                        Text(
                            text = "Posts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(Modifier.height(10.dp))

                        // riquadro scrollabile
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.White)
                        ) {
                            when {
                                postsLoading -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }

                                postsError != null -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = postsError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                userPosts.isEmpty() -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "No posts yet",
                                            color = Color(0xFF666666),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                else -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(userPosts, key = { it.id }) { p ->
                                            UserPostRow(
                                                post = p,
                                                onClick = {
                                                    selectedPost = p
                                                    showPostPopup = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showPostPopup && selectedPost != null) {
            val p = selectedPost!!
            val canDelete = (myUserId != null && myUserId == p.ownerUserId)

            PostPopup(
                post = p,
                onDismiss = {
                    showPostPopup = false
                    selectedPost = null
                },
                onOpenProfile = { uid ->
                    // 1) chiudi popup
                    showPostPopup = false
                    selectedPost = null

                    // 2) se vuoi: evita di riaprire lo stesso profilo
                    if (uid != userId) {
                        // chiudi anche il dialog profilo corrente (opzionale: io lo farei)
                        onDismiss()
                        openUserProfile(uid)
                    }
                },
                canDelete = canDelete,
                onDelete = {
                    // delete + refresh lista profilo
                    scope.launch {
                        postsError = null
                        try {
                            RealmsNetwork.repository.deletePost(p.id)
                            showPostPopup = false
                            selectedPost = null
                            refreshPosts() // <<< refresh lista profilo
                        } catch (e: Exception) {
                            postsError = e.message ?: "delete post failed"
                        }
                    }
                },
                postsError = postsError
            )
        }
    }
}

private fun timeAgoLabel(createdAtUtc: String?): String {
    if (createdAtUtc.isNullOrBlank()) return ""
    return try {
        val created = Instant.parse(createdAtUtc)
        val now = Instant.now()
        val d = Duration.between(created, now)

        when {
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()}m ago"
            d.toDays() < 1 -> "${d.toHours()}h ago"
            else -> "${d.toDays()}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}
