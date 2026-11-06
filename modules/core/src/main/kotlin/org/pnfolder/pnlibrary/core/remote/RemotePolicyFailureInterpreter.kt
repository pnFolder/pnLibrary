package org.pnfolder.pnlibrary.core.remote

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns remote policy failures into a short message for administrators. */
internal object RemotePolicyFailureInterpreter {
    fun message(error: Throwable): String {
        if (isNetwork(error)) return "Нет доступа к интернету: политику не удалось скачать"
        val message = error.message.orEmpty()
        if (isCompilerMissing(error)) {
            return "На сервере нет компилятора Java: сервер запущен на JRE, а для политики нужен JDK"
        }
        if (message.contains("compil", ignoreCase = true)) {
            return "Не удалось скомпилировать политику"
        }
        return message.lineSequence().firstOrNull().orEmpty()
            .ifBlank { error.javaClass.simpleName }
    }

    /** The server runs on a JRE, so the policy cannot be compiled. */
    fun isCompilerMissing(error: Throwable): Boolean =
        error.message.orEmpty().contains("Java compiler is unavailable", ignoreCase = true)

    /** A problem of the server setup (no internet, no JDK), not a defect of the plugin or the library. */
    fun isEnvironment(error: Throwable): Boolean = isNetwork(error) || isCompilerMissing(error)

    /** Whether [error] or one of its causes means the source could not be reached at all. */
    fun isNetwork(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(8).any {
        it is UnknownHostException || it is ConnectException || it is NoRouteToHostException ||
            it is SocketTimeoutException || it is SSLException
    }
}
