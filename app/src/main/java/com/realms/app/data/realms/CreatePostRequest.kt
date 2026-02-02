package com.realms.app.data.realms

data class CreatePostRequest(
    val caption: String?,
    val photoUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val visibility: String // "PUBLIC" | "FRIENDS"
)