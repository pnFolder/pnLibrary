package org.pnfolder.pnlibrary.core.remote;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.jetbrains.annotations.NotNull;

/** Turns remote policy failures into a short message for administrators. */
public final class RemotePolicyFailureInterpreter {
    private static final int CAUSE_DEPTH = 8;

    private RemotePolicyFailureInterpreter() {
    }

    public static @NotNull String message(@NotNull Throwable error) {
        if (isNetwork(error)) {
            return "Нет доступа к интернету: политику не удалось скачать";
        }
        if (isCompilerMissing(error)) {
            return "На сервере нет компилятора Java (это JRE), а запасной скачать не удалось: проверьте интернет сервера и повторите";
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.toLowerCase(Locale.ROOT).contains("compil")) {
            return "Не удалось скомпилировать политику";
        }
        String firstLine = message.split("\r\n|\n|\r", -1)[0];
        return isBlank(firstLine) ? error.getClass().getSimpleName() : firstLine;
    }

    /** The server runs on a JRE, so the policy cannot be compiled. */
    public static boolean isCompilerMissing(@NotNull Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        return message.toLowerCase(Locale.ROOT).contains("java compiler is unavailable");
    }

    /** A problem of the server setup (no internet, no JDK), not a defect of the plugin or the library. */
    public static boolean isEnvironment(@NotNull Throwable error) {
        return isNetwork(error) || isCompilerMissing(error);
    }

    /** Whether the error or one of its causes means the source could not be reached at all. */
    public static boolean isNetwork(@NotNull Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < CAUSE_DEPTH; depth++) {
            if (current instanceof UnknownHostException || current instanceof ConnectException
                || current instanceof NoRouteToHostException || current instanceof SocketTimeoutException
                || current instanceof SSLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
