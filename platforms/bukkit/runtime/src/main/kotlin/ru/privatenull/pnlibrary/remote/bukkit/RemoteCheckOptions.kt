package ru.privatenull.pnlibrary.remote.bukkit

import java.util.Collections

class RemoteCheckOptions private constructor(builder: Builder) {
    val url: String = builder.url.also { require(it.startsWith("https://")) { "HTTPS URL is required" } }
    val className: String? = builder.className
    val maxBytes: Long = builder.maxBytes.also { require(it > 0) }
    val intervalTicks: Long = builder.intervalTicks.also { require(it > 0) }
    val listener: RemoteCheckListener = builder.listener
    val values: Map<String, String> = Collections.unmodifiableMap(HashMap(builder.values))

    class Builder internal constructor(val url: String, val className: String?) {
        var maxBytes: Long = 8L * 1024L * 1024L
        var intervalTicks: Long = 6L * 60L * 60L * 20L
        var listener: RemoteCheckListener = object : RemoteCheckListener {}
        val values = HashMap<String, String>()
        fun maxBytes(value: Long) = apply { require(value > 0); maxBytes = value }
        fun intervalTicks(value: Long) = apply { require(value > 0); intervalTicks = value }
        fun listener(value: RemoteCheckListener?) = apply { listener = value ?: object : RemoteCheckListener {} }
        fun value(key: String, value: String) = apply { values[key] = value }
        fun build() = RemoteCheckOptions(this)
    }

    companion object {
        @JvmStatic fun builder(url: String) = Builder(url, null)
        @JvmStatic fun builder(url: String, className: String) = Builder(url, className)
        @JvmStatic fun github(owner: String, repository: String, file: String, className: String): Builder {
            require(owner.matches(Regex("[A-Za-z0-9_.-]+")) && repository.matches(Regex("[A-Za-z0-9_.-]+")) && file.matches(Regex("[A-Za-z0-9._-]+")))
            return builder("https://github.com/$owner/$repository/releases/latest/download/$file", className)
        }
    }
}
