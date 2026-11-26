package org.pnfolder.pnlibrary.api.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A {@link PnLogger} that adds its facts to every line it writes, then passes the line to the base logger. */
final class ContextPnLogger implements PnLogger {
    private final PnLogger base;
    private final Map<String, Object> facts;

    ContextPnLogger(PnLogger base, Map<String, ?> facts) {
        this.base = base;
        this.facts = new LinkedHashMap<String, Object>(facts);
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error,
                    @NotNull Map<String, ?> context) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>(facts);
        merged.putAll(context);
        base.log(level, message, error, merged);
    }

    @Override
    public void record(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        base.record(level, message, error);
    }
}
