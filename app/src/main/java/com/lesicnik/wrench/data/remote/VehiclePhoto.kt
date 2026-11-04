package com.lesicnik.wrench.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** LubeLogger supplies its stock placeholder as a URL even when no photo was uploaded. */
internal fun hasVehiclePhoto(location: String?): Boolean {
    if (location.isNullOrBlank()) return false
    val path = "/" + location.substringBefore('?').substringBefore('#').trim('/')
    return !path.endsWith("/defaults/noimage.png", ignoreCase = true)
}


internal fun Vehicle.photoUrl(serverUrl: String): String? {
    if (!hasVehiclePhoto(imageLocation) || serverUrl.isBlank()) return null
    val server = "${serverUrl.trimEnd('/')}/".toHttpUrlOrNull() ?: return null
    val location = imageLocation.orEmpty()
    // Keep LubeLogger paths relative to installations hosted under a URL prefix.
    val path = if (location.startsWith('/') && !location.startsWith("//")) location.drop(1) else location
    val photo = server.resolve(path) ?: return null
    // Both the UI and preloader attach credentials to these URLs.
    if (photo.scheme != server.scheme || photo.host != server.host || photo.port != server.port) return null
    if (photo.username.isNotEmpty() || photo.password.isNotEmpty()) return null
    return photo.toString()
}
