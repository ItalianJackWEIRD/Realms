package com.realms.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditProfileScreen(
    initialUsername: String = "",
    initialFirstName: String = "",
    initialLastName: String = "",
    initialBio: String? = null,
    initialProfilePictureUrl: String? = null,
    onSave: (String, String, String, String?, String?) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf(initialUsername) }
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var bio by remember { mutableStateOf(initialBio.orEmpty()) }
    var profilePictureUrl by remember { mutableStateOf(initialProfilePictureUrl.orEmpty()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                "Realms — Modifica profilo",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(16.dp))

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
                    .heightIn(min = 56.dp)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = profilePictureUrl,
                onValueChange = { profilePictureUrl = it },
                label = { Text("Foto profilo URL (opzionale)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
            }

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
                            profilePictureUrl.trim().ifBlank { null }
                        )
                    },
                    enabled = !isLoading
                            && username.isNotBlank()
                            && firstName.isNotBlank()
                            && lastName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoading) "..." else "Salva")
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
