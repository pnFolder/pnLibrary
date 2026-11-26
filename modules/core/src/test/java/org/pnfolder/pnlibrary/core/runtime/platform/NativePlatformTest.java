package org.pnfolder.pnlibrary.core.runtime.platform;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativePlatformTest {
    private interface TestPlatform {
    }

    private static final TestPlatform IMPLEMENTATION = new TestPlatform() {
    };
    private static final TestPlatform REPLACEMENT = new TestPlatform() {
    };

    @Test
    void registersAndRemovesATypedPlatformImplementation() throws Exception {
        NativePlatform provider = new NativePlatform();

        assertNull(provider.get(TestPlatform.class));
        AutoCloseable registration = provider.register(TestPlatform.class, IMPLEMENTATION);
        assertSame(IMPLEMENTATION, provider.require(TestPlatform.class));

        registration.close();
        assertNull(provider.get(TestPlatform.class));
    }

    @Test
    void rejectsDuplicateRegistrationAndMissingRequiredPlatform() {
        NativePlatform provider = new NativePlatform();
        provider.register(TestPlatform.class, IMPLEMENTATION);

        assertThrows(IllegalArgumentException.class, () -> provider.register(TestPlatform.class, IMPLEMENTATION));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> provider.require(Runnable.class));
        assertTrue(missing.getMessage().contains(Runnable.class.getName()));
        assertTrue(missing.getMessage().contains(TestPlatform.class.getName()));
    }

    @Test
    void staleRegistrationHandleCannotRemoveAReplacement() throws Exception {
        NativePlatform provider = new NativePlatform();
        AutoCloseable first = provider.register(TestPlatform.class, IMPLEMENTATION);

        provider.close();
        provider.register(TestPlatform.class, REPLACEMENT);

        first.close();

        assertSame(REPLACEMENT, provider.require(TestPlatform.class));
    }
}
