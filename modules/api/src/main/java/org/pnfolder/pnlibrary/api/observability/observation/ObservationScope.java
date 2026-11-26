package org.pnfolder.pnlibrary.api.observability.observation;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the parts of one {@link ObservationRequest}.
 *
 * <p>The scope is created by {@code ObservabilityService.capture} or {@code ObservabilityService.failure} and handed to
 * a {@code Consumer}; callers do not retain it.
 */
public final class ObservationScope {
    private static final long MAX_KEEP_DAYS = 3650L;
    private static final String DEFAULT_MESSAGE = "Diagnostic observation";

    private String plugin;
    private String source;
    private String message = DEFAULT_MESSAGE;
    private ObservationLevel level = ObservationLevel.INFO;
    private final Map<String, String> data = new LinkedHashMap<>();
    private final List<Path> files = new ArrayList<>();
    private Duration keepFor;

    /** Creates an empty scope; the library does this for the caller. */
    @ApiStatus.Internal
    public ObservationScope() {
    }

    /** Overrides the plugin name the observation is attributed to. */
    @NotNull
    public ObservationScope plugin(@NotNull String value) {
        plugin = value;
        return this;
    }

    /** Names the component that produced the observation. */
    @NotNull
    public ObservationScope source(@NotNull String value) {
        source = value;
        return this;
    }

    /** Sets the observation text. */
    @NotNull
    public ObservationScope message(@NotNull String value) {
        message = value;
        return this;
    }

    /** Sets the severity, {@code INFO} by default. */
    @NotNull
    public ObservationScope level(@NotNull ObservationLevel value) {
        level = value;
        return this;
    }

    /** Attaches one key-value detail; the value is stored as text. */
    @NotNull
    public ObservationScope data(@NotNull String key, @Nullable Object value) {
        data.put(key, value == null ? "null" : value.toString());
        return this;
    }

    /** Attaches key-value details; values are stored as text. */
    @NotNull
    public ObservationScope data(@NotNull Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            data(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /** Attaches files to include with the observation. */
    @NotNull
    public ObservationScope files(@NotNull Path... paths) {
        Collections.addAll(files, paths);
        return this;
    }

    /**
     * Keeps the observation for {@code duration} regardless of its level, at most 3650 days.
     *
     * <p>The period is set by the plugin author in code; without it retention follows the level.
     */
    @NotNull
    public ObservationScope keepFor(@NotNull Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Retention must be positive");
        }
        if (duration.compareTo(Duration.ofDays(MAX_KEEP_DAYS)) > 0) {
            throw new IllegalArgumentException("Retention must not exceed " + MAX_KEEP_DAYS + " days");
        }
        keepFor = duration;
        return this;
    }

    /** Builds the request of a plain observation. */
    @ApiStatus.Internal
    @NotNull
    public ObservationRequest build() {
        return build(null);
    }

    /** Builds the request of a failure: the level is always {@link ObservationLevel#ERROR}. */
    @ApiStatus.Internal
    @NotNull
    public ObservationRequest build(@Nullable Throwable error) {
        String text = error != null && error.getMessage() != null && message.equals(DEFAULT_MESSAGE) ? error.getMessage() : message;
        Map<String, String> all = new LinkedHashMap<>(data);
        if (keepFor != null) {
            all.put(ObservationKeys.KEEP_FOR_MS, Long.toString(keepFor.toMillis()));
        }
        return new ObservationRequest(plugin, source, text, error == null ? level : ObservationLevel.ERROR, all,
                new ArrayList<>(files), error);
    }
}
