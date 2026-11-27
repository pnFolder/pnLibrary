package org.pnfolder.pnlibrary.core.updates.catalog.backup;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogSupport;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogErrors;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;

/** The {@code backups} commands of the catalog: the list with its filters, and {@code restore}, {@code files} and {@code delete} of one saved set. */
public final class UpdateCatalogBackupCommands {
    private final PnLibrary library;
    private final Predicate<CommandContext> ready;
    private final Function<CommandContext, UpdateCatalogRoutes> routesFor;
    private final Function<CommandContext, Integer> requestedPage;
    private final Consumer<Runnable> deliver;

    /**
     * @param ready returns whether the command may run for this sender; it reports the reason itself
     * @param routesFor the routes adapted to the sender
     * @param requestedPage the page the sender asked for, or null after telling them it is wrong
     */
    public UpdateCatalogBackupCommands(PnLibrary library, Predicate<CommandContext> ready, Function<CommandContext, UpdateCatalogRoutes> routesFor,
                                Function<CommandContext, Integer> requestedPage, Consumer<Runnable> deliver) {
        this.library = library;
        this.ready = ready;
        this.routesFor = routesFor;
        this.requestedPage = requestedPage;
        this.deliver = deliver;
    }

    private static void permission(CommandNodeBuilder node, String permission) {
        if (permission != null) {
            node.permission(permission);
        }
    }

    private List<String> products(CommandContext context) {
        return UpdateCatalogSupport.productNames(library);
    }

    /** Adds {@code backups}; the read permission opens the list, the manage permission the commands that change something. */
    public void install(BiConsumer<String, Consumer<CommandNodeBuilder>> addLiteral, String readPermission, String managePermission) {
        addLiteral.accept("backups", node -> {
            permission(node, readPermission);
            filters(node, new HashSet<>());
            node.literal("restore", restore -> {
                permission(restore, managePermission);
                restore.argument("backupId", ArgumentType.string(), id -> id.argument("backupPlugin", ArgumentType.string(), plugin -> {
                    plugin.suggests(this::products);
                    plugin.literal("confirm", confirm -> confirm.argument("databaseName", ArgumentType.greedyString(),
                        name -> name.executes(this::restoreDatabase)));
                    plugin.argument("databaseName", ArgumentType.greedyString(), name -> name.executes(this::previewRestore));
                }));
            });
            node.literal("files", files -> {
                permission(files, managePermission);
                files.argument("backupId", ArgumentType.string(), id -> id.argument("backupPlugin", ArgumentType.string(), plugin -> {
                    plugin.suggests(this::products);
                    plugin.executes(this::extractBackup);
                }));
            });
            node.literal("delete", delete -> {
                permission(delete, managePermission);
                delete.argument("backupId", ArgumentType.string(), id -> id.executes(this::deleteBackup));
            });
        });
    }

    /**
     * The words after {@code backups}: {@code plugin <name>}, {@code since <7d|date>} and {@code sort <new|old|plugin>},
     * each at most once and in any order, then an optional page. Every path through them shows the list.
     */
    private void filters(CommandNodeBuilder node, Set<String> used) {
        node.executes(this::showBackups);
        node.argument("page", ArgumentType.integer(), page -> page.executes(this::showBackups));
        if (!used.contains("plugin")) {
            node.literal("plugin", plugin -> plugin.argument("backupPlugin", ArgumentType.string(), value -> {
                value.suggests(this::products);
                filters(value, with(used, "plugin"));
            }));
        }
        if (!used.contains("since")) {
            node.literal("since", since -> since.argument("backupSince", ArgumentType.string(), value -> {
                value.suggests(context -> Arrays.asList("1d", "7d", "30d"));
                filters(value, with(used, "since"));
            }));
        }
        if (!used.contains("sort")) {
            List<String> words = new ArrayList<>();
            for (BackupOrder order : BackupOrder.values()) {
                words.add(order.getWord());
            }
            node.literal("sort", sort -> sort.argument("backupSort", ArgumentType.choice(words.toArray(new String[0])),
                value -> filters(value, with(used, "sort"))));
        }
    }

    private static Set<String> with(Set<String> used, String word) {
        Set<String> more = new HashSet<>(used);
        more.add(word);
        return more;
    }

    /** Without {@code confirm} it only warns and shows the command that does it: a database cannot be put back by accident. */
    private void previewRestore(CommandContext context) {
        if (!ready.test(context)) {
            return;
        }
        UUID id = backupId(context);
        if (id == null) {
            return;
        }
        String plugin = context.get("backupPlugin");
        String name = context.get("databaseName");
        context.getSender().sendMessage(CatalogErrors.line("База «" + name + "» плагина " + plugin
            + " будет заменена сохранённой: всё, что записано в неё после копии, пропадёт."));
        context.getSender().sendMessage(Component.text("Текущее состояние сохранится отдельной копией."));
        context.getSender().sendMessage(Component.text(
            "Если согласны, выполните: /pn updates backups restore " + id + " " + plugin + " confirm " + name));
    }

    private void restoreDatabase(CommandContext context) {
        if (!ready.test(context)) {
            return;
        }
        UUID id = backupId(context);
        if (id == null) {
            return;
        }
        String plugin = context.get("backupPlugin");
        String name = context.get("databaseName");
        org.pnfolder.pnlibrary.api.commands.CommandSender sender = context.getSender();
        library.getUpdates().getBackups().restoreDatabase(id, plugin, name, UpdateCatalogSupport.actorOf(sender))
            .whenComplete((done, failure) -> deliver.accept(() -> {
                if (failure == null) {
                    sender.sendMessage(Component.text("База «" + name + "» возвращена. Перезапустите плагин " + plugin + "."));
                } else {
                    sender.sendMessage(CatalogErrors.line("Не удалось вернуть базу: " + Throwables.rootCause(failure).getMessage()));
                }
            }));
    }

    /** The id the sender typed, or null after telling them it is not one. */
    private UUID backupId(CommandContext context) {
        try {
            return UUID.fromString(context.<String>get("backupId"));
        } catch (IllegalArgumentException invalid) {
            context.getSender().sendMessage(CatalogErrors.line("Некорректный ID резервного набора."));
            return null;
        }
    }

    /** Hands out the saved databases and exports of a plugin as plain files, for what the library cannot put back itself. */
    private void extractBackup(CommandContext context) {
        if (!ready.test(context)) {
            return;
        }
        UUID id = backupId(context);
        if (id == null) {
            return;
        }
        String plugin = context.get("backupPlugin");
        org.pnfolder.pnlibrary.api.commands.CommandSender sender = context.getSender();
        library.getUpdates().getBackups().extract(id, plugin, UpdateCatalogSupport.actorOf(sender))
            .whenComplete((folder, failure) -> deliver.accept(() -> {
                if (failure == null) {
                    sender.sendMessage(Component.text("Файлы плагина " + plugin + " лежат в папке: " + folder));
                } else {
                    sender.sendMessage(CatalogErrors.line("Не удалось выдать файлы: " + Throwables.rootCause(failure).getMessage()));
                }
            }));
    }

    private void deleteBackup(CommandContext context) {
        if (!ready.test(context)) {
            return;
        }
        UUID id = backupId(context);
        if (id == null) {
            return;
        }
        org.pnfolder.pnlibrary.api.commands.CommandSender sender = context.getSender();
        library.getUpdates().getBackups().delete(id, UpdateCatalogSupport.actorOf(sender)).whenComplete((done, failure) -> deliver.accept(() -> {
            if (failure == null) {
                sender.sendMessage(Component.text("Резервный набор " + id + " удалён."));
            } else {
                sender.sendMessage(CatalogErrors.line("Не удалось удалить набор: " + Throwables.rootCause(failure).getMessage()));
            }
        }));
    }

    private void showBackups(CommandContext context) {
        if (!ready.test(context)) {
            return;
        }
        Integer page = requestedPage.apply(context);
        if (page == null) {
            return;
        }
        BackupQuery query = backupQuery(context);
        if (query == null) {
            return;
        }
        library.getUpdates().getBackups().list().whenComplete((backups, failure) -> deliver.accept(() -> {
            if (failure != null) {
                context.getSender().sendMessage(CatalogErrors.line("Не удалось прочитать резервные наборы."));
                return;
            }
            for (Component line : UpdateCatalogView.backups(backups, page, routesFor.apply(context), query)) {
                context.getSender().sendMessage(line);
            }
        }));
    }

    /** What the words after {@code backups} ask for, or null after telling the sender that a value is wrong. */
    private BackupQuery backupQuery(CommandContext context) {
        String plugin = context.contains("backupPlugin") ? context.<String>get("backupPlugin") : null;
        String sinceText = context.contains("backupSince") ? context.<String>get("backupSince") : null;
        Instant since = null;
        if (sinceText != null) {
            since = BackupQuery.parseSince(sinceText, Instant.now(), DisplayZone.getZone());
            if (since == null) {
                context.getSender().sendMessage(CatalogErrors.line("Не понял «" + sinceText + "»: после since напишите " + BackupQuery.SINCE_HELP + "."));
                return null;
            }
        }
        BackupOrder order = context.contains("backupSort") ? BackupQuery.orderOf(context.<String>get("backupSort")) : BackupOrder.NEWEST;
        return new BackupQuery(plugin == null ? null : plugin.toLowerCase(Locale.ROOT), since, sinceText,
            order == null ? BackupOrder.NEWEST : order);
    }
}
