package com.realms.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color


data class BasicProfileUi(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String?
    // profilePhotoUrl NON serve ancora: placeholder sempre nero
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheetBasic(
    profile: BasicProfileUi,
    friendsCount: Int,
    contentBehind: @Composable () -> Unit
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 170.dp, // la “barra grigia” visibile
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f) // <-- questa fa salire MOLTO di più lo sheet quando espanso
                    .padding(bottom = 24.dp) // alza l'area interattiva sopra i gesti di sistema
            ) {
                // GRIP grigia
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

                ProfileBasicContent(profile, friendsCount)
                Spacer(Modifier.height(24.dp))
            }
        }
    ) {
        contentBehind()
    }
}

@Composable
private fun ProfileBasicContent(profile: BasicProfileUi, friendsCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // TOP: foto a sinistra + username centrato (alla stessa altezza) + cerchio verde amici a destra
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp), // stessa altezza della foto
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto profilo (placeholder nero)
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
            )

            // Username centrato nello spazio disponibile
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

            // Cerchio verde (più piccolo) con numero amici
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


        Spacer(Modifier.height(12.dp))

        // Nome + cognome a sinistra
        Text(
            text = "${profile.firstName} ${profile.lastName}".trim(),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(6.dp))

        // Bio sotto
        val bioText = profile.bio?.takeIf { it.isNotBlank() } ?: "—"
        Text(
            text = bioText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (bioText == "—")
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.onSurface
        )

        // area post (placeholder, per dopo)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Post (in arrivo)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(120.dp))
    }
}
