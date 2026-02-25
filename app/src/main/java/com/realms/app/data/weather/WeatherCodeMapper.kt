package com.realms.app.data.weather

object WeatherCodeMapper {
    // Mappatura “umana” minimale. La possiamo arricchire quando vuoi.
    fun toLabel(code: Int?): String {
        return when (code) {
            null -> "Unknown"
            0 -> "Clear"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Rain showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm (hail)"
            else -> "Weather $code"
        }
    }
}
