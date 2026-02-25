package com.realms.app.data.realms

data class UsernamesResponse(val items: List<UsernameItemDto>)
data class UsernamesRequest(val ids: List<String>)
data class UsernameItemDto(val id: String, val username: String)