package org.pnfolder.pnlibrary.spi.tasks;

import org.jetbrains.annotations.NotNull;

/** Native scheduling boundary implemented by each platform runtime. */
public interface PlatformTaskAdapter extends AutoCloseable {
    /** Starts describing a task; finish with {@link PlatformTaskBuilder#schedule()}. */
    @NotNull
    PlatformTaskBuilder buildTask();

    /** Releases platform scheduler resources. */
    @Override
    default void close() {
    }
}
