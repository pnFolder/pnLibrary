package org.pnfolder.pnlibrary.spi.tasks;

import org.jetbrains.annotations.NotNull;

/** Adapter used by runtimes that do not provide native task scheduling. */
public final class UnsupportedPlatformTaskAdapter implements PlatformTaskAdapter {
    /** The only instance. */
    public static final UnsupportedPlatformTaskAdapter INSTANCE = new UnsupportedPlatformTaskAdapter();

    private UnsupportedPlatformTaskAdapter() {
    }

    /** Returns a builder whose {@link PlatformTaskBuilder#schedule()} always fails. */
    @NotNull
    @Override
    public PlatformTaskBuilder buildTask() {
        return new PlatformTaskBuilder(request -> {
            throw new UnsupportedOperationException("Native task scheduling is not supported by this platform");
        });
    }
}
