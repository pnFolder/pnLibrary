package ru.privatenull.pnlibrary.core.plugin

/** Executes lifecycle cleanup steps without abandoning later resources after a failure. */
internal object ResourceCleanup {
    /**
     * Runs every [steps] action and attaches failures to [primary].
     *
     * This is intended for rollback paths where the exception that caused the
     * rollback must remain the exception observed by the caller.
     */
    fun suppressInto(primary: Throwable, vararg steps: () -> Unit) {
        steps.forEach { step ->
            try {
                step()
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== primary) primary.addSuppressed(cleanupFailure)
            }
        }
    }

    /**
     * Runs every [steps] action and throws the first failure after completion.
     * Later failures are attached to the first one as suppressed exceptions.
     */
    fun closeAll(vararg steps: () -> Unit) {
        var failure: Throwable? = null
        steps.forEach { step ->
            try {
                step()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (error !== failure) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
