package com.realms.app.data.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponseDto(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "current") val current: CurrentDto?
)

@JsonClass(generateAdapter = true)
data class CurrentDto(
    // ISO string es: "2026-01-24T15:00"
    @Json(name = "time") val time: String?,
    @Json(name = "temperature_2m") val temperature2m: Double?,
    @Json(name = "weather_code") val weatherCode: Int?
)
