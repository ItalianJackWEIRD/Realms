package com.realms.app.data.realms

import com.google.gson.annotations.SerializedName

data class SearchUserDto(
    val id: String,
    val username: String,
    @SerializedName("profilePhotoUrl")
    val profilePhotoUrl: String? = null
)