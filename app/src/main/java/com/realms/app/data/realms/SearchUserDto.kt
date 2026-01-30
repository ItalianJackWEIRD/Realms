package com.realms.app.data.realms

data class SearchUserDto(
    val id: String,
    val username: String,
    val profilePhotoUrl: String? = null
)