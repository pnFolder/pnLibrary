package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.common.lang.Failures

internal fun runAll(vararg steps: () -> Unit) = runAll(steps.toList())

/** Runs every step even when earlier ones fail; the first failure is rethrown with later ones suppressed into it. */
internal fun runAll(steps: List<() -> Unit>) {
    var failure: Throwable? = null
    steps.forEach { step ->
        try {
            step()
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            val first = failure
            if (first == null) failure = error else if (error !== first) first.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
