package org.pnfolder.pnlibrary.api.metrics

import org.pnfolder.pnlibrary.api.security.SecretValue

/**
 * Provider-specific project credentials.
 *
 * A bStats project ID is public metadata. A FastStats token is sensitive and must not be
 * included in logs or diagnostic reports. Closing credentials releases owned secret buffers.
 */
sealed class MetricsCredentials<T> : AutoCloseable {

    /** Backend identified by these credentials. */
    abstract val provider: MetricsProvider

    /** Provides typed, scoped access to the provider's credential value. */
    abstract fun <R> useValue(operation: (T) -> R): R

    /** Releases owned resources; public numeric identifiers require no cleanup. */
    override fun close() = Unit

    /** Public positive project identifier assigned by bStats. */
    data class BStats(
        /** Public bStats project identifier. */
        val projectId: Int,
    ) : MetricsCredentials<Int>() {
        init {
            require(projectId > 0) { "Metrics project ID must be positive for bStats" }
        }

        override val provider: MetricsProvider = MetricsProvider.BSTATS

        override fun <R> useValue(operation: (Int) -> R): R = operation(projectId)
    }

    /**
     * Owns a defensive copy of the FastStats token and erases that copy on close.
     *
     * The caller owns the input array and should erase it separately. Native SDKs may retain
     * immutable strings; this class cannot erase those strings or a caller's original String.
     */
    class FastStats(token: CharArray) : MetricsCredentials<CharArray>() {
        private val secret: SecretValue

        init {
            require(token.isNotEmpty() && token.any { !it.isWhitespace() }) {
                "FastStats requires a non-blank project token"
            }
            secret = SecretValue(token)
        }

        override val provider: MetricsProvider = MetricsProvider.FASTSTATS

        /** Supplies an isolated token copy and erases it after the operation returns. */
        override fun <R> useValue(operation: (CharArray) -> R): R = secret.useSecret(operation)

        /**
         * Supplies a temporary String at the native SDK boundary.
         *
         * Do not retain or log the supplied value except where required by the SDK.
         * The JVM provides no reliable way to erase the immutable String.
         */
        fun <T> useToken(operation: (String) -> T): T {
            return useValue { operation(String(it)) }
        }

        /** Erases the owned character buffer once and prevents further access. */
        override fun close() = secret.close()

        /** Always redacts the token from textual representations. */
        override fun toString(): String = "FastStats(token=[REDACTED])"
    }
}
