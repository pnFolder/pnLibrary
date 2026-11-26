package org.pnfolder.pnlibrary.core.metrics.bstats;

import org.bstats.config.MetricsConfig;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What the {@code bStats} folder next to the plugins says about sending statistics.
 *
 * <p>Every plugin of the server reads the same file, so the owner of the server opts out once for all of them. A file
 * that cannot be read counts as an opt-out: without knowing the answer nothing is sent.
 */
public final class BStatsSettings {
    private static final String HEADER = "# bStats (https://bStats.org) collects some basic information for plugin authors, like how\n"
            + "# many people use their plugin and their total player count. It's recommended to keep bStats\n"
            + "# enabled, but if you're not comfortable with this, you can turn this setting off. There is no\n"
            + "# performance penalty associated with having metrics enabled, and data sent to bStats is fully\n"
            + "# anonymous.";

    private final boolean enabled;
    private final String serverUuid;
    private final boolean logFailedRequests;
    private final boolean logSentData;
    private final boolean logResponseStatusText;
    private final boolean createdNow;

    /** Settings that log nothing and were not created by this call. */
    public BStatsSettings(boolean enabled, @NotNull String serverUuid) {
        this(enabled, serverUuid, false, false, false, false);
    }

    /**
     * Creates settings.
     *
     * @param enabled whether statistics may be sent
     * @param serverUuid anonymous identifier of this server, the same for every plugin
     * @param logFailedRequests whether failed requests are written to the log
     * @param logSentData whether the data that is sent is written to the log
     * @param logResponseStatusText whether the answer of bStats is written to the log
     * @param createdNow whether this call created the file, so a platform may show a short notice about it
     */
    public BStatsSettings(boolean enabled, @NotNull String serverUuid, boolean logFailedRequests, boolean logSentData,
                          boolean logResponseStatusText, boolean createdNow) {
        this.enabled = enabled;
        this.serverUuid = serverUuid;
        this.logFailedRequests = logFailedRequests;
        this.logSentData = logSentData;
        this.logResponseStatusText = logResponseStatusText;
        this.createdNow = createdNow;
    }

    /** Whether statistics may be sent. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Anonymous identifier of this server, the same for every plugin. */
    @NotNull
    public String getServerUuid() {
        return serverUuid;
    }

    /** Whether failed requests are written to the log. */
    public boolean isLogFailedRequests() {
        return logFailedRequests;
    }

    /** Whether the data that is sent is written to the log. */
    public boolean isLogSentData() {
        return logSentData;
    }

    /** Whether the answer of bStats is written to the log. */
    public boolean isLogResponseStatusText() {
        return logResponseStatusText;
    }

    /** Whether this call created the file, so a platform may show a short notice about it. */
    public boolean isCreatedNow() {
        return createdNow;
    }

    /** Settings that send nothing; used when the file cannot be read or written at all. */
    @NotNull
    public static BStatsSettings disabled() {
        return new BStatsSettings(false, UUID.randomUUID().toString());
    }

    /**
     * Reads {@code bStats/config.yml}, the layout Bukkit and BungeeCord plugins share, and creates it when it is missing
     * or has no server identifier. Values already in the file, such as an opt-out, are kept.
     */
    @NotNull
    public static BStatsSettings yaml(@NotNull File file) {
        boolean existed = file.exists();
        Map<String, String> existing = new LinkedHashMap<>();
        if (existed) {
            try {
                existing = parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (IOException unreadable) {
                return disabled();
            }
        }
        String uuid = existing.get("serverUuid");
        boolean hasUuid = uuid != null && !uuid.trim().isEmpty();
        BStatsSettings settings = new BStatsSettings(
                flag(existing.get("enabled"), true),
                hasUuid ? uuid : UUID.randomUUID().toString(),
                flag(existing.get("logFailedRequests"), false),
                flag(existing.get("logSentData"), false),
                flag(existing.get("logResponseStatusText"), false),
                !existed);
        if (!hasUuid) {
            Failures.runCatchingNonFatal(() -> {
                write(file, settings);
                return null;
            });
        }
        return settings;
    }

    /** Reads {@code bStats/config.txt}, the layout Velocity plugins share, and creates it when it is missing. */
    @NotNull
    public static BStatsSettings properties(@NotNull File file) {
        try {
            MetricsConfig config = new MetricsConfig(file, true);
            return new BStatsSettings(config.isEnabled(), config.getServerUUID(), config.isLogErrorsEnabled(),
                    config.isLogSentDataEnabled(), config.isLogResponseStatusTextEnabled(), !config.didExistBefore());
        } catch (IOException unreadable) {
            return disabled();
        }
    }

    private static Map<String, String> parse(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : content.split("\r\n|\n|\r")) {
            String line = raw.trim();
            int colon = line.indexOf(':');
            if (line.isEmpty() || line.startsWith("#") || colon < 0) {
                continue;
            }
            values.put(line.substring(0, colon).trim(), unquote(line.substring(colon + 1).trim()));
        }
        return values;
    }

    private static String unquote(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isQuote(value.charAt(start))) {
            start++;
        }
        while (end > start && isQuote(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isQuote(char character) {
        return character == '"' || character == '\'';
    }

    private static boolean flag(String value, boolean fallback) {
        return value == null ? fallback : "true".equals(value.toLowerCase(Locale.ROOT));
    }

    private static void write(File file, BStatsSettings settings) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        String separator = System.lineSeparator();
        String content = HEADER + separator
                + "enabled: " + settings.enabled + separator
                + "serverUuid: \"" + settings.serverUuid + "\"" + separator
                + "logFailedRequests: " + settings.logFailedRequests + separator
                + "logSentData: " + settings.logSentData + separator
                + "logResponseStatusText: " + settings.logResponseStatusText + separator;
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
