package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class ScheduledTasksTest {
    static TaskSpec spec(String name) {
        return TaskSpec.builder().name(name).action(context -> { }).build();
    }

    @Test
    void closedServiceRejectsBothNativeOwnerAndNamedModuleScopes() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter);
        service.close();
        assertThrows(IllegalStateException.class, () -> service.scope(new Object()));
        assertThrows(IllegalStateException.class, () -> service.scope(new Object(), PluginId.of("module")));
        assertTrue(adapter.requests.isEmpty());
    }

    @Test
    void scopeCleanupContinuesAfterNativeCancellationFails() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter(false, 0);
        ScheduledTasks service = new ScheduledTasks(adapter);
        TaskScope scope = service.scope(new Object());
        TaskHandle first = scope.schedule(spec("first"));
        TaskHandle second = scope.schedule(spec("second"));
        assertThrows(IllegalStateException.class, scope::close);
        for (RecordingTaskAdapter.NativeHandle handle : adapter.handles) {
            assertTrue(handle.cancelled.get());
        }
        assertNull(service.get(first.getId()));
        assertNull(service.get(second.getId()));
        for (TaskSnapshot snapshot : scope.query()) {
            assertEquals(TaskStatus.CANCELLED, snapshot.getStatus());
        }
        assertThrows(IllegalStateException.class, () -> scope.schedule(spec("late")));
        service.close();
        assertTrue(adapter.closed);
    }

    @Test
    void serviceCleanupClosesAllOwnersAndAdapterDespiteACancellationFailure() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter(false, 0);
        ScheduledTasks service = new ScheduledTasks(adapter);
        TaskHandle first = service.scope(new Object()).schedule(spec("first-owner"));
        TaskHandle second = service.scope(new Object()).schedule(spec("second-owner"));
        assertThrows(IllegalStateException.class, service::close);
        for (RecordingTaskAdapter.NativeHandle handle : adapter.handles) {
            assertTrue(handle.cancelled.get());
        }
        assertTrue(adapter.closed);
        assertNull(service.get(first.getId()));
        assertNull(service.get(second.getId()));
    }

    @Test
    void failedActionIsArchivedEvenWhenCancellationAndErrorLoggingAlsoFail() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter(false, 0);
        IllegalStateException actionFailure = new IllegalStateException("action failed");
        IllegalArgumentException loggerFailure = new IllegalArgumentException("logger failed");
        List<Throwable> reported = new ArrayList<Throwable>();
        ScheduledTasks service = new ScheduledTasks(adapter, new TaskServiceSettings(), (owner, message, error) -> {
            reported.add(error);
            throw loggerFailure;
        }, null);
        TaskHandle task = service.scope(new Object()).schedule(TaskSpec.builder().action(context -> {
            throw actionFailure;
        }).build());
        adapter.fire(0);
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertNull(service.get(task.getId()));
        assertNotNull(service.query().get(0).getLastCompletedAt());
        assertEquals(Arrays.<Throwable>asList(actionFailure), reported);
        assertEquals("native cancellation failed", actionFailure.getSuppressed()[0].getMessage());
        assertEquals("logger failed", actionFailure.getSuppressed()[1].getMessage());
        service.close();
    }

    @Test
    void taskCanCancelItselfAndNativeHandleIsCancelled() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter);
        TaskHandle handle = service.scope(new Object()).schedule(TaskSpec.builder()
                .name("self-stop").interval(Duration.ofSeconds(1)).action(context -> context.cancel()).build());
        adapter.fire(0);
        assertEquals(TaskStatus.CANCELLED, handle.getStatus());
        assertTrue(adapter.handles.get(0).cancelled.get());
    }

    @Test
    void duplicateNamesAreListedWhileKeyedConflictsFollowPolicy() {
        TaskScope scope = new ScheduledTasks(new RecordingTaskAdapter()).scope(new Object());
        scope.schedule(spec("same"));
        scope.schedule(spec("same"));
        assertEquals(2, scope.query(TaskQuery.builder().nameContains("same").build()).size());
        TaskHandle first = scope.schedule(TaskSpec.builder().key("singleton").action(context -> { }).build());
        assertThrows(IllegalStateException.class,
                () -> scope.schedule(TaskSpec.builder().key("singleton").action(context -> { }).build()));
        TaskHandle kept = scope.schedule(TaskSpec.builder().key("singleton")
                .conflictPolicy(TaskConflictPolicy.KEEP_EXISTING).action(context -> { }).build());
        assertSame(first, kept);
    }

    @Test
    void scopeQueryCannotSeeAnotherOwnerAndHistoryIsBounded() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter, new TaskServiceSettings(1), (o, m, e) -> { }, null);
        TaskScope a = service.scope(new Object());
        TaskScope b = service.scope(new Object());
        a.schedule(spec("a"));
        b.schedule(spec("b"));
        adapter.fire(0);
        adapter.fire(1);
        assertEquals(0, a.query().size());
        assertEquals(1, service.query().size());
        assertEquals("b", service.query().get(0).getName());
    }

    @Test
    void registrationCallbackCancellingBeforeAttachmentCancelsReturnedNativeHandle() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter(true, -1);
        TaskHandle handle = new ScheduledTasks(adapter).scope(new Object())
                .schedule(TaskSpec.builder().action(context -> context.cancel()).build());
        assertTrue(handle.isCancelled());
        assertTrue(adapter.handles.get(0).cancelled.get());
    }

    @Test
    void completedSynchronousTaskCancelsNativeHandleAfterAttachment() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter(true, -1);
        TaskHandle handle = new ScheduledTasks(adapter).scope(new Object()).schedule(spec("sync"));
        assertEquals(TaskStatus.COMPLETED, handle.getStatus());
        assertTrue(adapter.handles.get(0).cancelled.get());
    }

    @Test
    void closedScopeRejectsNewTasks() {
        TaskScope scope = new ScheduledTasks(new RecordingTaskAdapter()).scope(new Object());
        scope.close();
        assertThrows(IllegalStateException.class, () -> scope.schedule(spec("late")));
    }

    @Test
    void closingOneOwnerCancelsOnlyThatOwnersTasks() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter);
        Object pluginOwner = new Object();
        TaskHandle pluginTask = service.scope(pluginOwner).schedule(spec("plugin"));
        TaskHandle otherTask = service.scope(new Object()).schedule(spec("other"));

        service.close(pluginOwner);

        assertEquals(TaskStatus.CANCELLED, pluginTask.getStatus());
        assertTrue(pluginTask.isCancelled());
        assertEquals(TaskStatus.SCHEDULED, otherTask.getStatus());
        assertFalse(otherTask.isCancelled());
        assertTrue(adapter.handles.get(0).cancelled.get());
        assertFalse(adapter.handles.get(1).cancelled.get());
    }

    @Test
    void canonicalTaskLookupWorksForServiceAndOwnerScope() {
        ScheduledTasks service = new ScheduledTasks(new RecordingTaskAdapter());
        TaskScope scope = service.scope(new Object());
        TaskHandle handle = scope.schedule(TaskSpec.builder().key("cleanup").action(context -> { }).build());

        assertSame(handle, service.get(handle.getId()));
        assertSame(handle, service.require(handle.getId()));
        assertSame(handle, scope.get(handle.getId()));
        assertSame(handle, scope.getByKey("cleanup"));
        assertThrows(IllegalStateException.class, () -> service.require(TaskId.random()));
        assertThrows(UnsupportedOperationException.class, () -> scope.query().clear());
    }

    @Test
    void aFinishedTaskCannotBeCancelledAndKeepsItsStatus() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        TaskHandle task = new ScheduledTasks(adapter).scope(new Object()).schedule(spec("once"));
        adapter.fire(0);

        assertFalse(task.cancelIfActive());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertFalse(task.isCancelled());
    }

    @Test
    void aRunThePlatformAlreadyQueuedDoesNothingAfterCancel() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        AtomicInteger runs = new AtomicInteger();
        TaskHandle task = new ScheduledTasks(adapter).scope(new Object())
                .schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).action(context -> runs.incrementAndGet()).build());
        task.cancel();
        adapter.fire(0);

        assertEquals(0, runs.get());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }
}
