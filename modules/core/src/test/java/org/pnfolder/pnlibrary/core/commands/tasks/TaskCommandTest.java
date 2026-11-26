package org.pnfolder.pnlibrary.core.commands.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources;
import org.pnfolder.pnlibrary.core.tasks.TaskControl;

class TaskCommandTest {
    private final TaskSnapshot snapshot = TaskSnapshot.builder(TaskId.random(), "pnTest", TaskExecution.Kind.GLOBAL,
            TaskStatus.SCHEDULED, Instant.now())
            .name("cleanup")
            .tags(Collections.emptySet())
            .nextRunAt(Instant.now())
            .runCount(0)
            .skippedCount(0)
            .build();
    private final PnLibrary library = mock(PnLibrary.class, withSettings().extraInterfaces(RuntimeResources.class));
    private final RuntimeResources resources = (RuntimeResources) library;
    private final RecordingSender sender = new RecordingSender();
    private final CommandDefinition definition = new TaskCommand(library).definition();

    TaskCommandTest() {
        when(resources.taskSnapshots()).thenReturn(Arrays.asList(snapshot));
    }

    private String run(String... words) {
        return CommandRunner.run(definition, sender, "tasks", words);
    }

    @Test
    @DisplayName("command is named tasks and has the basic subcommands")
    void commandIsNamedTasksAndHasTheBasicSubcommands() {
        assertEquals("tasks", definition.getName());
        List<String> words = new ArrayList<>();
        for (CommandNode child : definition.getRoot().getChildren()) {
            words.add(child.getName());
        }
        assertTrue(words.containsAll(Arrays.asList("view", "run", "cancel", "today", "active")), words.toString());
    }

    @Test
    @DisplayName("plain command lists the task")
    void plainCommandListsTheTask() {
        assertTrue(run().contains("cleanup"));
        assertTrue(run("any", "all").contains("cleanup"));
    }

    @Test
    @DisplayName("view finds a task by a short id and reports an unknown one")
    void viewFindsATaskByAShortIdAndReportsAnUnknownOne() {
        assertTrue(run("view", snapshot.getId().getValue().substring(0, 6)).contains("cleanup"));
        assertTrue(run("view", "zzzzzzzz").contains("Такой задачи нет"));
    }

    @Test
    @DisplayName("cancel and run answer by the control result")
    void cancelAndRunAnswerByTheControlResult() {
        String id = snapshot.getId().getValue();
        when(resources.cancelTask(id)).thenReturn(TaskControl.DONE);
        assertTrue(run("cancel", id).contains("остановлена"));
        when(resources.cancelTask(id)).thenReturn(TaskControl.FORBIDDEN);
        assertTrue(run("cancel", id).contains("не разрешает"));
        when(resources.runTaskNow(id)).thenReturn(TaskControl.DONE);
        assertTrue(run("run", id).contains("запущена"));
        when(resources.runTaskNow(id)).thenReturn(TaskControl.UNAVAILABLE);
        assertTrue(run("run", id).contains("нельзя"));
    }
}
