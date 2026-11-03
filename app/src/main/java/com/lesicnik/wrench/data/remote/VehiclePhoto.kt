package com.lesicnik.wrench.data.remote

/** LubeLogger supplies its stock placeholder as a URL even when no photo was uploaded. */
internal fun hasVehiclePhoto(location: String?): Boolean {
    if (location.isNullOrBlank()) return false
    val path = "/" + location.substringBefore('?').substringBefore('#').trim('/')
    return !path.endsWith("/defaults/noimage.png", ignoreCase = true)
}


internal fun Vehicle.photoUrl(serverUrl: String): String? {
    if (!hasVehiclePhoto(imageLocation) || serverUrl.isBlank()) return null
    val location = imageLocation.orEmpty()
    return if (location.startsWith("http", ignoreCase = true)) location
        else "${serverUrl.trimEnd('/')}/${location.trimStart('/')}"
}
