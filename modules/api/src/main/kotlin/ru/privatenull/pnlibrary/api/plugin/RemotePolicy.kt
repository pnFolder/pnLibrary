package ru.privatenull.pnlibrary.api.plugin

import java.time.Duration
import java.net.URI
import java.util.Collections

/** Action applied when a remote policy denies operation. */
enum class DenyAction {
    DISABLE_PLUGIN,
    DISABLE_MODULE
}

/**
 * Reusable declaration for a remotely sourced policy.
 *
 * @property source absolute HTTPS or file URI of Java/Kotlin policy source
 * @property checkEvery interval between policy refreshes
 * @property onDeny action applied after a denied result
 * @property values immutable application-defined policy values
 */
data class RemotePolicy(
    val source: String,
    val checkEvery: Duration = Duration.ofHours(6),
    val onDeny: DenyAction = DenyAction.DISABLE_PLUGIN,
    val values: Map<String, String> = emptyMap(),
) {
    /** Fluent Java-friendly builder for [RemotePolicy]. */
    class Builder internal constructor() {
        private var source: String? = null
        private var checkEvery: Duration = Duration.ofHours(6)
        private var onDeny: DenyAction = DenyAction.DISABLE_PLUGIN
        private val values = linkedMapOf<String, String>()

        /** Sets and validates the Java/Kotlin policy source URI. */
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

        /** Sets the positive refresh interval. */
        fun checkEvery(value: Duration) = apply {
            require(!value.isZero && !value.isNegative) { "Remote policy interval must be positive" }
            checkEvery = value
        }

        /** Sets the action applied to a denied result. */
        fun onDeny(value: DenyAction) = apply { onDeny = value }

        /** Adds or replaces one application-defined policy value. */
        fun value(key: String, value: String) = apply {
            val normalized = key.trim()
            require(normalized.isNotEmpty()) { "Remote policy value key must not be blank" }
            values[normalized] = value
        }
        /** Adds all application-defined policy [values]. */
        fun values(values: Map<String, String>) = apply { values.forEach(::value) }

        /** Validates required fields and creates the immutable policy. */
        fun build(): RemotePolicy = RemotePolicy(
            requireNotNull(source) { "remote policy source is required" },
            checkEvery,
            onDeny,
            Collections.unmodifiableMap(LinkedHashMap(values)),
        )
    }

    /** Creates remote-policy declarations. */
    companion object {
        /** Creates a Java-friendly fluent remote-policy builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}

/** Compatibility adapter used by the original plugin-registration callback. */
@Deprecated("Use RemotePolicy.builder() for reusable policy declarations")
class RemotePolicyBuilder {
    private val delegate = RemotePolicy.builder()
    /** Sets the remote source URI. */
    fun source(value: String) { delegate.source(value) }

    /** Sets the refresh interval. */
    fun checkEvery(value: Duration) { delegate.checkEvery(value) }

    /** Sets the action applied to denial. */
    fun onDeny(value: DenyAction) { delegate.onDeny(value) }

    /** Adds an application-defined policy value. */
    fun value(key: String, value: String) { delegate.value(key, value) }

    /** Builds the reusable policy declaration. */
    fun build(): RemotePolicy = delegate.build()
}
