package ru.privatenull.pnlibrary.core.runtime

/** Executes runtime shutdown actions in declaration order while isolating failures. */
internal class RuntimeShutdown {
    private val actions = mutableListOf<() -> Unit>()

    fun add(action: () -> Unit) {
        actions += action
    }

    fun run() {
        actions.forEach { action ->
            runCatching(action)
        }
    }
}
