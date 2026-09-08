package ru.privatenull.pnlibrary.api.plugin

/** Rows that a plugin may append to an automatically displayed lifecycle message. */
interface LifecycleReport {
    fun ok(label: String, detail: String): LifecycleReport
    fun warn(label: String, detail: String): LifecycleReport
    fun skip(label: String, detail: String): LifecycleReport
    fun fail(label: String, detail: String, error: Throwable? = null): LifecycleReport
}
