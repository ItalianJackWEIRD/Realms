package com.realms.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.realms.app.data.realms.MapPostDto
import com.realms.app.ui.utils.timeAgoEn
import androidx.compose.material.icons.filled.Image

data class FeedSection(
    val ownerUserId: String,
    val username: String,
    val hasFriendsPosts: Boolean,
    val posts: List<MapPostDto>
)

private fun isFriends(p: MapPostDto) = p.visibility.equals("FRIENDS", ignoreCase = true)

fun buildFeedSections(posts: List<MapPostDto>): List<FeedSection> {
    val sections = posts
        .groupBy { it.ownerUserId }
        .map { (uid, userPosts) ->

            val username = userPosts.firstOrNull()?.owner?.username?.takeIf { it.isNotBlank() }
                ?: uid.take(8)

            // Dentro gruppo: FRIENDS -> PUBLIC, e poi data desc
            val ordered = userPosts.sortedWith(
                compareByDescending<MapPostDto> { isFriends(it) }
                    .thenByDescending { it.createdAtUtc }
            )

            FeedSection(
                ownerUserId = uid,
                username = "@$username",
                hasFriendsPosts = ordered.any { isFriends(it) },
                posts = ordered
            )
        }

    // Gruppi: chi ha FRIENDS prima, poi PUBLIC, poi username
    return sections.sortedWith(
        compareByDescending<FeedSection> { it.hasFriendsPosts }
            .thenBy { it.username.lowercase() }
    )
}

@Composable
fun FeedOverlay(
    posts: List<MapPostDto>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPostClick: (MapPostDto) -> Unit,
    onClose: () -> Unit
) {
    val sections = buildFeedSections(posts)

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.80f),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends Feed",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Divider()

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }

                sections.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No posts yet.")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 18.dp)
                    ) {
                        sections.forEach { section ->
                            item(key = "h_${section.ownerUserId}") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Avatar piccolo nell'header del feed
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Recuperiamo l'URL dal campo profilePhotoUrl dell'owner del primo post
                                            val avatarUrl = section.posts.firstOrNull()?.owner?.profilePhotoUrl
                                            if (!avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = avatarUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text("👤", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Text(
                                            text = section.username,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Divider()
                                }
                            }

                            items(section.posts, key = { it.id }) { p ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .clickable { onPostClick(p) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically // Centra icone e testo
                                        ) {
                                            Text(
                                                if (isFriends(p)) "FRIENDS" else "PUBLIC",
                                                style = MaterialTheme.typography.labelMedium
                                            )

                                            // Se il post ha una foto, mostriamo l'iconcina accanto alla scritta della visibilità
                                            if (!p.photoUrl.isNullOrBlank()) {
                                                Spacer(Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = "Contiene immagine",
                                                    modifier = Modifier.size(14.dp), // Piccola per non disturbare
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }

                                            Spacer(Modifier.weight(1f))

                                            Text(
                                                text = timeAgoEn(p.createdAtUtc),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(p.caption ?: "No text", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
