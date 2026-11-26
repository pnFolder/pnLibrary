package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

class RuntimeResourcesTest {
    private final PnLibrary foreignLibrary = (PnLibrary) Proxy.newProxyInstance(PnLibrary.class.getClassLoader(),
        new Class<?>[] {PnLibrary.class}, (proxy, method, args) -> {
            throw new UnsupportedOperationException();
        });

    @Test
    void runtimeBridgesRejectALibraryThatIsNotTheCoreRuntime() {
        IllegalStateException command = assertThrows(IllegalStateException.class,
            () -> RuntimeBridge.registerRuntimeCommand(foreignLibrary, "demo", it -> { }));
        IllegalStateException tasks = assertThrows(IllegalStateException.class, () -> RuntimeBridge.runtimeTaskScope(foreignLibrary));

        assertTrue(command.getMessage().contains("core runtime"));
        assertTrue(tasks.getMessage().contains("core runtime"));
    }
}
