package org.pnfolder.pnlibrary.core.commands.proxy;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.pnfolder.pnlibrary.common.lang.Pair;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.network.ChannelNames;
import org.pnfolder.pnlibrary.core.commands.configs.ConfigsCommand;
import org.pnfolder.pnlibrary.core.commands.deps.DepsCommand;
import org.pnfolder.pnlibrary.core.commands.events.EventBusCommand;
import org.pnfolder.pnlibrary.core.commands.groups.GroupsCommand;
import org.pnfolder.pnlibrary.core.commands.modules.ModulesCommand;
import org.pnfolder.pnlibrary.core.commands.tasks.TaskCommand;
import org.pnfolder.pnlibrary.core.lifecycle.MountableCommands;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.ServerGroups;
import org.pnfolder.pnlibrary.core.link.report.LinkReports;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.updates.catalog.proxy.ProxyUpdateCommand;

/**
 * The one command of pnLibrary on BungeeCord and Velocity, {@code /pn}, the same as the Bukkit command of the same name.
 *
 * <pre>
 * /pn, status, check, updates ...      the list of plugins, their state, the update catalog   pnlibrary.admin
 * /pn update &lt;plugin&gt;, update rollback, update confirm &lt;token&gt;   stages an update, rolls back, confirms
 * /pn events ...                       the journal of events                                  pnlibrary.events.view
 * /pn bus ...                          listeners, calls and traces of the event bus           pnlibrary.events.bus
 * /pn tasks ...                        scheduled tasks of every plugin                        pnlibrary.tasks
 * /pn servers ...                      the game servers that report to this proxy             pnlibrary.servers
 * /pn groups ...                       groups of servers and the plugins they should have     pnlibrary.groups
 * /pn debug ...                        the encrypted report for support                       pnlibrary.debug
 * /pn support                          the support link                                       pnlibrary.admin
 * </pre>
 *
 * It works for the console and for players, and the output is clickable wherever the sender can click.
 * The proxy answers to {@code /pnproxy} and {@code /pnp}, not {@code /pn}, so it never hides the {@code /pn} of a game
 * server; the text and buttons are written with {@code /pn} and the platform adapters show them as {@code /pnproxy}
 * (see {@link ProxyCommandNames}).
 */
public final class ProxyControlCommand {
    private static final long RUN_TIMEOUT_SECONDS = 10L;

    /** What a sender without the admin permission is told about, with the permission each command needs. */
    private static final List<Pair<String, String>> OTHERS = Arrays.asList(
            new Pair<>("/pn events", "pnlibrary.events.view"),
            new Pair<>("/pn bus", "pnlibrary.events.bus"),
            new Pair<>("/pn tasks", "pnlibrary.tasks"),
            new Pair<>("/pn stats", "pnlibrary.stats"),
            new Pair<>("/pn placeholders", "pnlibrary.placeholders"),
            new Pair<>("/pn actions", "pnlibrary.actions"),
            new Pair<>("/pn settings", "pnlibrary.settings"),
            new Pair<>("/pn servers", ServersCommand.PERMISSION),
            new Pair<>("/pn notices", NoticesCommand.PERMISSION),
            new Pair<>("/pn channels", ChannelsCommand.PERMISSION),
            new Pair<>("/pn groups", GroupsCommand.PERMISSION),
            new Pair<>("/pn debug", "pnlibrary.debug"));

    private final PnLibrary library;
    private final Link link;
    private CommandDefinition built;

    public ProxyControlCommand(@NotNull PnLibrary library, @NotNull Link link) {
        this.library = Objects.requireNonNull(library, "library");
        this.link = Objects.requireNonNull(link, "link");
    }

    @NotNull
    public synchronized CommandDefinition definition() {
        if (built == null) {
            built = build();
        }
        return built;
    }

    @NotNull
    public CommandRegistration register() {
        return RuntimeBridge.registerRuntimeCommand(library, definition());
    }

    /**
     * Runs {@code line}, a command such as {@code pn update rollback}, for the viewer and returns what it printed. The menu
     * of another server uses it for the buttons that run a chat command.
     */
    @NotNull
    public List<Component> run(@NotNull UUID viewerId, @NotNull String viewerName, @NotNull Predicate<String> holds,
            @NotNull String line) throws InterruptedException, ExecutionException, TimeoutException {
        List<String> words = new ArrayList<>();
        for (String word : line.trim().split(" ")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        if (words.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Component> printed = Collections.synchronizedList(new ArrayList<>());
        final Pointers pointers = AudienceExtensions.senderPointers(viewerName, viewerId, holds);
        CommandSender sender = new CommandSender() {
            @NotNull
            @Override
            public Pointers pointers() {
                return pointers;
            }

            @Override
            public void sendMessage(@NotNull Component message) {
                printed.add(message);
            }
        };
        CommandContext context = new CommandContext(sender, words.subList(1, words.size()), ProxyCommandNames.NAME,
                words.get(words.size() - 1));
        CompletionStage<Void> stage = RuntimeBridge.dispatchRuntimeCommand(library, definition(), context);
        if (stage == null) {
            return new ChatKit(ChatReader.PLAYER).notice(new ChatNotice(ChatTone.FAILURE, "Команды прокси сейчас недоступны."));
        }
        stage.toCompletableFuture().get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new ArrayList<>(printed);
    }

    private CommandDefinition build() {
        final org.pnfolder.pnlibrary.core.updates.catalog.proxy.ProxyUpdateCommand updates = new ProxyUpdateCommand(library);
        return CommandDefinition.command(ProxyCommandNames.NAME, root -> {
            root.aliases(ProxyCommandNames.ALIASES.toArray(new String[0]));
            root.executes(context -> updates.overview(context, OTHERS));
            updates.install(root);
            mountShared(root);
            root.mount("bus", new EventBusCommand(library).definition());
            root.mount("tasks", new TaskCommand(library).definition());
            root.mount("modules", new ModulesCommand(library).definition());
            root.mount("configs", new ConfigsCommand(library).definition());
            root.mount("deps", new DepsCommand(library).definition());
            root.mount("servers", new ServersCommand(link, ServersCommand.fromReports(link), library.getProxy()).definition());
            root.mount("notices", new NoticesCommand(NoticesCommand.fromReports(library, link)).definition());
            root.mount("channels", new ChannelsCommand(() -> ChannelNames.of(library.getProxy())).definition());
            root.mount("groups", new GroupsCommand(link, withProxy()).definition());
        });
    }

    private void mountShared(CommandBuilder root) {
        for (String name : MountableCommands.ALL) {
            CommandDefinition mounted = RuntimeBridge.mountableCommand(library, name);
            if (mounted != null) {
                root.mount(name, mounted);
            }
        }
    }

    private Supplier<List<NetworkServer>> withProxy() {
        final Supplier<List<NetworkServer>> servers = ServersCommand.fromReports(link);
        return () -> {
            List<NetworkServer> all = new ArrayList<>();
            all.add(new NetworkServer(ServerGroups.PROXY, true, LinkReports.build(library, "proxy", 0)));
            all.addAll(servers.get());
            return all;
        };
    }
}
