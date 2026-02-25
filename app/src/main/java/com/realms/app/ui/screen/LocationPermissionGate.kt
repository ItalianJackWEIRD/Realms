package com.realms.app.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class LocationPermState {
    FINE_GRANTED,
    COARSE_ONLY,        // approximate granted, precise denied
    DENIED,
    PERMANENTLY_DENIED  // "Don't ask again" for FINE
}

@Composable
fun LocationPermissionGate(
    onGranted: @Composable () -> Unit,
    onFallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Per distinguere "prima richiesta" vs "don't ask again"
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    fun computeState(): LocationPermState {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine) return LocationPermState.FINE_GRANTED
        if (coarse) return LocationPermState.COARSE_ONLY

        val permanentlyDeniedFine =
            askedOnce &&
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )

        return if (permanentlyDeniedFine) LocationPermState.PERMANENTLY_DENIED
        else LocationPermState.DENIED
    }

    // ✅ Stato Compose: questo fa “refresh” immediato
    var permState by rememberSaveable { mutableStateOf(computeState()) }

    // ✅ Quando torni dalle impostazioni o l’app riprende, ricontrolla
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permState = computeState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        askedOnce = true
        // ✅ refresh immediato dopo la scelta del popup
        permState = computeState()
    }

    when (permState) {
        LocationPermState.FINE_GRANTED -> {
            onGranted()
        }

        LocationPermState.COARSE_ONLY -> {
            PermissionExplainScreen(
                title = "Posizione approssimativa attiva",
                body = "Hai concesso la posizione approssimativa. Per usare Realms serve la posizione precisa.",
                primaryButtonText = "Passa a precisa",
                onPrimaryClick = {
                    askedOnce = true
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                secondaryButtonText = "Continua senza GPS",
                onSecondaryClick = { },
                renderFallback = onFallback
            )
        }

        LocationPermState.PERMANENTLY_DENIED -> {
            PermissionExplainScreen(
                title = "Posizione disattivata",
                body = "Hai negato il permesso in modo definitivo. Apri le impostazioni e abilita la posizione precisa.",
                primaryButtonText = "Apri impostazioni",
                onPrimaryClick = { context.openAppSettings() },
                secondaryButtonText = "Continua senza GPS",
                onSecondaryClick = { },
                renderFallback = onFallback
            )
        }

        LocationPermState.DENIED -> {
            PermissionExplainScreen(
                title = "Permesso posizione",
                body = "Realms usa la tua posizione per mostrarti la mappa nel raggio di 500m. Puoi abilitarla ora.",
                primaryButtonText = "Abilita posizione",
                onPrimaryClick = {
                    askedOnce = true
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                secondaryButtonText = "Continua senza GPS",
                onSecondaryClick = { },
                renderFallback = onFallback
            )
        }
    }
}

@Composable
private fun PermissionExplainScreen(
    title: String,
    body: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    renderFallback: @Composable () -> Unit
) {
    var showFallback by rememberSaveable { mutableStateOf(false) }

    if (showFallback) {
        renderFallback()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title)
        Spacer(Modifier.height(10.dp))
        Text(text = body)
        Spacer(Modifier.height(20.dp))

        Button(onClick = onPrimaryClick) { Text(primaryButtonText) }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                onSecondaryClick()
                showFallback = true
            }
        ) { Text(secondaryButtonText) }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
