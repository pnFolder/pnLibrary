package org.pnfolder.pnlibrary.core.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskBuilder;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;

/** A platform that only remembers what was scheduled; the test fires the callbacks itself. */
final class RecordingTaskAdapter implements PlatformTaskAdapter {
    final List<PlatformTaskRequest> requests = new ArrayList<>();
    final List<NativeHandle> handles = new ArrayList<>();
    boolean closed;
    private final boolean fireDuringSchedule;
    private final int failingCancelIndex;

    RecordingTaskAdapter() {
        this(false, -1);
    }

    RecordingTaskAdapter(boolean fireDuringSchedule, int failingCancelIndex) {
        this.fireDuringSchedule = fireDuringSchedule;
        this.failingCancelIndex = failingCancelIndex;
    }

    @Override
    public PlatformTaskBuilder buildTask() {
        return new PlatformTaskBuilder(this::dispatch);
    }

    private PlatformTaskHandle dispatch(PlatformTaskRequest request) {
        requests.add(request);
        RuntimeException failure = handles.size() == failingCancelIndex ? new IllegalStateException("native cancellation failed") : null;
        NativeHandle handle = new NativeHandle(failure);
        handles.add(handle);
        if (fireDuringSchedule) {
            request.getCallback().run();
        }
        return handle;
    }

    void fire(int index) {
        requests.get(index).getCallback().run();
    }

    @Override
    public void close() {
        closed = true;
    }

    /** The platform's handle of one scheduled task. */
    static final class NativeHandle implements PlatformTaskHandle {
        final AtomicBoolean cancelled = new AtomicBoolean();
        private final RuntimeException failure;

        NativeHandle(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean cancel() {
            boolean changed = cancelled.compareAndSet(false, true);
            if (changed && failure != null) {
                throw failure;
            }
            return changed;
        }
    }
}
