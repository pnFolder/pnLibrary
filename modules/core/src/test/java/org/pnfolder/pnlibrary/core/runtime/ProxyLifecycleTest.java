package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class ProxyLifecycleTest {
    private final List<String> steps = new ArrayList<>();
    private final PnLibraryRuntimeHost host = mock(PnLibraryRuntimeHost.class);
    private final ProxyRuntime proxy = mock(ProxyRuntime.class);

    private final class Startup implements ProxyStartup {
        private final boolean failToListen;

        Startup(boolean failToListen) {
            this.failToListen = failToListen;
        }

        @NotNull
        @Override
        public PnLibraryRuntimeHost launch() {
            steps.add("launch");
            return host;
        }

        @NotNull
        @Override
        public ProxyRuntime createProxy() {
            steps.add("create proxy");
            return proxy;
        }

        @NotNull
        @Override
        public AutoCloseable listen(@NotNull PnLibraryRuntimeHost started) {
            assertSame(host, started);
            if (failToListen) {
                throw new IllegalStateException("cannot listen");
            }
            steps.add("listen");
            return () -> steps.add("close listeners");
        }
    }

    private ProxyLifecycleTest() {
        doAnswer(call -> steps.add("close proxy")).when(proxy).close();
        doAnswer(call -> steps.add("close host")).when(host).close();
        doAnswer(call -> steps.add("start proxy")).when(proxy).start(host);
    }

    @Test
    void startsInOrderAndClosesInReverse() {
        ProxyLifecycle lifecycle = new ProxyLifecycle();
        lifecycle.start(new Startup(false));
        lifecycle.close();

        assertEquals(Arrays.asList("launch", "create proxy", "start proxy", "listen", "close listeners", "close proxy", "close host"),
                steps);
    }

    @Test
    void aFailedStartStopsWhatStartedAndRethrows() {
        ProxyLifecycle lifecycle = new ProxyLifecycle();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> lifecycle.start(new Startup(true)));

        assertEquals("cannot listen", failure.getMessage());
        assertEquals(Arrays.asList("launch", "create proxy", "start proxy", "close proxy", "close host"), steps);
    }

    @Test
    void aSecondStartIsRefusedAndCloseCanRunTwice() {
        ProxyLifecycle lifecycle = new ProxyLifecycle();
        lifecycle.start(new Startup(false));

        assertThrows(IllegalStateException.class, () -> lifecycle.start(new Startup(false)));
        lifecycle.close();
        lifecycle.close();

        assertEquals(3, steps.stream().filter(step -> step.startsWith("close")).count());
    }

    @Test
    void closingBeforeAnyStartDoesNothing() {
        new ProxyLifecycle().close();
        assertEquals(0, steps.size());
    }
}
