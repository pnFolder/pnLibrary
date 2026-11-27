package org.pnfolder.pnlibrary.spi.platform.diagnostics;

import org.jetbrains.annotations.NotNull;

/** Provides the lightweight native platform snapshot used by runtime logging. */
public interface PlatformSnapshotProvider {
    /** Current lightweight platform snapshot. */
    @NotNull
    default PlatformSnapshot snapshot() {
        return PlatformSnapshot.unknown("unknown");
    }
}
