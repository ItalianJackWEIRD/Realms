package com.realms.app.data.realms

import com.squareup.moshi.Json

data class UpdateLocationRequest(
    @Json(name = "Latitude") val latitude: Double,
    @Json(name = "Longitude") val longitude: Double
)

data class NearbyUserDto(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAtUtc: String? = null
)

data class CreateMeRequest(
    val username: String,
    val firstName: String,
    val lastName: String,
    val bio: String? = null,
    val profilePictureUrl: String? = null
)

