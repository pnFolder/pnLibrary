package ru.privatenull.pnlibrary.lifecycle;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.update.GitHubUpdater;
import ru.privatenull.pnlibrary.database.DatabaseExecutor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the infrastructure shared by PrivateNull plugins: updater, join
 * notifications, bStats and lifecycle banners.
 *
 * <p>The GitHub repository and notification permission are derived from the
 * plugin metadata, including {@code bstats-id} from plugin.yml.</p>
 */
public final class PluginRuntime implements AutoCloseable {

    private static final Pattern GITHUB_REPOSITORY = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?");

    private final JavaPlugin plugin;
    private final GitHubUpdater updates;
    private final Metrics metrics;
    private final Listener joinListener;
    private DatabaseExecutor databaseExecutor;
    private boolean closed;

    private PluginRuntime(JavaPlugin plugin, int bStatsPluginId) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        if (bStatsPluginId <= 0) {
            throw new IllegalArgumentException("bStats plugin id must be positive");
        }

        String pluginName = plugin.getDescription().getName();
        String repository = repository(pluginName, plugin.getDescription().getWebsite());
        List<String> permissions = plugin.getDescription().getPermissions().stream()
                .map(Permission::getName)
                .toList();
        String notifyPermission = notificationPermission(pluginName, permissions);

        updates = new GitHubUpdater(plugin, repository, notifyPermission, supportUrl());
        metrics = new Metrics(plugin, bStatsPluginId);
        joinListener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                updates.notifyAdminOnJoin(event.getPlayer());
            }
        };

        plugin.getServer().getPluginManager().registerEvents(joinListener, plugin);
        updates.start();
        printBanner("ENABLED");
    }

    public static PluginRuntime start(JavaPlugin plugin) {
        return new PluginRuntime(plugin, bStatsId(plugin));
    }

    public GitHubUpdater updates() {
        return updates;
    }

    public synchronized DatabaseExecutor databaseExecutor() {
        if (closed) throw new IllegalStateException("Plugin runtime is closed");
        if (databaseExecutor == null) databaseExecutor = new DatabaseExecutor(plugin);
        return databaseExecutor;
    }

    /** Adds a string-valued bStats chart without exposing bStats in consumer code. */
    public PluginRuntime simplePie(String chartId, Supplier<String> value) {
        if (closed) throw new IllegalStateException("Plugin runtime is closed");
        Supplier<String> source = Objects.requireNonNull(value, "value");
        metrics.addCustomChart(new SimplePie(
                Objects.requireNonNull(chartId, "chartId"),
                source::get
        ));
        return this;
    }

    /** Restarts the shared updater; plugin configuration is not involved. */
    public void reload() {
        if (closed) throw new IllegalStateException("Plugin runtime is closed");
        updates.restart();
    }

    public static String supportUrl() {
        return "https://discord.gg/SZxPP9surw";
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(joinListener);
        if (databaseExecutor != null) databaseExecutor.close();
        updates.close();
        metrics.shutdown();
        printBanner("DISABLED");
    }

    static String repository(String pluginName, String website) {
        String configured = website == null ? "" : website.trim();
        Matcher matcher = GITHUB_REPOSITORY.matcher(configured);
        if (matcher.matches()) {
            String repository = matcher.group(2);
            if (repository.toLowerCase(Locale.ROOT).endsWith(".git")) {
                repository = repository.substring(0, repository.length() - 4);
            }
            return matcher.group(1) + "/" + repository;
        }
        String name = Objects.requireNonNull(pluginName, "pluginName").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Plugin name cannot be blank");
        return "Dy6HiLa/" + name;
    }

    static String notificationPermission(String pluginName, List<String> declaredPermissions) {
        String normalizedName = Objects.requireNonNull(pluginName, "pluginName")
                .toLowerCase(Locale.ROOT);
        List<String> permissions = declaredPermissions == null ? List.of() : declaredPermissions;
        String conventional = normalizedName + ".admin";
        if (permissions.stream().anyMatch(conventional::equalsIgnoreCase)) return conventional;
        return permissions.stream()
                .filter(Objects::nonNull)
                .filter(permission -> permission.toLowerCase(Locale.ROOT).endsWith(".admin"))
                .findFirst()
                .orElseGet(() -> permissions.stream()
                        .filter(Objects::nonNull)
                        .filter(permission -> permission.toLowerCase(Locale.ROOT).endsWith(".update"))
                        .findFirst()
                        .orElse(conventional));
    }

    private static int bStatsId(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        try (InputStream stream = plugin.getResource("plugin.yml")) {
            if (stream == null) throw new IllegalStateException("plugin.yml is unavailable");
            YamlConfiguration metadata = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            int id = metadata.getInt("bstats-id", 0);
            if (id <= 0) throw new IllegalStateException("plugin.yml must contain a positive bstats-id");
            return id;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read bstats-id from plugin.yml", exception);
        }
    }

    private void printBanner(String state) {
        String[] lines = {
                " ________  ________  ___  ___      ___ ________  _________  _______   ________   ___  ___  ___       ___",
                "|@   __  @|@   __  @|@  @|@  @    /  /|@   __  @|@___   ___@@  ___ @ |@   ___  @|@  @|@  @|@  @     |@  @",
                "@ @  @|@  @ @  @|@  @ @  @ @  @  /  / | @  @|@  @|___ @  @_@ @   __/|@ @  @@ @  @ @  @@@  @ @  @    @ @  @",
                " @ @   ____@ @   _  _@ @  @ @  @/  / / @ @   __  @   @ @  @ @ @  @_|/_@ @  @@ @  @ @  @@@  @ @  @    @ @  @",
                "  @ @  @___|@ @  @@  @@ @  @ @    / /   @ @  @ @  @   @ @  @ @ @  @_|@ @ @  @@ @  @ @  @@@  @ @  @____@ @  @____",
                "   @ @__@    @ @__@@ _@@ @__@ @__/ /     @ @__@ @__@   @ @__@ @ @_______@ @__@@ @__@ @_______@ @_______@ @_______@",
                "    @|__|     @|__|@|__|@|__|@|__|/       @|__|@|__|    @|__|  @|_______|@|__| @|__|@|_______|@|_______|@|_______|"
        };
        plugin.getLogger().info(" ");
        for (String line : lines) plugin.getLogger().info(line.replace('@', '\\'));
        plugin.getLogger().info(" ");
        plugin.getLogger().info(plugin.getDescription().getName() + " v"
                + plugin.getDescription().getVersion() + " | " + state);
        plugin.getLogger().info("Support: " + supportUrl());
        plugin.getLogger().info(" ");
    }
}
