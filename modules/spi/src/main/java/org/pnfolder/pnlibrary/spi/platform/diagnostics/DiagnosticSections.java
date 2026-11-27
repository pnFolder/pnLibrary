package org.pnfolder.pnlibrary.spi.platform.diagnostics;

import org.pnfolder.pnlibrary.spi.platform.ProxyBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Building blocks shared by every platform's diagnostic sections, so the same fact is computed the same way
 * on Bukkit, BungeeCord and Velocity.
 */
public final class DiagnosticSections {
    /** A player latency at or above this value (milliseconds, 95th percentile) raises a health signal. */
    public static final long HIGH_PING_THRESHOLD_MS = 300;

    private static final String UNASSIGNED_BACKEND = "[unassigned]";

    private DiagnosticSections() {
    }

    /**
     * Summarizes player latencies in milliseconds.
     *
     * <p>The 95th percentile uses the nearest-rank method, so a small population with one slow player still
     * reports that player's latency.
     */
    @NotNull
    public static Map<String, Object> pingSummary(@NotNull Collection<Long> pings) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("samples", pings.size());
        summary.put("min", pings.isEmpty() ? null : Collections.min(pings));
        summary.put("average", pings.isEmpty() ? null : average(pings));
        summary.put("max", pings.isEmpty() ? null : Collections.max(pings));
        summary.put("p95", percentile(pings, 0.95));
        return summary;
    }

    /** Nearest-rank percentile of {@code values}, or null when there are none. */
    @Nullable
    public static Long percentile(@NotNull Collection<Long> values, double fraction) {
        if (!(fraction >= 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("Fraction must be within 0..1");
        }
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(sorted.size() * fraction) - 1;
        return sorted.get(Math.max(0, Math.min(rank, sorted.size() - 1)));
    }

    /**
     * Lists plugins that require a plugin which is not installed.
     *
     * @param required hard dependencies of each installed plugin, keyed by the plugin's own identifier
     * @param installed identifiers that are present on the server
     */
    @NotNull
    public static List<Map<String, Object>> missingDependencies(
            @NotNull Map<String, ? extends Collection<String>> required, @NotNull Set<String> installed) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : required.entrySet()) {
            List<String> missing = new ArrayList<>();
            for (String dependency : entry.getValue()) {
                if (!installed.contains(dependency)) {
                    missing.add(dependency);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            Collections.sort(missing);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plugin", entry.getKey());
            row.put("missing", missing);
            result.add(row);
        }
        return result;
    }

    /** Signals every platform reports: unmet dependencies and slow players. */
    @NotNull
    public static List<Map<String, Object>> commonSignals(
            @NotNull List<Map<String, Object>> missingDependencies, @NotNull Collection<Long> pings) {
        List<Map<String, Object>> signals = new ArrayList<>();
        if (!missingDependencies.isEmpty()) {
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("code", "missingRequiredDependencies");
            signal.put("count", missingDependencies.size());
            signals.add(signal);
        }
        Long p95 = percentile(pings, 0.95);
        if (p95 != null && p95 >= HIGH_PING_THRESHOLD_MS) {
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("code", "highPlayerLatency");
            signal.put("p95Ms", p95);
            signals.add(signal);
        }
        return signals;
    }

    /** The {@code health} section: {@code healthy} without signals, otherwise {@code attention} with the signals. */
    @NotNull
    public static Map<String, Object> health(@NotNull List<Map<String, Object>> signals) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", signals.isEmpty() ? "healthy" : "attention");
        health.put("signals", signals);
        return health;
    }

    /**
     * Adds the sections every proxy reports: unmet dependencies, backend servers, online players per backend
     * with their latency summary, and health. Backend addresses are read and added only when {@code includeSensitive}.
     *
     * @param playerBackends the backend of each online player, or null while the player is not connected to one
     * @param pings latencies of the online players in milliseconds; negative values mean unknown and are skipped
     */
    public static void proxySections(
            @NotNull Map<String, Object> details,
            boolean includeSensitive,
            @NotNull List<Map<String, Object>> missingDependencies,
            @NotNull List<ProxyBackend> backends,
            @NotNull List<String> playerBackends,
            @NotNull Collection<Long> pings) {
        List<Long> knownPings = new ArrayList<>();
        for (Long ping : pings) {
            if (ping >= 0) {
                knownPings.add(ping);
            }
        }
        details.put("missingRequiredDependencies", missingDependencies);
        details.put("backends", backendRows(backends, includeSensitive));
        Map<String, Object> players = new LinkedHashMap<>();
        players.put("online", playerBackends.size());
        players.put("byBackend", playersByBackend(playerBackends));
        players.put("pingMs", pingSummary(knownPings));
        details.put("players", players);
        details.put("health", health(commonSignals(missingDependencies, knownPings)));
    }

    private static List<Map<String, Object>> backendRows(List<ProxyBackend> backends, boolean includeSensitive) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProxyBackend backend : backends) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", backend.getName());
            row.put("players", backend.getPlayers());
            if (includeSensitive) {
                InetSocketAddress address = backend.getAddress().get();
                row.put("host", address.getHostString());
                row.put("port", address.getPort());
            }
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Integer> playersByBackend(List<String> playerBackends) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String backend : playerBackends) {
            String key = backend != null ? backend : UNASSIGNED_BACKEND;
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
        }
        return counts;
    }

    private static double average(Collection<Long> values) {
        double sum = 0.0;
        for (Long value : values) {
            sum += value;
        }
        return sum / values.size();
    }
}
