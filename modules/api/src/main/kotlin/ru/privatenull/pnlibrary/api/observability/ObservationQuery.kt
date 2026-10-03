package ru.privatenull.pnlibrary.api.observability

data class ObservationQuery(
    val limit: Int = 50,
    val plugin: String? = null,
    val minimumLevel: ObservationLevel? = null,
    val since: Long? = null,
    val until: Long? = null,
)
