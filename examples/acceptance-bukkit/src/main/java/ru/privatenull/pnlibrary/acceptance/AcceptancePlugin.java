package ru.privatenull.pnlibrary.acceptance;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer;
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel;
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination;
import ru.privatenull.pnlibrary.api.events.EventSubscription;
import ru.privatenull.pnlibrary.api.plugin.DenyAction;
import ru.privatenull.pnlibrary.api.plugin.DownloadPolicy;
import ru.privatenull.pnlibrary.api.plugin.ModuleContext;
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration;
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy;
import ru.privatenull.pnlibrary.api.runtime.PnLibrary;
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider;
import ru.privatenull.pnlibrary.api.tasks.TaskSpec;
import ru.privatenull.pnlibrary.api.tasks.TaskQuery;
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor;
import ru.privatenull.pnlibrary.api.updates.UpdateChannel;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class AcceptancePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final AtomicLong events = new AtomicLong();
    private final AtomicLong taskRuns = new AtomicLong();
    private PluginRegistration pluginRegistration;
    private ModuleContext context;
    private EventSubscription eventSubscription;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PnLibrary library = PnLibraryProvider.getOrNull();
        if (library == null) {
            getLogger().severe("pnLibrary is unavailable; acceptance plugin is being disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pluginRegistration = library.getPlugins().register(this);
        context = pluginRegistration.registerModule("acceptance", builder -> {
            builder.product(ProductDescriptor.builder("acceptance", getDescription().getVersion())
                .pnLibraryApi(1, 1).build());
            builder.metrics(getConfig().getInt("metrics.project-id", 32592),
                getConfig().getBoolean("metrics.enabled", false), metrics -> {
                    metrics.simplePie("server_implementation", () -> getServer().getName());
                    metrics.singleLineChart("acceptance_events", () -> (int) events.get());
                });
            builder.diagnostics(getDataFolder().toPath(), DiagnosticContainer.builder("acceptance")
                .snapshot(this::diagnosticSnapshot).configuration("config.yml").build());

            if (getConfig().getBoolean("updates.enabled", false)) {
                builder.updates(getConfig().getString("updates.repository-owner"),
                    getConfig().getString("updates.repository-name"), update -> update
                        .channel(UpdateChannel.DEV).automaticDownload(false)
                        .artifact(getConfig().getString("updates.artifact-pattern"), 8));
            }
            if (getConfig().getBoolean("dependency.enabled", false)) {
                builder.dependencies(dependencies -> dependencies.plugin(
                    getConfig().getString("dependency.name"),
                    getConfig().getString("dependency.minimum-version"), dependency -> dependency
                        .url(getConfig().getString("dependency.url"))
                        .automaticDownload(getConfig().getBoolean("dependency.automatic-download", false))));
            }
            if (getConfig().getBoolean("downloads.enabled", false)) {
                builder.downloads(getDataFolder().toPath(), downloads -> downloads.file("acceptance-file", file -> file
                    .url(getConfig().getString("downloads.url"))
                    .destination(DownloadDestination.DATA_FOLDER, "downloads/acceptance.txt")
                    .automaticDownload(false).required(false)));
            }
            if (getConfig().getBoolean("remote-policy.enabled", false)) {
                builder.remotePolicy(RemotePolicy.builder()
                    .source(getConfig().getString("remote-policy.source"))
                    .checkEvery(Duration.ofHours(6))
                    .onDeny(DenyAction.valueOf(getConfig()
                        .getString("remote-policy.on-deny", "DISABLE_PLUGIN")
                        .trim().toUpperCase(java.util.Locale.ROOT)))
                    .build());
            }
        });

        context.getServices().register(AcceptancePlugin.class, this, 100);
        eventSubscription = context.getEvents().subscribe(AcceptanceEvent.class,
            event -> events.incrementAndGet());
        context.getTasks().schedule(TaskSpec.builder().name("acceptance-heartbeat").key("acceptance-heartbeat")
            .interval(Duration.ofSeconds(30)).tag("acceptance")
            .action(task -> taskRuns.incrementAndGet()).build());

        getCommand("pnaccept").setExecutor(this);
        getCommand("pnaccept").setTabCompleter(this);
        context.getLifecycle().enabled()
            .ok("Runtime", library.getVersion())
            .ok("Module", context.getId().getValue())
            .ok("Command", "/pnaccept status")
            .show();
    }

    @Override
    public void onDisable() {
        if (eventSubscription != null) eventSubscription.close();
        if (pluginRegistration != null) pluginRegistration.close();
        eventSubscription = null;
        pluginRegistration = null;
        context = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (context == null) return true;
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "status": showStatus(sender); break;
            case "event":
                new AcceptanceEvent(events.get() + 1).callEvent();
                sender.sendMessage("§aEvent bus: OK §7(count=" + events.get() + ")");
                break;
            case "task":
                context.getTasks().schedule(TaskSpec.builder().name("acceptance-manual")
                    .action(task -> taskRuns.incrementAndGet()).build());
                sender.sendMessage("§aTask scheduled. §7Check /pnaccept status in a moment.");
                break;
            case "cooldown":
                UUID cooldownOwner = sender instanceof org.bukkit.entity.Player
                    ? ((org.bukkit.entity.Player) sender).getUniqueId()
                    : UUID.nameUUIDFromBytes(("console:" + sender.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                boolean allowed = context.getCooldowns().acquire(cooldownOwner, "manual", Duration.ofSeconds(5)).getAllowed();
                sender.sendMessage(allowed ? "§aCooldown: first acquisition allowed" : "§eCooldown: active (expected on repeated call)");
                break;
            case "metrics":
                if (context.getMetrics().isEnabled()) context.getMetrics().disable(); else context.getMetrics().enable();
                sender.sendMessage("§aMetrics enabled: §f" + context.getMetrics().isEnabled());
                break;
            case "update":
                if (context.getUpdates() == null) sender.sendMessage("§eEnable updates.enabled in config.yml and restart.");
                else { context.getUpdates().checkNow(); sender.sendMessage("§aUpdate check requested."); }
                break;
            case "download":
                if (context.getDownloads() == null) sender.sendMessage("§eEnable downloads.enabled in config.yml and restart.");
                else context.getDownloads().downloadNow().thenAccept(result ->
                    sender.sendMessage("§aDownload finished: §f" + result));
                break;
            case "diagnostics":
                PnLibraryProvider.get().getDiagnostics().record("acceptance", DiagnosticLevel.INFO,
                    "command", "MANUAL_TEST", "Manual acceptance diagnostic event", null, Collections.emptyMap());
                sender.sendMessage("§aDiagnostic event recorded. §7Run the pnLibrary debug command for acceptance.");
                break;
            default: sender.sendMessage("§7/pnaccept status|event|task|cooldown|metrics|update|download|diagnostics");
        }
        return true;
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6━━━━━━━━ pnLibrary acceptance ━━━━━━━━");
        sender.sendMessage("§7Module: §f" + context.getId().getValue() + " §8| §7closed: §f" + context.isClosed());
        sender.sendMessage("§7Events: §f" + events.get() + " §8| §7task runs: §f" + taskRuns.get());
        sender.sendMessage("§7Tasks visible: §f" + context.getTasks().query(TaskQuery.all()).size());
        sender.sendMessage("§7Service registry: §f" + (context.getServices().get(AcceptancePlugin.class) == this));
        sender.sendMessage("§7Metrics: §f" + context.getMetrics().isEnabled() + " §8| §7diagnostics: §f" + (context.getDiagnostics() != null));
        sender.sendMessage("§7Updates: §f" + (context.getUpdates() == null ? "disabled" : context.getUpdates().getSnapshot().getState()));
        sender.sendMessage("§7Downloads: §f" + (context.getDownloads() == null ? "disabled" : context.getDownloads().snapshots()));
    }

    private Map<String, Object> diagnosticSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("events", events.get());
        snapshot.put("taskRuns", taskRuns.get());
        snapshot.put("metricsEnabled", context != null && context.getMetrics().isEnabled());
        snapshot.put("server", getServer().getName());
        snapshot.put("serverVersion", getServer().getVersion());
        return snapshot;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> values = Arrays.asList("status", "event", "task", "cooldown", "metrics", "update", "download", "diagnostics");
        java.util.ArrayList<String> matches = new java.util.ArrayList<>();
        for (String value : values) if (value.startsWith(prefix)) matches.add(value);
        return matches;
    }
}
