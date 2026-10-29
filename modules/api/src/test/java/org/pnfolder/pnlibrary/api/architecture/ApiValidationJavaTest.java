package org.pnfolder.pnlibrary.api.architecture;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey;
import org.pnfolder.pnlibrary.api.tasks.TaskSpec;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiValidationJavaTest {
    @Test
    void placeholderKeysRejectInvalidNamesFromJava() {
        assertEquals("balance", PlaceholderKey.of("balance", Long.class).getValue());
        assertThrows(IllegalArgumentException.class, () -> PlaceholderKey.of("Bad Key", String.class));
    }

    @Test
    void taskBuilderValidatesInputFromJava() {
        assertThrows(IllegalStateException.class, () -> TaskSpec.builder().build());
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.builder()
            .interval(Duration.ZERO).action(context -> { }).build());
        TaskSpec spec = TaskSpec.builder().name("tick").intervalTicks(20).tag("a").action(context -> { }).build();
        assertEquals(Duration.ofSeconds(1), spec.getInterval());
        assertTrue(spec.getTags().contains("a"));
    }
}
