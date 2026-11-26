package org.pnfolder.pnlibrary.common.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCatchingNonFatalTest {
    @Test
    void ordinaryFailuresAndResultsAreReturned() {
        assertEquals(Integer.valueOf(7), Failures.runCatchingNonFatal(() -> 7).getOrNull());
        assertTrue(Failures.runCatchingNonFatal(() -> {
            throw new IllegalStateException("boom");
        }).isFailure());
    }

    @Test
    void aFailingJvmEscapes() {
        assertThrows(OutOfMemoryError.class, () -> Failures.runCatchingNonFatal(() -> {
            throw new OutOfMemoryError();
        }));
    }

    @Test
    void anInterruptIsRecordedOnTheThread() {
        try {
            assertTrue(Failures.runCatchingNonFatal(() -> {
                throw new InterruptedException();
            }).isFailure());
            assertTrue(Thread.interrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
