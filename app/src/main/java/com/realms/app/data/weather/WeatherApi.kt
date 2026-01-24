package com.realms.app.data.weather

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        // Open-Meteo: current=temperature_2m,weather_code
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("timezone") timezone: String = "auto",
        @Query("temperature_unit") temperatureUnit: String = "celsius"
    ): WeatherResponseDto
}
