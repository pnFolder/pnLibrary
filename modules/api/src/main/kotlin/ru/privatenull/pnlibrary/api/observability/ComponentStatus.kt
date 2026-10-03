package ru.privatenull.pnlibrary.api.observability

data class ComponentStatus(
    val plugin: String,
    val component: String,
    val state: String,
    val detail: String = "",
    val data: Map<String, String> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)
