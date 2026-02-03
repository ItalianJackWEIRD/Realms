package com.realms.app.data.realms

class RealmsRepository(
    private val api: RealmsApi
) {
    suspend fun updateLocation(latitude: Double, longitude: Double): Result<Unit> = runCatching {
        val res = api.updateLocation(
            body = UpdateLocationRequest(latitude, longitude)
        )
        if (!res.isSuccessful) error("updateLocation HTTP ${res.code()}")
    }

    suspend fun getNearbyUsers(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Result<List<NearbyUserDto>> = runCatching {
        val res = api.getNearbyUsers(
            lat = latitude,
            lon = longitude,
            radiusMeters = radiusMeters
        )
        if (!res.isSuccessful) error("getNearbyUsers HTTP ${res.code()}")
        res.body() ?: emptyList()
    }

    suspend fun createMe(
        username: String,
        firstName: String,
        lastName: String,
        bio: String? = null,
        profilePictureUrl: String? = null
    ): Result<Unit> = runCatching {
        val res = api.createMe(
            CreateMeRequest(
                username = username,
                firstName = firstName,
                lastName = lastName,
                bio = bio,
                profilePictureUrl = profilePictureUrl
            )
        )

        if (res.isSuccessful) return@runCatching Unit
        if (res.code() == 409) return@runCatching Unit // già creato

        throw RuntimeException("createMe HTTP ${res.code()}")
    }

    suspend fun updateMe(
        username: String,
        firstName: String,
        lastName: String,
        bio: String? = null,
        profilePhotoUrl: String? = null
    ): Result<Unit> = runCatching {
        val res = api.updateMe(
            UpdateMeRequest(
                username = username,
                firstName = firstName,
                lastName = lastName,
                bio = bio,
                profilePhotoUrl = profilePhotoUrl
            )
        )

        if (res.isSuccessful) return@runCatching Unit

        // backend: Conflict("username already taken")
        if (res.code() == 409) throw RuntimeException("username already taken")

        throw RuntimeException("updateMe HTTP ${res.code()}")
    }


    // ===== ME =====
    suspend fun getMe(): Result<MeDto> = runCatching {
        val res = api.getMe()
        if (!res.isSuccessful) error("getMe failed: ${res.code()}")
        res.body() ?: error("getMe empty body")
    }

    // ===== FRIENDS =====
    suspend fun getFriends(): Result<List<FriendDto>> = runCatching {
        val res = api.getFriends()
        if (!res.isSuccessful) error("getFriends failed: ${res.code()}")
        res.body() ?: emptyList()
    }

    // ===== REQUESTS =====
    suspend fun sendFriendRequest(toUserId: String): Result<Unit> = runCatching {
        val res = api.sendFriendRequest(SendFriendRequestRequest(toUserId))
        if (!res.isSuccessful) error("sendFriendRequest failed: ${res.code()}")
    }

    suspend fun getIncomingFriendRequests(): Result<List<FriendRequestDto>> = runCatching {
        val res = api.getIncomingFriendRequests()
        if (!res.isSuccessful) error("getIncomingFriendRequests failed: ${res.code()}")
        res.body() ?: emptyList()
    }

    suspend fun acceptFriendRequest(id: Long): Result<Unit> = runCatching {
        val res = api.acceptFriendRequest(id)
        if (!res.isSuccessful) error("acceptFriendRequest failed: ${res.code()}")
    }

    suspend fun rejectFriendRequest(id: Long): Result<Unit> = runCatching {
        val res = api.rejectFriendRequest(id)
        if (!res.isSuccessful) error("rejectFriendRequest failed: ${res.code()}")
    }

    suspend fun removeFriend(friendUserId: String): Result<Unit> = runCatching {
        val res = api.removeFriend(friendUserId)
        if (!res.isSuccessful) error("removeFriend failed: ${res.code()}")
    }

    // ===== SEARCH =====
    suspend fun searchUsers(username: String): Result<List<SearchUserDto>> = runCatching {
        val res = api.searchUsers(username = username, max = 20)
        if (!res.isSuccessful) error("searchUsers failed: ${res.code()}")
        res.body() ?: emptyList()
    }

    suspend fun getUserProfile(id: String): Result<UserProfileDto> = runCatching {
        val res = api.getUserProfile(id)
        if (!res.isSuccessful) error("getUserProfile failed: ${res.code()}")
        res.body() ?: error("getUserProfile empty body")
    }

    suspend fun getUsernameMap(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val res = api.getUsernames(UsernamesRequest(ids.distinct()))
        return res.items.associate { it.id to it.username }
    }

    // ==== POST ====
    suspend fun createPost(
        caption: String?,
        photoUrl: String?,
        lat: Double,
        lon: Double,
        visibility: String = "PUBLIC"
    ): CreatePostResponse {
        return api.create(
            CreatePostRequest(
                caption = caption,
                photoUrl = photoUrl,
                latitude = lat,
                longitude = lon,
                visibility = visibility
            )
        )
    }

    suspend fun getMapPosts(
        lat: Double,
        lon: Double,
        radiusMeters: Double = 1000.0,
        max: Int = 100
    ): List<MapPostDto> = api.map(lat, lon, radiusMeters, max)

    suspend fun deletePost(id: Int) = api.delete(id)

    suspend fun getUserPosts(userId: String, max: Int = 100): List<MapPostDto> =
        api.getUserPosts(userId = userId, max = max)


    suspend fun getFeedPosts(max: Int = 150): List<MapPostDto> {
        return api.getFeedPosts(max)
    }


}




