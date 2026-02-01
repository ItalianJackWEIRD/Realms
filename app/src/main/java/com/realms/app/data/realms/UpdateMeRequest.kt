package com.realms.app.data.realms

data class UpdateMeRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String? = null,
    val profilePhotoUrl: String? = null
)
