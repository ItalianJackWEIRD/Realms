package com.realms.app.data.weather

import kotlin.math.roundToInt

class WeatherRepository(
    private val api: WeatherApi
) {
    suspend fun getWeatherFor(lat: Double, lon: Double): Result<WeatherUiModel> {
        return runCatching {
            val dto = api.getCurrentWeather(latitude = lat, longitude = lon)
            val current = dto.current

            val temp = current?.temperature2m ?: error("Missing temperature_2m")
            val code = current.weatherCode
            WeatherUiModel(
                temperatureC = temp.roundToInt(),
                label = WeatherCodeMapper.toLabel(code),
                fetchedAtIso = current.time
            )
        }
    }
}
