package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.bukkit.currency.CurrencyCommandExecutor;
import org.pnfolder.pnlibrary.bukkit.menu.PluginsMenu;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.commands.configs.ConfigsCommand;
import org.pnfolder.pnlibrary.core.commands.deps.DepsCommand;
import org.pnfolder.pnlibrary.core.commands.events.EventBusCommand;
import org.pnfolder.pnlibrary.core.commands.modules.ModulesCommand;
import org.pnfolder.pnlibrary.core.commands.proxy.ServersCommand;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskCommand;
import org.pnfolder.pnlibrary.core.lifecycle.MountableCommands;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes;

/**
 * The one Bukkit command of pnLibrary, {@code /pn}, expressed through the shared command builder.
 *
 * <p>Everything an administrator or a player types hangs under it:
 * <ul>
 *   <li>{@code /pn}, {@code status}, {@code check}, {@code updates ...}: the plugins, their state, the update
 *       catalog ({@code pnlibrary.admin});</li>
 *   <li>{@code /pn gui}: the inventory menu, players only ({@code pnlibrary.admin});</li>
 *   <li>{@code /pn update <plugin>}, {@code update confirm <token>}, {@code update rollback}, {@code update restart}:
 *       staging, confirming, rolling back and restarting ({@code pnlibrary.admin});</li>
 *   <li>{@code /pn events}, {@code bus}, {@code tasks}, {@code servers}, {@code debug}, {@code money},
 *       {@code support}: the shared commands, each with its own permission.</li>
 * </ul>
 *
 * <p>The root itself has no permission, so a moderator who may only read events can still enter {@code /pn events}.
 * The update flow lives in {@link BukkitUpdateFlow}, support helpers in {@link BukkitSupportCommands}, and reply
 * rendering in {@link BukkitCommandReplies}. The native sender is used only for permissions, identity and
 * player-bound confirmations.
 */
public final class BukkitControlCommand implements AutoCloseable {
    /** Catalog routes of the {@code /pn} tree, shared with the join notice. */
    public static final UpdateCatalogRoutes ROUTES = new UpdateCatalogRoutes("/pn updates",
        product -> "/pn update " + product, "/pn check", true, false, true);

    /** The permission of the control tree: updates, support helpers and the status pages. */
    static final String ADMIN = "pnlibrary.admin";
    static final String VIEW = "pnlibrary.updates.view";
    static final String DOWNLOAD = "pnlibrary.updates.download";

    private final PnLibrary library;
    private final CurrencyProviderRegistry currencies;
    private final Link link;
    private final ServerScheduler scheduler;
    private final BukkitCommandReplies replies;
    private final UpdateCatalogCommands catalog;
    private final BukkitUpdateFlow updates;
    private final BukkitSupportCommands support;
    private final PluginsMenu menu;

    public BukkitControlCommand(Plugin plugin, PnLibrary library, CurrencyProviderRegistry currencies) {
        this(plugin, library, currencies, Link.OFF, new ServerScheduler(plugin));
    }

    public BukkitControlCommand(Plugin plugin, PnLibrary library, CurrencyProviderRegistry currencies, Link link,
                                ServerScheduler scheduler) {
        this.library = library;
        this.currencies = currencies;
        this.link = link;
        this.scheduler = scheduler;
        BukkitSettingFeedback feedback = new BukkitSettingFeedback(scheduler);
        this.replies = new BukkitCommandReplies(library, scheduler);
        this.catalog = new UpdateCatalogCommands(library, ROUTES, VIEW, "pnlibrary.updates.rollback",
            "pnlibrary.updates.settings", context -> true,
            task -> {
                if (!library.isClosed()) {
                    scheduler.nextTick(task);
                }
            },
            (context, saved) -> {
                CommandSender sender = nativeOf(context);
                if (sender instanceof Player) {
                    feedback.show((Player) sender, saved);
                }
            });
        this.updates = new BukkitUpdateFlow(plugin, library, catalog, replies, scheduler);
        this.support = new BukkitSupportCommands(plugin, library, replies);
        this.menu = new PluginsMenu(plugin, library, replies, link, scheduler);
        // `/pn settings pick` is a shared command; the menu it opens exists only here.
        SettingPickerHook picker = RuntimeBridge.settingPicker(library);
        if (picker != null) {
            picker.setOpener((context, key, filter) -> {
                CommandSender sender = nativeOf(context);
                return sender != null && menu.pickFor(sender, key, filter);
            });
        }
    }

    /** The command a confirmation card offers to carry out what {@code token} was issued for. */
    public static String confirmCommand(String token) {
        return "/pn update confirm " + token;
    }

    @Override
    public void close() {
        SettingPickerHook picker = RuntimeBridge.settingPicker(library);
        if (picker != null) {
            picker.setOpener(null);
        }
        menu.close();
    }

    public CommandDefinition definition() {
        return CommandDefinition.command("pn", root -> {
            // Other labels for the same command: they keep it reachable when another plugin already owns `/pn`.
            root.aliases("pnlib", "pnlibrary");
            root.executes(this::overview);
            statusViews(root);
            updateViews(root);
            mountShared(root);
            root.literal("support", node -> {
                node.permission(ADMIN, VIEW);
                node.executes(context -> replies.nativeSender(context, (sender, reply) -> support.support(reply)));
            });
        });
    }

    /** The commands every platform shares, mounted under {@code /pn}. */
    private void mountShared(CommandBuilder root) {
        for (String name : MountableCommands.ALL) {
            CommandDefinition mounted = RuntimeBridge.mountableCommand(library, name);
            if (mounted != null) {
                root.mount(name, mounted);
            }
        }
        root.mount("bus", new EventBusCommand(library).definition());
        root.mount("tasks", new TaskCommand(library).definition());
        root.mount("modules", new ModulesCommand(library).definition());
        root.mount("configs", new ConfigsCommand(library).definition());
        root.mount("deps", new DepsCommand(library).definition());
        root.mount("servers", new ServersCommand(link, ServersCommand.fromNetworkMap(link)).definition());
        root.mount("money", new CurrencyCommandExecutor(currencies, scheduler).definition());
    }

    /** Status, check, the menu and the update catalog. */
    private void statusViews(CommandBuilder root) {
        root.literal("status", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(context -> replies.nativeSender(context, (sender, reply) -> updates.sendStatus(sender, reply, null)));
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> productNames());
                plugin.executes(context -> replies.nativeSender(context, (sender, reply) ->
                    updates.sendStatus(sender, reply, context.<String>get("plugin"))));
            });
        });
        root.literal("check", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(updates::checkUpdates);
        });
        root.literal("gui", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(context -> replies.nativeSender(context, (sender, reply) -> menu.openFor(sender, reply)));
        });
        root.literal("updates", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(catalog::showList);
            catalog.install((name, configure) -> node.literal(name, configure));
            node.literal("rollback", this::rollbackNode);
        });
    }

    private void rollbackNode(CommandNodeBuilder rollback) {
        rollback.permission("pnlibrary.updates.rollback");
        rollback.argument("transaction", ArgumentType.string(), transaction -> {
            transaction.executes(context -> updates.rollbackSaved(context, false));
            transaction.literal("configs", configs -> configs.executes(context -> updates.rollbackSaved(context, true)));
        });
    }

    /** Staging, confirming, rolling back and restarting an update. */
    private void updateViews(CommandBuilder root) {
        // Words after `update` are subcommands first and a plugin name second, so no plugin may be named like one.
        root.literal("update", node -> {
            node.permission(ADMIN, DOWNLOAD);
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> productNames());
                plugin.executes(context -> replies.nativeSender(context, (sender, reply) ->
                    updates.update(sender, reply, context.<String>get("plugin"))));
            });
            node.literal("confirm", confirm -> confirm.argument("token", ArgumentType.string(), token ->
                token.executes(context -> replies.nativeSender(context, (sender, reply) ->
                    updates.confirm(sender, reply, context.<String>get("token"))))));
            node.literal("rollback", rollback ->
                rollback.executes(context -> replies.nativeSender(context, (sender, reply) ->
                    updates.rollbackUpdate(sender, reply))));
            node.literal("restart", restartNode -> {
                restartNode.executes(context -> replies.nativeSender(context, (sender, reply) ->
                    updates.restartUpdate(sender, reply)));
                restartNode.literal("cancel", cancel -> cancel.executes(context ->
                    replies.nativeSender(context, (sender, reply) -> updates.cancelRestart(reply))));
            });
        });
    }

    /** {@code /pn} alone: the list of plugins for an administrator, otherwise the commands the sender may use. */
    private void overview(CommandContext context) {
        org.pnfolder.pnlibrary.api.commands.CommandSender sender = context.getSender();
        if (AudienceExtensions.hasPermission(sender, ADMIN)) {
            catalog.showList(context);
            return;
        }
        List<String> available = new ArrayList<>();
        addIfAllowed(available, sender, "pnlibrary.events.view", "/pn events");
        addIfAllowed(available, sender, "pnlibrary.events.bus", "/pn bus");
        addIfAllowed(available, sender, "pnlibrary.tasks", "/pn tasks");
        addIfAllowed(available, sender, "pnlibrary.stats", "/pn stats");
        addIfAllowed(available, sender, "pnlibrary.placeholders", "/pn placeholders");
        addIfAllowed(available, sender, "pnlibrary.actions", "/pn actions");
        addIfAllowed(available, sender, "pnlibrary.settings", "/pn settings");
        addIfAllowed(available, sender, ServersCommand.PERMISSION, "/pn servers");
        addIfAllowed(available, sender, "pnlibrary.debug", "/pn debug");
        available.add("/pn money");
        replies.say(sender, ChatTone.INFO, "Команды pnLibrary, доступные вам:", String.join("  ", available));
    }

    private static void addIfAllowed(List<String> available, org.pnfolder.pnlibrary.api.commands.CommandSender sender,
                                     String permission, String command) {
        if (AudienceExtensions.hasPermission(sender, permission)) {
            available.add(command);
        }
    }

    private List<String> productNames() {
        List<String> names = new ArrayList<>();
        for (UpdateRegistration registration : library.getUpdates().getProducts().all()) {
            names.add(registration.getSnapshot().getProduct());
        }
        return names;
    }

    private static CommandSender nativeOf(CommandContext context) {
        return context.getSender() instanceof BukkitCommandSender
            ? ((BukkitCommandSender) context.getSender()).getNative() : null;
    }
}
