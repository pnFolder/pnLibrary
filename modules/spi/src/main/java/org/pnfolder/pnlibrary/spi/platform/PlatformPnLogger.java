package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

import java.util.Map;

/**
 * A {@link PnLogger} for runtime parts that hold a {@link PlatformLogging} instead of a plugin logger. Lines carry the
 * {@code name} and go where {@code logging} sends them; when it is the library's recording sink, that is the black box too.
 * Context keys are not forwarded since {@link PlatformLogging} does not carry structured fields.
 */
public final class PlatformPnLogger implements PnLogger {
    private final PlatformLogging logging;
    private final Object owner;
    private final String name;

    public PlatformPnLogger(@NotNull PlatformLogging logging, @NotNull Object owner, @NotNull String name) {
        this.logging = logging;
        this.owner = owner;
        this.name = name;
    }

    @Override
    public void log(
            @NotNull LogLevel level,
            @NotNull String message,
            @Nullable Throwable error,
            @NotNull Map<String, ?> context) {
        logging.log(owner, level, "[" + name + "] " + message, error);
    }
}
