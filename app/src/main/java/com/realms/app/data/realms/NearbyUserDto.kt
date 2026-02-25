package com.realms.app.data.realms


data class NearbyUserDto(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAtUtc: String? = null
)

