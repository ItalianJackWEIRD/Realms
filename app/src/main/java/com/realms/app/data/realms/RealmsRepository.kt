package com.realms.app.data.realms

class RealmsRepository(
    private val api: RealmsApi
) {
    suspend fun updateLocation(latitude: Double, longitude: Double): Result<Unit> {
        return runCatching {
            val res = api.updateLocation(
                body = UpdateLocationRequest(latitude, longitude)
            )
            if (!res.isSuccessful) error("updateLocation HTTP ${res.code()}")
        }
    }

    suspend fun getNearbyUsers(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Result<List<NearbyUserDto>> {
        return runCatching {
            val res = api.getNearbyUsers(
                lat = latitude,
                lon = longitude,
                radiusMeters = radiusMeters
            )
            if (!res.isSuccessful) error("getNearbyUsers HTTP ${res.code()}")
            res.body() ?: emptyList()
        }
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
        if (res.code() == 409) return@runCatching Unit

        throw RuntimeException("createMe HTTP ${res.code()}")
    }

}
