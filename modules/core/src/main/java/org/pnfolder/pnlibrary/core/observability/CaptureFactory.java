package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.logging.DiagnosticCapture;
import org.pnfolder.pnlibrary.core.logging.DiagnosticLogBuffer;

/** Builds the log capture once the support runtime has its incident buffer and error sink. */
@FunctionalInterface
public interface CaptureFactory {
    @NotNull
    DiagnosticCapture create(@NotNull DiagnosticLogBuffer logs, @NotNull RuntimeErrorSink onError);
}
