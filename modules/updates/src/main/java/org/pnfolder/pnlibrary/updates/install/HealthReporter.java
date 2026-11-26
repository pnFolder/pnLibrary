package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.service.UpdateHealthReport;

/** Receives what a restart actually loaded; a failure to record it stops the verification so it is tried again. */
@FunctionalInterface
public interface HealthReporter {
    /** Records {@code report}. */
    void report(@NotNull UpdateHealthReport report) throws IOException;
}
