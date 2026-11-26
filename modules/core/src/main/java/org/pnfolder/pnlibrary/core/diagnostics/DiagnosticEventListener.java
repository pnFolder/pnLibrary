package org.pnfolder.pnlibrary.core.diagnostics;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;

/** Sees every recorded diagnostic event after its text and fields were redacted. */
@FunctionalInterface
public interface DiagnosticEventListener {
    /** Called once for each recorded event. */
    void onEvent(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component,
                 @NotNull String code, @NotNull String message, @Nullable Throwable error,
                 @NotNull Map<String, Object> fields);
}
