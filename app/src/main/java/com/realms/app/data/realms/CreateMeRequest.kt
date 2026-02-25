package com.realms.app.data.realms

data class CreateMeRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String? = null,
    val profilePictureUrl: String? = null
)