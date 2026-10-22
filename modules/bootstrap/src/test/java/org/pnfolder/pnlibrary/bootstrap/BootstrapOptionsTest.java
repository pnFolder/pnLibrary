package org.pnfolder.pnlibrary.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BootstrapOptionsTest {
    @Test
    void aNamedMinimumIsKept() {
        BootstrapOptions options = BootstrapOptions.builder("v1.2.0").build();

        assertTrue(options.hasMinimum());
        assertEquals("1.2.0", options.minimumVersion());
    }

    @Test
    void withoutAMinimumAnyVersionWillDo() {
        for (BootstrapOptions options : new BootstrapOptions[] {
                BootstrapOptions.builder().build(),
                BootstrapOptions.builder(null).build(),
                BootstrapOptions.builder("  ").build(),
        }) {
            assertFalse(options.hasMinimum());
            assertEquals("0.0.0", options.minimumVersion());
            assertTrue(BootstrapVersion.isAtLeast("0.0.1-dev.1", options.minimumVersion()));
        }
    }

    @Test
    void aBrokenMinimumIsStillRefused() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapOptions.builder("one").build());
    }

    @Test
    void theRepositoryComesFromGradleProperties() {
        BootstrapOptions options = BootstrapOptions.builder().build();

        assertEquals(BootstrapDefaults.REPOSITORY_OWNER, options.repositoryOwner());
        assertEquals(BootstrapDefaults.REPOSITORY_NAME, options.repositoryName());
    }
}
