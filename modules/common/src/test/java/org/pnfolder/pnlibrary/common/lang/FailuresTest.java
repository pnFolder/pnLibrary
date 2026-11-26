package org.pnfolder.pnlibrary.common.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailuresTest {
    @Test
    void aFailingJvmIsRethrown() {
        assertThrows(OutOfMemoryError.class, () -> Failures.rethrowIfFatal(new OutOfMemoryError()));
        assertThrows(StackOverflowError.class, () -> Failures.rethrowIfFatal(new StackOverflowError()));
    }

    @Test
    void linkageErrorsAndOrdinaryFailuresAreLeftToTheCaller() {
        assertDoesNotThrow(() -> Failures.rethrowIfFatal(new NoClassDefFoundError("missing bridge")));
        assertDoesNotThrow(() -> Failures.rethrowIfFatal(new NoSuchMethodError("old server")));
        assertDoesNotThrow(() -> Failures.rethrowIfFatal(new IllegalStateException("boom")));
    }
}
