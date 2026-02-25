package com.realms.app.data.realms

data class FriendRequestDto(
    val id: Long,
    val fromUserId: String,
    val toUserId: String,
    val status: String,
    val createdAtUtc: String?,
    val respondedAtUtc: String?
)