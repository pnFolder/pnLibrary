package org.pnfolder.pnlibrary.api.remote

import org.pnfolder.pnlibrary.api.internal.isHttps
import java.net.URI
import java.time.Duration
import java.util.Collections

/** Action applied when a remote policy denies operation. */
enum class DenyAction {
    DISABLE_PLUGIN,
    DISABLE_MODULE
}

/** What happens when a policy cannot be checked at all: no network, no compiler, a broken file. */
enum class PolicyErrorAction {
    /** Treat it as a denial and apply [RemotePolicySettings.onDeny], at start and later. */
    DENY,

    /**
     * Deny until the policy has been checked once: a plugin that could not be checked at start does not run. After a
     * successful check a later failure, such as the network dropping for a while, only logs a warning.
     */
    DENY_UNTIL_CHECKED,

    /** Log a warning and keep running; only an explicit denial stops anything. Safe for offline servers and JREs. */
    KEEP_RUNNING,
}

/**
 * Reusable declaration for a remotely sourced policy.
 *
 * @property source absolute HTTPS or file URI of Java/Kotlin policy source
 * @property checkEvery interval between policy refreshes
 * @property onDeny action applied after a denied result
 * @property onError what an unreachable, uncompilable or failing policy means
 * @property supportUrl where administrators get help when the plugin cannot run; `null` means the pnFolder Discord
 * @property values immutable application-defined policy values
 */
data class RemotePolicySettings(
    val source: String,
    val checkEvery: Duration = Duration.ofHours(6),
    val onDeny: DenyAction = DenyAction.DISABLE_PLUGIN,
    val values: Map<String, String> = emptyMap(),
    val onError: PolicyErrorAction = PolicyErrorAction.DENY,
    val supportUrl: String? = null,
) {
    /** Fluent Java-friendly builder for [RemotePolicySettings]. */
    class Builder internal constructor() {
        private var source: String? = null
        private var checkEvery: Duration = Duration.ofHours(6)
        private var onDeny: DenyAction = DenyAction.DISABLE_PLUGIN
        private var onError: PolicyErrorAction = PolicyErrorAction.DENY
        private var supportUrl: String? = null
        private val values = linkedMapOf<String, String>()

        /** Sets and validates the Java/Kotlin policy source URI. */
        fun source(value: String): Builder = apply {
            val uri = URI.create(value)
            val https = uri.isHttps()
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
        fun checkEvery(value: Duration): Builder = apply {
            require(!value.isZero && !value.isNegative) { "Remote policy interval must be positive" }
            checkEvery = value
        }

        /** Sets the action applied to a denied result. */
        fun onDeny(value: DenyAction): Builder = apply { onDeny = value }

        /** Sets what a policy that cannot be checked means; [PolicyErrorAction.DENY] by default. */
        fun onError(value: PolicyErrorAction): Builder = apply { onError = value }

        /** Sets the support link shown when the plugin cannot run, such as a Discord invite; HTTPS only. */
        fun support(url: String): Builder = apply {
            require(URI.create(url).isHttps()) { "Support link must be an HTTPS URL" }
            supportUrl = url
        }

        /** Adds or replaces one application-defined policy value. */
        fun value(key: String, value: String): Builder = apply {
            val normalized = key.trim()
            require(normalized.isNotEmpty()) { "Remote policy value key must not be blank" }
            values[normalized] = value
        }
        /** Adds all application-defined policy [values]. */
        fun values(values: Map<String, String>): Builder = apply { values.forEach(::value) }

        /** Validates required fields and creates the immutable policy. */
        fun build(): RemotePolicySettings = RemotePolicySettings(
            requireNotNull(source) { "Remote policy source is required" },
            checkEvery,
            onDeny,
            Collections.unmodifiableMap(LinkedHashMap(values)),
            onError,
            supportUrl,
        )
    }

    /** Creates remote-policy declarations. */
    companion object {
        /** Creates a Java-friendly fluent remote-policy builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
