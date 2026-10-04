package ru.privatenull.pnlibrary.api.metrics

/**
 * Provider-neutral, normalized error event suitable for local or remote reporting.
 *
 * @property type fully qualified exception type or another stable error identifier
 * @property message human-readable error message, when one is available
 * @property stackTrace rendered stack frames ordered from the failure site outward
 * @property handled whether application code caught and handled the failure
 * @property operation optional logical operation that was executing when the failure occurred
 * @property attributes additional structured context associated with this occurrence
 */
data class TelemetryError(
    val type: String,
    val message: String?,
    val stackTrace: List<String>,
    val handled: Boolean,
    val operation: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
)

/** Error reporting contract shared by local diagnostics and remote providers. */
interface ErrorReporter : AutoCloseable {
    /** Captures and queues an error; implementations must not throw into application code. */
    fun capture(error: TelemetryError)

    /** Convenience conversion for a Throwable with a bounded stack trace. */
    fun capture(
        throwable: Throwable,
        operation: String? = null,
        handled: Boolean = true,
        attributes: Map<String, Any?> = emptyMap(),
    ) = capture(
        TelemetryError(
            type = throwable.javaClass.name,
            message = throwable.message,
            stackTrace = throwable.stackTrace.map { it.toString() },
            handled = handled,
            operation = operation,
            attributes = attributes,
        ),
    )

    /** Installs a process/class-loader handler when the provider supports it. */
    fun installGlobalHandler() = Unit

    /** Flushes pending reports where supported and releases reporter resources. */
    override fun close()
}
