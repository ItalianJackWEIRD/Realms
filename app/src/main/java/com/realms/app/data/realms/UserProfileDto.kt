package com.realms.app.data.realms

data class UserProfileDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String?,
    val profilePhotoUrl: String?,
    val friendsCount: Int,
    val isFriend: Boolean
)
