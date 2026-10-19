package org.pnfolder.pnlibrary.core.remote

/** Turns remote policy failures into a short message for administrators. */
internal object RemotePolicyFailureInterpreter {
    fun message(error: Throwable): String {
        val message = error.message.orEmpty()
        if (message.contains("compil", ignoreCase = true)) {
            return "Не удалось скомпилировать удалённую policy"
        }
        return message.lineSequence().firstOrNull().orEmpty()
            .ifBlank { error.javaClass.simpleName }
    }
}
