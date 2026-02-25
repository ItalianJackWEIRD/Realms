package com.realms.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun EditProfileScreen(
    initialUsername: String = "",
    initialFirstName: String = "",
    initialLastName: String = "",
    initialBio: String? = null,
    initialProfilePictureUrl: String? = null,
    // onSave ora accetta l'Uri? finale invece della stringa URL
    onSave: (username: String, firstName: String, lastName: String, bio: String?, imageUri: Uri?) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf(initialUsername) }
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var bio by remember { mutableStateOf(initialBio.orEmpty()) }

    // Uri locale della foto selezionata dall'utente durante questa sessione
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher per il selettore immagini di Android
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally // Centra la foto
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                "Realms — Modifica profilo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(24.dp))

            // --- AVATAR / PHOTO PICKER ---
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Priorità: 1. Nuova foto scelta, 2. Foto esistente sul server, 3. Icona vuota
                val imageModel = selectedImageUri ?: initialProfilePictureUrl

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Foto profilo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Aggiungi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- CAMPI DI TESTO ---
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Cognome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio (opzionale)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
            )

            Spacer(Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
            }

            // --- AZIONI ---
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSave(
                            username.trim(),
                            firstName.trim(),
                            lastName.trim(),
                            bio.trim().ifBlank { null },
                            selectedImageUri // Passiamo l'Uri al ViewModel
                        )
                    },
                    enabled = !isLoading
                            && username.isNotBlank()
                            && firstName.isNotBlank()
                            && lastName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoading) "Salvataggio..." else "Salva")
                }

                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annulla")
                }
            }
        }
    }
}