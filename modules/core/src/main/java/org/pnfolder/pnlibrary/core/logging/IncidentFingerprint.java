package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Computes the id shared by every repeat of one incident. */
@FunctionalInterface
public interface IncidentFingerprint {
    @NotNull String of(@NotNull List<String> context, @NotNull String message, @Nullable Throwable error);
}
