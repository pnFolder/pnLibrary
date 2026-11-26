package org.pnfolder.pnlibrary.core.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceCleanupTest {
    @Test
    void rollbackRetainsPrimaryFailureAndRecordsCleanupFailures() {
        IllegalStateException primary = new IllegalStateException("registration");
        IllegalArgumentException cleanup = new IllegalArgumentException("cleanup");

        ResourceCleanup.suppressInto(primary, () -> {
            throw cleanup;
        }, () -> { });

        assertEquals(Collections.singletonList(cleanup), Arrays.asList(primary.getSuppressed()));
    }

    @Test
    void closeAllExecutesLaterStepsAndCombinesFailures() {
        final List<Integer> calls = new ArrayList<>();
        final IllegalStateException first = new IllegalStateException("first");
        final IllegalArgumentException second = new IllegalArgumentException("second");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> ResourceCleanup.closeAll(
            () -> {
                calls.add(1);
                throw first;
            },
            () -> calls.add(2),
            () -> {
                calls.add(3);
                throw second;
            }));

        assertSame(first, thrown);
        assertEquals(Arrays.asList(1, 2, 3), calls);
        assertEquals(Collections.singletonList(second), Arrays.asList(thrown.getSuppressed()));
    }
}
