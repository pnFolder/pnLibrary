package org.pnfolder.pnlibrary.core.remote;

import org.pnfolder.pnlibrary.core.remote.report.RemotePolicyFailureInterpreter;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class RemotePolicyFailureInterpreterTest {
    @Test
    void replacesCompilerInternalsWithAClearMessage() {
        assertEquals("Не удалось скомпилировать политику",
            RemotePolicyFailureInterpreter.message(new IllegalStateException("Kotlin compilation failed at line 42")));
    }

    @Test
    void keepsOnlyTheFirstUsefulLineOfAnOrdinaryFailure() {
        assertEquals("Сервер вернул HTTP 503",
            RemotePolicyFailureInterpreter.message(new IllegalStateException("Сервер вернул HTTP 503\nstack details")));
    }

    @Test
    void aMissingNetworkSaysThatTheCheckNeedsInternetEvenDeepInTheCauseChain() {
        IllegalStateException offline = new IllegalStateException("load failed",
            new IOException("io", new UnknownHostException("raw.githubusercontent.com")));
        assertEquals(true, RemotePolicyFailureInterpreter.isNetwork(offline));
        assertEquals("Нет доступа к интернету: политику не удалось скачать", RemotePolicyFailureInterpreter.message(offline));
        assertEquals(false, RemotePolicyFailureInterpreter.isNetwork(new IllegalStateException("Сервер вернул HTTP 503")));
    }

    @Test
    void aServerWithoutJavacIsToldToRunOnAJdk() {
        assertEquals(
            "На сервере нет компилятора Java (это JRE), а запасной скачать не удалось: проверьте интернет сервера и повторите",
            RemotePolicyFailureInterpreter.message(
                new IllegalStateException("Java compiler is unavailable; run the server with a JDK")));
    }

    @Test
    void aMissingJdkAndAMissingNetworkAreServerSetupABrokenPolicyIsNot() {
        assertEquals(true, RemotePolicyFailureInterpreter.isEnvironment(
            new IllegalStateException("Java compiler is unavailable; run the server with a JDK")));
        assertEquals(true, RemotePolicyFailureInterpreter.isEnvironment(new UnknownHostException("raw.githubusercontent.com")));
        assertEquals(false, RemotePolicyFailureInterpreter.isEnvironment(
            new IllegalArgumentException("Remote policy compilation failed")));
    }
}
