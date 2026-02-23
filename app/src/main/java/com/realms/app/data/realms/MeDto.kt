package com.realms.app.data.realms

import com.google.gson.annotations.SerializedName

data class MeDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String?,
    @SerializedName("profilePhotoUrl")
    val profilePhotoUrl: String?,
    val friendsCount: Int
)
