package org.pnfolder.pnlibrary.bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.DiagnosticDetailsCollector;
import org.pnfolder.pnlibrary.spi.platform.DiagnosticSections;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;

/** Collects actionable Bukkit state without enumerating private or high-volume server data. */
final class BukkitDiagnosticDetailsCollector {
    private static final double LOW_TPS_THRESHOLD = 18.0;
    private static final long DIAGNOSTIC_TIMEOUT_SECONDS = 15L;

    /**
     * Collects on the server thread, handing the work to {@code global} when called from elsewhere (and always on
     * Folia, which has no single main thread); waits a bounded time for the answer.
     */
    Map<String, Object> collect(Supplier<PlatformSnapshot> snapshot, boolean includeSensitive,
                                Consumer<Runnable> global) {
        if (!ServerCapabilities.isFolia() && Bukkit.isPrimaryThread()) {
            return collect(snapshot.get(), includeSensitive);
        }
        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        global.accept(() -> {
            Attempt<Map<String, Object>> collected = Failures.runCatchingNonFatal(
                () -> collect(snapshot.get(), includeSensitive));
            if (collected.isSuccess()) {
                result.complete(collected.getOrNull());
            } else {
                result.completeExceptionally(collected.exceptionOrNull());
            }
        });
        try {
            return result.get(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException error) {
            throw PlatformErrors.rethrow(error);
        }
    }

    private Map<String, Object> collect(PlatformSnapshot snapshot, boolean includeSensitive) {
        return new DiagnosticDetailsCollector(() -> snapshot).collect(includeSensitive, this::enrich);
    }

    private void enrich(Map<String, Object> details) {
        Server server = Bukkit.getServer();
        Plugin[] plugins = server.getPluginManager().getPlugins();
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Plugin plugin : plugins) {
            dependencies.put(plugin.getName(), plugin.getDescription().getDepend());
            names.add(plugin.getName());
        }
        List<Map<String, Object>> missingDependencies = DiagnosticSections.missingDependencies(dependencies, names);
        List<Long> pings = pings(server);
        double[] tps = ServerCapabilities.getTPS();
        Double oneMinuteTps = tps != null && tps.length > 0 ? tps[0] : null;
        List<Map<String, Object>> signals = DiagnosticSections.commonSignals(missingDependencies, pings);
        if (oneMinuteTps != null && oneMinuteTps < LOW_TPS_THRESHOLD) {
            Map<String, Object> low = new LinkedHashMap<>();
            low.put("code", "lowTps");
            low.put("oneMinute", oneMinuteTps);
            signals.add(low);
        }
        details.put("server", serverSection(server));
        details.put("performance", performanceSection(server, tps, pings));
        details.put("worlds", worlds(server));
        details.put("pluginStates", pluginStates(plugins));
        details.put("missingRequiredDependencies", missingDependencies);
        details.put("health", DiagnosticSections.health(signals));
    }

    private static List<Long> pings(Server server) {
        List<Long> pings = new ArrayList<>();
        for (Player player : server.getOnlinePlayers()) {
            Long ping = Failures.runCatchingNonFatal(() -> {
                Object value = player.getClass().getMethod("getPing").invoke(player);
                return value instanceof Number ? ((Number) value).longValue() : null;
            }).getOrNull();
            if (ping != null) {
                pings.add(ping);
            }
        }
        return pings;
    }

    private static Map<String, Object> serverSection(Server server) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("name", server.getName());
        section.put("version", server.getVersion());
        section.put("minecraftVersion", ServerCapabilities.getMinecraftVersion());
        section.put("onlineMode", server.getOnlineMode());
        section.put("maxPlayers", server.getMaxPlayers());
        section.put("folia", ServerCapabilities.isFolia());
        return section;
    }

    private static Map<String, Object> performanceSection(Server server, double[] tps, List<Long> pings) {
        Map<String, Object> section = new LinkedHashMap<>();
        List<Double> firstThree = null;
        if (tps != null && tps.length > 0) {
            firstThree = new ArrayList<>();
            for (int index = 0; index < Math.min(3, tps.length); index++) {
                firstThree.add(tps[index]);
            }
        }
        section.put("tps", firstThree);
        section.put("onlinePlayers", server.getOnlinePlayers().size());
        section.put("pingMs", DiagnosticSections.pingSummary(pings));
        return section;
    }

    private static List<Map<String, Object>> worlds(Server server) {
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (World world : server.getWorlds()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", world.getName());
            row.put("environment", world.getEnvironment().name());
            row.put("players", world.getPlayers().size());
            if (!ServerCapabilities.isFolia()) {
                Integer chunks = count(world, "getChunkCount");
                if (chunks != null) {
                    row.put("loadedChunks", chunks);
                }
                Integer entities = count(world, "getEntityCount");
                if (entities != null) {
                    row.put("entities", entities);
                }
            }
            worlds.add(row);
        }
        return worlds;
    }

    private static List<Map<String, Object>> pluginStates(Plugin[] plugins) {
        List<Map<String, Object>> states = new ArrayList<>();
        for (Plugin installed : plugins) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", installed.getName());
            row.put("version", installed.getDescription().getVersion());
            row.put("enabled", installed.isEnabled());
            states.add(row);
        }
        return states;
    }

    /**
     * Reads a cheap counter exposed by Paper-based servers. {@code World.getEntities()} and
     * {@code getLoadedChunks()} copy every element on the main thread, so they are never used as a fallback; the
     * value is simply omitted.
     */
    private static Integer count(Object world, String method) {
        return Failures.runCatchingNonFatal(() -> {
            Method read = world.getClass().getMethod(method);
            Object value = read.invoke(world);
            return value instanceof Number ? ((Number) value).intValue() : null;
        }).getOrNull();
    }
}
