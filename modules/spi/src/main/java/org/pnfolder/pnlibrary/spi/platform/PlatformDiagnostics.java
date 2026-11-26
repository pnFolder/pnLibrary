package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Diagnostic snapshots supplied by a platform. */
public interface PlatformDiagnostics extends PlatformSnapshotProvider, PlatformDiagnosticReport {
    @NotNull
    @Override
    default Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return snapshot().asMap();
    }
}
