package org.pnfolder.pnlibrary.api.architecture;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiValidationJavaTest {
    @Test
    void viewersWorkFromJava() {
        assertEquals("en", ViewerInfo.none().getLanguage());
        assertThrows(RuntimeException.class, () -> ViewerInfo.none().requirePlayerId());
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
