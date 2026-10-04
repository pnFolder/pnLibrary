package ru.privatenull.pnlibrary.api.metrics

/** Provider-neutral, already normalized error event. */
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

    override fun close()
}
