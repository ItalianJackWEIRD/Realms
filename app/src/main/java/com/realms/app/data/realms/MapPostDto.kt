package com.realms.app.data.realms

data class MapPostDto(
    val id: Int,
    val ownerUserId: String,
    val owner: PostOwnerDto?,
    val caption: String?,
    val photoUrl: String?,
    val visibility: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtUtc: String,
    val expiresAtUtc: String
)


