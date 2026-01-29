package com.realms.app.data.realms

import com.squareup.moshi.Json

data class UpdateLocationRequest(
    @Json(name = "Latitude") val latitude: Double,
    @Json(name = "Longitude") val longitude: Double
)