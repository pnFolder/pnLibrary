package org.pnfolder.pnlibrary.core.logging;

import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;
import org.pnfolder.pnlibrary.spi.platform.diagnostics.PlatformSnapshotProvider;

/** What a startup card needs from the platform: where to print, who owns the plugin and what it runs on. */
final class BoxEnvironment {
    final PlatformLogging logging;
    final PlatformOwnership ownership;
    final PlatformSnapshotProvider diagnostics;
    final PlatformIdentity identity;
    final DiagnosticCapture capture;

    BoxEnvironment(PlatformLogging logging, PlatformOwnership ownership, PlatformSnapshotProvider diagnostics,
                   PlatformIdentity identity, DiagnosticCapture capture) {
        this.logging = logging;
        this.ownership = ownership;
        this.diagnostics = diagnostics;
        this.identity = identity;
        this.capture = capture;
    }
}
