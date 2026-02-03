package com.realms.app.ui.util

import java.time.Duration
import java.time.Instant

fun timeAgoEn(isoUtc: String): String {
    val created = runCatching { Instant.parse(isoUtc) }.getOrNull() ?: return ""
    val now = Instant.now()
    val d = Duration.between(created, now)

    val seconds = d.seconds
    if (seconds < 0) return "just now"

    val minute = 60L
    val hour = 60L * minute
    val day = 24L * hour
    val week = 7L * day

    return when {
        seconds < 10 -> "just now"
        seconds < minute -> "${seconds}s ago"
        seconds < hour -> "${seconds / minute}m ago"
        seconds < day -> "${seconds / hour}h ago"
        seconds < week -> "${seconds / day}d ago"
        else -> "${seconds / week}w ago"
    }
}
