package com.realms.app.data.realms

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.DELETE
import retrofit2.http.PUT

interface RealmsApi {

    @POST("locations/update")
    suspend fun updateLocation(
        @Body body: UpdateLocationRequest
    ): Response<Unit>

    @GET("users/nearby")
    suspend fun getNearbyUsers(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusMeters") radiusMeters: Int
    ): Response<List<NearbyUserDto>>

    @POST("users/me")
    suspend fun createMe(
        @Body req: CreateMeRequest
    ): Response<Unit>

    // ===== PROFILE =====
    @GET("users/me")
    suspend fun getMe(): Response<MeDto>

    @PUT("users/me")
    suspend fun updateMe(
        @Body req: UpdateMeRequest
    ): Response<Unit>

    // ===== FRIENDS =====
    @GET("friends")
    suspend fun getFriends(): Response<List<FriendDto>>

    @POST("friends/requests")
    suspend fun sendFriendRequest(
        @Body req: SendFriendRequestRequest
    ): Response<Unit>

    @GET("friends/requests/incoming")
    suspend fun getIncomingFriendRequests(): Response<List<FriendRequestDto>>

    @POST("friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") id: Long): Response<Unit>

    @POST("friends/requests/{id}/reject")
    suspend fun rejectFriendRequest(@Path("id") id: Long): Response<Unit>

    @DELETE("friends/{friendUserId}")
    suspend fun removeFriend(@Path("friendUserId") friendUserId: String): Response<Unit>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("username") username: String,
        @Query("max") max: Int = 20
    ): retrofit2.Response<List<SearchUserDto>>

    @GET("users/{id}/profile")
    suspend fun getUserProfile(@Path("id") id: String): Response<UserProfileDto>

    @POST("users/usernames")
    suspend fun getUsernames(@Body req: UsernamesRequest): UsernamesResponse


}
