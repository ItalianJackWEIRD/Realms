package com.realms.app.ui.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.realms.app.data.realms.MapPostDto

@Composable
fun PostPopup(
    post: MapPostDto,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    // opzionali per avere la stessa logica del popup mappa
    canDelete: Boolean = false,
    onDelete: (() -> Unit)? = null,
    postsError: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // banner "owner" cliccabile: riusa onOpenProfile
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenProfile(post.ownerUserId)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) { Text("👤", color = Color.White) }

                    Spacer(Modifier.width(12.dp))

                    val title = post.owner?.username
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "@$it" }
                        ?: "@${post.ownerUserId.take(8)}"

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val isFriends = post.visibility.equals("FRIENDS", ignoreCase = true)

                    if (isFriends) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2ECC71) // verde
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Friends only",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(18.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFBDBDBD) // grigio
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Public",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(18.dp)
                            )
                        }
                    }
                }

                Text(post.caption ?: "Nessun testo")

                if (!post.photoUrl.isNullOrBlank()) {
                    Text("Foto: TODO render")
                } else {
                    Text("Nessuna foto")
                }

                if (postsError != null) {
                    Text(
                        text = postsError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
        dismissButton = {
            if (canDelete && onDelete != null) {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Elimina") }
            }
        }
    )
}
