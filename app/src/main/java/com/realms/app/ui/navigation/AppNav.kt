package com.realms.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNav() {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Checking Firebase...") }
    var details by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            // Prova a inizializzare (anche se già auto-init)
            FirebaseApp.initializeApp(context)

            val app = FirebaseApp.getInstance()
            val options: FirebaseOptions = app.options

            status = "✅ Firebase OK"
            details =
                "name=${app.name}\n" +
                        "projectId=${options.projectId}\n" +
                        "appId=${options.applicationId}\n" +
                        "apiKey=${options.apiKey.take(6)}..."

            Log.d("FB_MIN", "Firebase OK: $details")
        } catch (e: Exception) {
            status = "❌ Firebase NOT initialized"
            details = (e.message ?: e.toString())
            Log.e("FB_MIN", "Firebase FAIL", e)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(status, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(details)

            Spacer(Modifier.height(20.dp))

            Text(
                "Se vedi ✅ Firebase OK, allora il collegamento (google-services.json + plugin) è corretto.\n" +
                        "Se vedi ❌, non ha letto le options: problema di plugin/json."
            )
        }
    }
}
