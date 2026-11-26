package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/**
 * The catalog commands {@code list}, {@code available}, {@code info}, {@code settings}, {@code set}, {@code history}
 * and {@code backups}, shared by the Bukkit {@code /pn updates} tree and the console-only {@code /pn update} command
 * of the proxies.
 *
 * <p>Platforms own what differs: the permissions, who may use the command (the guard) and which thread results are
 * delivered on. The commands that prepare, confirm or restore updates stay with the platform because their
 * confirmation flows differ. Output adapts to the sender: a sender that cannot click gets text without buttons and a
 * list of commands, and the console gets ASCII only.
 */
public final class UpdateCatalogCommands {
    private final PnLibrary library;
    private final UpdateCatalogRoutes routes;
    private final String viewPermission;
    private final String backupsPermission;
    private final String managePermission;
    private final Predicate<CommandContext> guard;
    private final UpdateCatalogSettingsCommands settings;
    private final UpdateCatalogBackupCommands backups;
    private final UpdateCatalogHistoryCommands history;
    private final Consumer<Runnable> deliver;

    /** A setting that was changed from a player or console command. */
    public static final class SettingSaved {
        private final String product;
        private final String setting;
        private final String from;
        private final String to;

        /**
         * @param product product whose setting changed
         * @param setting name of the changed setting
         * @param from value before the change
         * @param to value after the change
         */
        public SettingSaved(@NotNull String product, @NotNull String setting, @NotNull String from, @NotNull String to) {
            this.product = product;
            this.setting = setting;
            this.from = from;
            this.to = to;
        }

        public @NotNull String getProduct() {
            return product;
        }

        public @NotNull String getSetting() {
            return setting;
        }

        public @NotNull String getFrom() {
            return from;
        }

        public @NotNull String getTo() {
            return to;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SettingSaved)) {
                return false;
            }
            SettingSaved that = (SettingSaved) other;
            return product.equals(that.product) && setting.equals(that.setting) && from.equals(that.from) && to.equals(that.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, setting, from, to);
        }
    }

    /**
     * @param viewPermission permission required for list, available, info, settings and history, or null for none
     * @param backupsPermission permission required for backups, which expose rollback identities
     * @param managePermission permission required for {@code set}, which changes a component's channel, automatic download or pause
     * @param guard returns whether the command may run for this sender; it reports the reason itself
     * @param deliver runs result delivery on the right thread; asynchronous results are never sent directly
     * @param onSaved called on that thread after a setting was saved, so a platform can add its own feedback
     */
    public UpdateCatalogCommands(@NotNull PnLibrary library, @NotNull UpdateCatalogRoutes routes, @Nullable String viewPermission,
                                 @Nullable String backupsPermission, @Nullable String managePermission,
                                 @NotNull Predicate<CommandContext> guard, @NotNull Consumer<Runnable> deliver,
                                 @NotNull BiConsumer<CommandContext, SettingSaved> onSaved) {
        this.library = library;
        this.routes = routes;
        this.viewPermission = viewPermission;
        this.backupsPermission = backupsPermission;
        this.managePermission = managePermission;
        this.guard = guard;
        this.deliver = deliver;
        this.settings = new UpdateCatalogSettingsCommands(library, this::ready, this::routesFor, deliver, onSaved);
        this.backups = new UpdateCatalogBackupCommands(library, this::ready, this::routesFor, this::requestedPage, deliver);
        this.history = new UpdateCatalogHistoryCommands(library, viewPermission, this::ready, this::routesFor, this::requestedPage, deliver);
    }

    public UpdateCatalogCommands(@NotNull PnLibrary library, @NotNull UpdateCatalogRoutes routes, @Nullable String viewPermission,
                                 @Nullable String backupsPermission, @Nullable String managePermission) {
        this(library, routes, viewPermission, backupsPermission, managePermission, context -> true, Runnable::run,
            (context, saved) -> { });
    }

    public UpdateCatalogCommands(@NotNull PnLibrary library, @NotNull UpdateCatalogRoutes routes, @Nullable String viewPermission) {
        this(library, routes, viewPermission, viewPermission, viewPermission);
    }

    public UpdateCatalogCommands(@NotNull PnLibrary library, @NotNull UpdateCatalogRoutes routes) {
        this(library, routes, null);
    }

    private static void permission(CommandNodeBuilder node, String permission) {
        if (permission != null) {
            node.permission(permission);
        }
    }

    /**
     * Adds the catalog literals through {@code addLiteral}, which forwards to whichever command or node is being built.
     *
     * <pre>{@code
     * catalog.install((name, configure) -> root.literal(name, configure));
     * }</pre>
     */
    public void install(@NotNull BiConsumer<String, Consumer<CommandNodeBuilder>> addLiteral) {
        addLiteral.accept("list", node -> pagedList(node, false));
        addLiteral.accept("available", node -> pagedList(node, true));
        addLiteral.accept("info", node -> {
            permission(node, viewPermission);
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> UpdateCatalogSupport.productNames(library));
                plugin.executes(this::showInfo);
            });
        });
        addLiteral.accept("settings", node -> {
            permission(node, viewPermission);
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> UpdateCatalogSupport.productNames(library));
                plugin.executes(context -> showSettings(context, context.<String>get("plugin")));
            });
        });
        addLiteral.accept("set", this::installSettings);
        addLiteral.accept("history", this.history::install);
        backups.install(addLiteral, backupsPermission != null ? backupsPermission : viewPermission, managePermission);
    }

    private void pagedList(CommandNodeBuilder node, boolean updatesOnly) {
        permission(node, viewPermission);
        node.executes(context -> showCatalog(context, updatesOnly));
        node.argument("page", ArgumentType.integer(), page -> page.executes(context -> showCatalog(context, updatesOnly)));
    }

    private void installSettings(CommandNodeBuilder set) {
        permission(set, managePermission);
        set.literal("all", all -> all.literal("auto", auto -> auto.argument("value", ArgumentType.string(), value -> {
            value.suggests(context -> UpdateCatalogSettingsCommands.SWITCH_SUGGESTIONS);
            value.executes(settings::setAllAutomatic);
        })));
        set.argument("plugin", ArgumentType.string(), plugin -> {
            plugin.suggests(context -> UpdateCatalogSupport.productNames(library));
            plugin.literal("channel", node -> node.argument("value", ArgumentType.string(), value -> {
                value.suggests(context -> channelNames());
                value.executes(settings::setChannel);
            }));
            plugin.literal("java", node -> node.argument("value", ArgumentType.string(), value -> {
                value.suggests(context -> java.util.Arrays.asList("8", "11", "17", "21", UpdateCatalogSettingsCommands.DEFAULT_CHANNEL));
                value.executes(settings::setJava);
            }));
            plugin.literal("auto", node -> node.argument("value", ArgumentType.string(), value -> {
                value.suggests(context -> UpdateCatalogSettingsCommands.SWITCH_SUGGESTIONS);
                value.executes(settings::setAutomatic);
            }));
            plugin.literal("hold", node -> node.argument("value", ArgumentType.string(), value -> {
                value.suggests(context -> {
                    List<String> periods = new ArrayList<>(UpdateCatalogView.HOLD_PERIODS);
                    periods.add("off");
                    return periods;
                });
                value.executes(settings::setHeld);
            }));
        });
    }

    private List<String> channelNames() {
        List<String> names = new ArrayList<>();
        for (org.pnfolder.pnlibrary.api.updates.product.UpdateChannel channel : settings.available()) {
            names.add(channel.name().toLowerCase(java.util.Locale.ROOT));
        }
        names.add(UpdateCatalogSettingsCommands.DEFAULT_CHANNEL);
        return names;
    }

    /** Shows the first page of every registered component; used as the default action of a command. */
    public void showList(@NotNull CommandContext context) {
        showCatalog(context, false);
    }

    /** Shows the first page of components with an available or prepared update. */
    public void showAvailable(@NotNull CommandContext context) {
        showCatalog(context, true);
    }

    /** Routes for this sender: no buttons when it cannot click, ASCII for the console, read-only without permission. */
    private UpdateCatalogRoutes routesFor(CommandContext context) {
        return routes.forReader(routes.getInteractive() && context.getSender().getSupportsInteraction(),
            AudienceExtensions.isConsole(context.getSender()),
            managePermission == null || AudienceExtensions.hasPermission(context.getSender(), managePermission));
    }

    private void showCatalog(CommandContext context, boolean updatesOnly) {
        if (!ready(context)) {
            return;
        }
        Integer page = requestedPage(context);
        if (page == null) {
            return;
        }
        send(context, UpdateCatalogView.catalog(snapshots(), page, updatesOnly, routesFor(context)));
    }

    private List<UpdateSnapshot> snapshots() {
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (UpdateRegistration product : library.getUpdates().getProducts().all()) {
            snapshots.add(product.getSnapshot());
        }
        return snapshots;
    }

    private static void send(CommandContext context, List<Component> lines) {
        lines.forEach(context.getSender()::sendMessage);
    }
    private void showInfo(CommandContext context) {
        if (!ready(context)) {
            return;
        }
        UpdateSnapshot snapshot = snapshotOf(context, context.get("plugin"));
        if (snapshot != null) {
            send(context, UpdateCatalogView.info(snapshot, routesFor(context), library.getUpdates().getSettings().heldUntil(snapshot.getProduct())));
        }
    }

    private void showSettings(CommandContext context, String requested) {
        if (!ready(context)) {
            return;
        }
        UpdateSnapshot snapshot = snapshotOf(context, requested);
        if (snapshot != null) {
            send(context, UpdateCatalogView.settings(snapshot, routesFor(context),
                library.getUpdates().getSettings().heldUntil(snapshot.getProduct())));
        }
    }

    private UpdateSnapshot snapshotOf(CommandContext context, String requested) {
        UpdateRegistration registration = library.getUpdates().getProducts().get(requested);
        if (registration == null) {
            context.getSender().sendMessage(CatalogErrors.line("Плагин " + requested + " не зарегистрирован в pnLibrary."));
            return null;
        }
        return registration.getSnapshot();
    }

    private Integer requestedPage(CommandContext context) {
        int page = context.contains("page") ? context.<Integer>get("page") : 1;
        if (page >= 1) {
            return page;
        }
        context.getSender().sendMessage(CatalogErrors.line("Номер страницы должен быть положительным."));
        return null;
    }

    private boolean ready(CommandContext context) {
        if (!guard.test(context)) {
            return false;
        }
        if (!library.isClosed()) {
            return true;
        }
        context.getSender().sendMessage(CatalogErrors.line("pnLibrary не готова. Дождитесь полного запуска сервера."));
        return false;
    }
}
