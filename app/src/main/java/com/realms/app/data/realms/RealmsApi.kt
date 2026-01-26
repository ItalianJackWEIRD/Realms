package com.realms.app.data.realms

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface RealmsApi {

    @POST("locations/update")
    suspend fun updateLocation(
        @Header("X-User-Id") userId: String,
        @Body body: UpdateLocationRequest
    ): Response<Unit>

    @GET("users/nearby")
    suspend fun getNearbyUsers(
        @Header("X-User-Id") userId: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusMeters") radiusMeters: Int
    ): Response<List<NearbyUserDto>>
}
