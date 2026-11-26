package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/** Chart identifier rules shared by every metrics provider. */
public final class ChartIds {
    private static final Pattern CHART_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private ChartIds() {
    }

    /**
     * Trims {@code id} and rejects values that a metrics provider could not represent; every supported provider
     * accepts the rest.
     *
     * @throws IllegalArgumentException when the trimmed identifier is not 1 to 64 letters, digits, {@code _} or {@code -}
     */
    @NotNull
    public static String validChartId(@NotNull String id) {
        String trimmed = id.trim();
        if (!CHART_ID.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid metrics chart id: " + id);
        }
        return trimmed;
    }
}
