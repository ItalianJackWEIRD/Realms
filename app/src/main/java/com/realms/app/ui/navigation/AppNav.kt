package com.realms.app.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.realms.app.auth.AuthUiState
import com.realms.app.data.realms.RealmsNetwork
import com.realms.app.ui.screen.LoginScreen
import com.realms.app.ui.screen.MapScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var uiState by remember {
        mutableStateOf(
            AuthUiState(
                user = auth.currentUser,
                isLoading = false,
                errorMessage = null
            )
        )
    }

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
                onSignUp = { email, password, username, firstName, lastName, bio ->
                    uiState = uiState.copy(isLoading = true, errorMessage = null)

                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            scope.launch {
                                val res = RealmsNetwork.repository.createMe(
                                    username = username,
                                    firstName = firstName,
                                    lastName = lastName,
                                    bio = bio?.takeIf { it.isNotBlank() },
                                    profilePictureUrl = null
                                )

                                if (res.isFailure) {
                                    auth.signOut()
                                    uiState = uiState.copy(
                                        isLoading = false,
                                        errorMessage = res.exceptionOrNull()?.message
                                            ?: "Errore backend (createMe)"
                                    )
                                } else {
                                    uiState = uiState.copy(isLoading = false, errorMessage = null)
                                }
                            }
                        }
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
