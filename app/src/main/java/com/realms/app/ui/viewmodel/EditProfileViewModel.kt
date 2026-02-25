package com.realms.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realms.app.data.realms.RealmsRepository
import com.realms.app.ui.utils.ImageUtils
import kotlinx.coroutines.launch

class EditProfileViewModel(private val repository: com.realms.app.data.realms.RealmsRepository) : androidx.lifecycle.ViewModel() {
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)

    fun updateProfile(context: android.content.Context, username: String, firstName: String, lastName: String, bio: String?, imageUri: Uri?) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                var photoUrl: String? = null

                // 1. Upload foto (se selezionata)
                imageUri?.let { uri ->
                    val bytes = com.realms.app.ui.utils.ImageUtils.getResizedImageBytes(context, uri)
                    if (bytes != null) {
                        photoUrl = repository.uploadProfilePicture(bytes).getOrThrow()
                    }
                }

                // 2. Update profilo
                repository.updateMe(username, firstName, lastName, bio, photoUrl).getOrThrow()

                isSuccess = true
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }
}