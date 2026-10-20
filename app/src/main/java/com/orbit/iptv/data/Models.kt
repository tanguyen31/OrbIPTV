package com.orbit.iptv.data

enum class Kind { LIVE, MOVIE }

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val group: String,
    val url: String,
    val kind: Kind,
    val streamId: String? = null,
)

sealed class Source {
    data class M3u(val url: String) : Source()
    data class Xtream(val server: String, val user: String, val pass: String) : Source()
}

const val DEFAULT_GROUP = "Sans catégorie"
