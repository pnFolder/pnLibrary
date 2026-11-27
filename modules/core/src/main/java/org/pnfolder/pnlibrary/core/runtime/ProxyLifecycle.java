package org.pnfolder.pnlibrary.core.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;

/**
 * The start and stop of the library on a proxy, the same on every platform: launch, proxy side, listeners, and the
 * reverse order on the way out. A start that fails stops what already started and rethrows the failure.
 */
public final class ProxyLifecycle implements AutoCloseable {
    /** What already started, the last started on top, so that closing goes in the reverse order. */
    private final Deque<AutoCloseable> started = new ArrayDeque<>();

    /**
     * Runs the steps of {@code startup}.
     *
     * @throws IllegalStateException when the library already runs here
     */
    public synchronized void start(@NotNull ProxyStartup startup) {
        if (!started.isEmpty()) {
            throw new IllegalStateException("The library is already running on this proxy");
        }
        try {
            PnLibraryRuntimeHost host = begin(startup.launch());
            begin(startup.createProxy()).start(host);
            begin(startup.listen(host));
        } catch (Throwable error) {
            ResourceCleanup.suppressInto(error, this::close);
            throw error;
        }
    }

    /** Closes the listeners, the proxy side and the library; the first failure is thrown after every step ran. */
    @Override
    public synchronized void close() {
        List<CleanupStep> steps = new ArrayList<>();
        for (AutoCloseable resource : started) {
            steps.add(resource::close);
        }
        started.clear();
        ResourceCleanup.closeAll(steps);
    }

    private <T extends AutoCloseable> T begin(T resource) {
        started.push(resource);
        return resource;
    }
}
