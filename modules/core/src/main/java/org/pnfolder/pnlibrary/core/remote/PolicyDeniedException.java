package org.pnfolder.pnlibrary.core.remote;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.console.ReportedFailure;

/** The startup policy stopped the plugin; its console card has already been printed. */
public final class PolicyDeniedException extends IllegalStateException implements ReportedFailure {
    public PolicyDeniedException(@NotNull String reason) {
        super("Startup stopped by policy: " + reason);
    }
}
