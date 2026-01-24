package com.realms.app.data.weather

data class WeatherUiModel(
    val temperatureC: Int,
    val label: String,
    val fetchedAtIso: String?
)
