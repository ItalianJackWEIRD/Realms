package com.realms.app.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.realms.app.auth.AuthUiState
import com.realms.app.ui.screen.LoginScreen
import com.realms.app.ui.screen.MapScreen

private object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }

    var uiState by remember {
        mutableStateOf(
            AuthUiState(
                user = auth.currentUser,
                isLoading = false,
                errorMessage = null
            )
        )
    }

    // Listener: aggiorna user su login/logout
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            uiState = uiState.copy(
                user = fa.currentUser,
                isLoading = false
            )
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {

            LaunchedEffect(uiState.user) {
                if (uiState.user != null) {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            LoginScreen(
                uiState = uiState,
                onSignIn = { email, password ->
                    uiState = uiState.copy(isLoading = true, errorMessage = null)

                    auth.signInWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Errore login"
                            )
                        }
                },
                onSignUp = { email, password ->
                    uiState = uiState.copy(isLoading = true, errorMessage = null)

                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Errore registrazione"
                            )
                        }
                }
            )
        }

        composable(Routes.MAP) {

            LaunchedEffect(uiState.user) {
                if (uiState.user == null) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAP) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            MapScreen(
                onLogout = { auth.signOut() }
            )
        }
    }
}
