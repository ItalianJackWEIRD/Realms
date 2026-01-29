package com.realms.app.data.realms

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Headers


interface RealmsApi {

    @POST("locations/update")
    suspend fun updateLocation(
        @Body body: UpdateLocationRequest
    ): retrofit2.Response<Unit>

    @GET("users/nearby")
    suspend fun getNearbyUsers(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusMeters") radiusMeters: Int
    ): retrofit2.Response<List<NearbyUserDto>>

    @POST("users/me")
    suspend fun createMe(
        @Body req: CreateMeRequest
    ): retrofit2.Response<Unit>

    @GET("users/me")
    suspend fun getMe(): retrofit2.Response<MeDto>

    @GET("friends")
    suspend fun getFriends(): retrofit2.Response<List<FriendDto>>

}

