package com.realms.app.data.realms

class RealmsRepository(
    private val api: RealmsApi
) {
    suspend fun updateLocation(
        userId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> {
        return runCatching {
            val res = api.updateLocation(
                userId = userId,
                body = UpdateLocationRequest(latitude, longitude)
            )
            if (!res.isSuccessful) error("updateLocation HTTP ${res.code()}")
        }
    }

    suspend fun getNearbyUsers(
        userId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Result<List<NearbyUserDto>> {
        return runCatching {
            val res = api.getNearbyUsers(
                userId = userId,
                lat = latitude,
                lon = longitude,
                radiusMeters = radiusMeters
            )
            if (!res.isSuccessful) error("getNearbyUsers HTTP ${res.code()}")
            res.body() ?: emptyList()
        }
    }
}
