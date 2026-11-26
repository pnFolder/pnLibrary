package org.pnfolder.pnlibrary.api.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class TaskQueryTest {
    @Test
    void builderCreatesNormalizedImmutableCollectionSnapshots() {
        TaskQuery query = TaskQuery.builder()
                .key("  cleanup  ")
                .nameContains("  nightly  ")
                .status(TaskStatus.SCHEDULED)
                .execution(TaskExecution.Kind.ASYNC)
                .tag("maintenance")
                .build();

        assertEquals("cleanup", query.getKey());
        assertEquals("nightly", query.getNameContains());
        assertThrows(UnsupportedOperationException.class, () -> query.getTags().add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> query.getStatuses().clear());
    }

    @Test
    void taskDeclarationCollectionsAreImmutable() {
        TaskSpec spec = TaskSpec.builder()
                .condition(() -> true)
                .cancelWhen(() -> false)
                .tag("maintenance")
                .action(context -> {})
                .build();

        assertThrows(UnsupportedOperationException.class, () -> spec.getTags().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.getConditions().clear());
    }

    @Test
    void builderRejectsMeaninglessBlankFilters() {
        assertThrows(IllegalArgumentException.class, () -> TaskQuery.builder().key(" ").build());
        assertThrows(IllegalArgumentException.class, () -> TaskQuery.builder().nameContains(" ").build());
        assertThrows(IllegalArgumentException.class, () -> TaskQuery.builder().tag(" ").build());
    }
}
