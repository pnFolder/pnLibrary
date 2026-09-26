package ru.privatenull.pnlibrary.api.plugin

import java.time.Duration
import java.net.URI
import java.util.Collections

enum class DenyAction { DISABLE_PLUGIN, DISABLE_MODULE }

data class RemotePolicy(
    val source: String,
    val checkEvery: Duration = Duration.ofHours(6),
    val onDeny: DenyAction = DenyAction.DISABLE_PLUGIN,
    val values: Map<String, String> = emptyMap(),
) {
    class Builder internal constructor() {
        private var source: String? = null
        private var checkEvery: Duration = Duration.ofHours(6)
        private var onDeny: DenyAction = DenyAction.DISABLE_PLUGIN
        private val values = linkedMapOf<String, String>()

        fun source(value: String) = apply {
            val uri = URI.create(value)
            val https = uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
            val localFile = uri.scheme.equals("file", true) && uri.isAbsolute
            require(https || localFile) {
                "Remote policy source must be an absolute HTTPS or file URL"
            }
            require(uri.path.endsWith(".java", true) || uri.path.endsWith(".kt", true)) {
                "Remote policy source must be a .java or .kt file"
            }
            source = uri.toASCIIString()
        }
        fun checkEvery(value: Duration) = apply {
            require(!value.isZero && !value.isNegative) { "Remote policy interval must be positive" }
            checkEvery = value
        }
        fun onDeny(value: DenyAction) = apply { onDeny = value }
        fun value(key: String, value: String) = apply {
            val normalized = key.trim()
            require(normalized.isNotEmpty()) { "Remote policy value key must not be blank" }
            values[normalized] = value
        }
        fun values(values: Map<String, String>) = apply { values.forEach(::value) }
        fun build(): RemotePolicy = RemotePolicy(
            requireNotNull(source) { "remote policy source is required" },
            checkEvery,
            onDeny,
            Collections.unmodifiableMap(LinkedHashMap(values)),
        )
    }

    companion object {
        /** Creates a Java-friendly fluent remote-policy builder. */
        @JvmStatic fun builder(): Builder = Builder()
    }
}

/** Compatibility adapter used by the original plugin-registration callback. */
@Deprecated("Use RemotePolicy.builder() for reusable policy declarations")
class RemotePolicyBuilder {
    private val delegate = RemotePolicy.builder()
    fun source(value: String) { delegate.source(value) }
    fun checkEvery(value: Duration) { delegate.checkEvery(value) }
    fun onDeny(value: DenyAction) { delegate.onDeny(value) }
    fun value(key: String, value: String) { delegate.value(key, value) }
    fun build(): RemotePolicy = delegate.build()
}
