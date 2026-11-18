package org.pnfolder.pnlibrary.core.exceptions

object ResourceCleanup {
    // Rollback helper: later failures suppress into the primary so the caller sees the original cause.
    fun suppressInto(primary: Throwable, vararg steps: () -> Unit) {
        steps.forEach { step ->
            try {
                step()
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== primary) primary.addSuppressed(cleanupFailure)
            }
        }
    }

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
