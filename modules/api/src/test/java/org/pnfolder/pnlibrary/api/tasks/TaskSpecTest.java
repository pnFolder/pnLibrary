package org.pnfolder.pnlibrary.api.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class TaskSpecTest {
    @Test
    void duplicateDisplayNamesAreValidWhileIdsRemainDistinct() {
        TaskSpec first = TaskSpec.builder().name("player-hud").action(context -> {}).build();
        TaskSpec second = TaskSpec.builder().name("player-hud").action(context -> {}).build();

        assertEquals(first.getName(), second.getName());
        assertNotEquals(TaskId.random(), TaskId.random());
    }

    @Test
    void builderRejectsInvalidSchedulingValuesBeforeRegistration() {
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.builder().key(" ").action(context -> {}).build());
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.builder().tag(" ").action(context -> {}).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> TaskSpec.builder().delay(Duration.ofSeconds(-1)).action(context -> {}).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> TaskSpec.builder().interval(Duration.ZERO).action(context -> {}).build());
        assertThrows(IllegalStateException.class, () -> TaskSpec.builder().build());
        assertThrows(IllegalArgumentException.class, () -> new TaskServiceSettings(-1));
    }

    @Test
    void executionFactoriesAndBuilderAreDirectlyVisibleToJava() throws NoSuchMethodException {
        assertEquals(TaskExecution.Kind.GLOBAL, TaskExecution.global().getKind());
        assertEquals(TaskExecution.Kind.ASYNC, TaskExecution.async().getKind());
        assertEquals("player", TaskExecution.entity("player").getTarget());
        assertNotNull(TaskSpec.class.getMethod("builder"));
    }

    @Test
    void builderConvertsTicksToDuration() {
        TaskSpec spec = TaskSpec.builder().delayTicks(20).intervalTicks(100).action(context -> {}).build();

        assertEquals(Duration.ofSeconds(1), spec.getDelay());
        assertEquals(Duration.ofSeconds(5), spec.getInterval());
    }

    @Test
    void builderRejectsNegativeTicks() {
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.builder().delayTicks(-1));
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.builder().intervalTicks(-1));
    }
}
