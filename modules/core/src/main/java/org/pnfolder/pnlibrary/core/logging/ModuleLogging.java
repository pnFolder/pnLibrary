package org.pnfolder.pnlibrary.core.logging;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshotProvider;

import java.util.Map;

/** Adapts library logging, diagnostics capture, and lifecycle summaries to one platform. */
public final class ModuleLogging implements LoggingService {
    private final DiagnosticCapture capture;
    private final BoxEnvironment environment;

    public ModuleLogging(@NotNull PlatformLogging logging, @NotNull PlatformOwnership ownership,
                         @NotNull PlatformSnapshotProvider diagnostics, @NotNull PlatformIdentity identity,
                         @NotNull DiagnosticCapture capture) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.environment = new BoxEnvironment(logging, ownership, diagnostics, identity, capture);
    }

    public ModuleLogging(@NotNull PlatformLogging logging, @NotNull PlatformOwnership ownership,
                         @NotNull PlatformSnapshotProvider diagnostics, @NotNull PlatformIdentity identity,
                         @Nullable DiagnosticLogBuffer diagnosticLogs, @Nullable JournalSink journal) {
        this(logging, ownership, diagnostics, identity,
            new DiagnosticCapture(ownership, logging, diagnosticLogs, journal));
    }

    public ModuleLogging(@NotNull PlatformLogging logging, @NotNull PlatformOwnership ownership,
                         @NotNull PlatformSnapshotProvider diagnostics, @NotNull PlatformIdentity identity,
                         @Nullable DiagnosticLogBuffer diagnosticLogs) {
        this(logging, ownership, diagnostics, identity, diagnosticLogs, null);
    }

    public ModuleLogging(@NotNull PlatformLogging logging, @NotNull PlatformOwnership ownership,
                         @NotNull PlatformSnapshotProvider diagnostics, @NotNull PlatformIdentity identity) {
        this(logging, ownership, diagnostics, identity, null, null);
    }

    @Override
    public @NotNull PnLogger logger(@NotNull Object owner, @NotNull String name) {
        return new PnLogger() {
            @Override
            public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error,
                            @NotNull Map<String, ?> context) {
                capture.write(owner, level, "[" + name + "] " + message, error, context);
            }

            @Override
            public void record(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
                capture.captureOnly(owner, level, "[" + name + "] " + message, error);
            }
        };
    }

    @Override
    public @NotNull MessageBox box(@NotNull Object owner, @NotNull String title) {
        return new StartupBox(environment, owner, title, BoxKind.GENERIC, title, null);
    }

    @Override
    public @NotNull MessageBox shutdownBox(@NotNull Object owner, @NotNull String title) {
        return new StartupBox(environment, owner, title, BoxKind.DISABLED, null, null);
    }

    @Override
    public @NotNull MessageBox box(@NotNull Object owner, @NotNull String name, @NotNull String version) {
        return new StartupBox(environment, owner, name + " " + version, BoxKind.ENABLED, name, version);
    }

    @Override
    public @NotNull MessageBox shutdownBox(@NotNull Object owner, @NotNull String name, @NotNull String version) {
        return new StartupBox(environment, owner, name + " " + version, BoxKind.DISABLED, name, version);
    }
}
