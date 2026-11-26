package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdown;

class RuntimeShutdownTest {
    @Test
    void runsEveryActionAndPreservesAllCleanupFailures() {
        RuntimeShutdown shutdown = new RuntimeShutdown();
        List<String> calls = new ArrayList<>();
        IllegalStateException firstFailure = new IllegalStateException("first");
        IllegalArgumentException secondFailure = new IllegalArgumentException("second");

        shutdown.add(() -> {
            calls.add("first");
            throw firstFailure;
        });
        shutdown.add(() -> {
            calls.add("second");
            throw secondFailure;
        });
        shutdown.add(() -> calls.add("third"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, shutdown::run);

        assertEquals(Arrays.asList("first", "second", "third"), calls);
        assertSame(firstFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0] == secondFailure);
    }
}
