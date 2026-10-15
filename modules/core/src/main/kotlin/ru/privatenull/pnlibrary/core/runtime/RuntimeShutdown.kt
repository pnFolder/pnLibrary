package ru.privatenull.pnlibrary.core.runtime

/** Executes runtime shutdown actions in declaration order while isolating failures. */
internal class RuntimeShutdown {
    private val actions = mutableListOf<() -> Unit>()

    fun add(action: () -> Unit) {
        actions += action
    }

    fun run() {
        val failures = mutableListOf<Throwable>()
        actions.forEach { action ->
            runCatching(action).onFailure(failures::add)
        }

        val first = failures.firstOrNull() ?: return
        failures.drop(1).forEach(first::addSuppressed)
        throw first
    }
}
