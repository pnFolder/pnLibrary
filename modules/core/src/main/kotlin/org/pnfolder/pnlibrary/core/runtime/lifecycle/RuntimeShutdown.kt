package org.pnfolder.pnlibrary.core.runtime.lifecycle

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal

/** Executes runtime shutdown actions in declaration order while isolating failures. */
internal class RuntimeShutdown {
    private val actions = mutableListOf<() -> Unit>()

    fun add(action: () -> Unit) {
        actions += action
    }

    fun run() {
        val failures = mutableListOf<Throwable>()
        actions.forEach { action ->
            runCatchingNonFatal(action).onFailure(failures::add)
        }

        val first = failures.firstOrNull() ?: return
        failures.drop(1).forEach(first::addSuppressed)
        throw first
    }
}
