package com.realms.app.data.realms

import com.google.gson.annotations.SerializedName

data class FriendDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    @SerializedName("profilePhotoUrl")
    val profilePhotoUrl: String?
)
