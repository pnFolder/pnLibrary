package org.pnfolder.pnlibrary.core.updates.catalog.proxy;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogSupport;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogCommands;
import java.util.Objects;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.common.lang.Pair;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/**
 * The {@code /pn} update controls of the proxies, the same as on Bukkit: the list of plugins, status, check, the
 * catalog ({@code updates …}), staging one plugin ({@code update <plugin>}), rollback and its confirmation.
 *
 * <p>It works for the console and for players alike; each action asks for its own permission, and the output is
 * clickable for a sender that can click and plain text for the console. A proxy has no restart command of its own,
 * so a staged update is applied by restarting the proxy the usual way.
 */
public final class ProxyUpdateCommand {
    public static final UpdateCatalogRoutes ROUTES =
        new UpdateCatalogRoutes("/pn updates", product -> "/pn update " + product, "/pn check");
    static final Duration CONFIRMATION_TIME = Duration.ofSeconds(30);
    static final String ADMIN = "pnlibrary.admin";
    static final String VIEW = "pnlibrary.updates.view";
    static final String DOWNLOAD = "pnlibrary.updates.download";
    static final String ROLLBACK = "pnlibrary.updates.rollback";
    static final String SETTINGS = "pnlibrary.updates.settings";

    private final PnLibrary library;
    private final UpdateConfirmationTokens tokens = new UpdateConfirmationTokens();
    private final ProxyUpdateRollback rollbacks;
    private final UpdateCatalogCommands catalog;

    public ProxyUpdateCommand(@NotNull PnLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
        this.catalog = new UpdateCatalogCommands(library, ROUTES, VIEW, ROLLBACK, SETTINGS);
        this.rollbacks = new ProxyUpdateRollback(library, tokens);
    }

    /** The catalog commands ({@code list}, {@code available}, {@code info}, {@code settings}, {@code set}, {@code history}, {@code backups}). */
    public @NotNull UpdateCatalogCommands getCatalog() {
        return catalog;
    }

    /** Adds the update commands to the {@code /pn} root being built. */
    public void install(@NotNull CommandBuilder root) {
        root.literal("status", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(context -> status(context, null));
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> UpdateCatalogSupport.productNames(library));
                plugin.executes(context -> status(context, context.<String>get("plugin")));
            });
        });
        root.literal("check", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(this::check);
        });
        root.literal("updates", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(catalog::showList);
            catalog.install((name, configure) -> node.literal(name, configure));
            node.literal("rollback", rollback -> {
                rollback.permission(ROLLBACK);
                rollback.argument("transaction", ArgumentType.string(), transaction -> {
                    transaction.executes(context -> rollbacks.rollbackSaved(context, false));
                    transaction.literal("configs", configs -> configs.executes(context -> rollbacks.rollbackSaved(context, true)));
                });
            });
        });
        root.literal("update", node -> {
            node.permission(ADMIN, DOWNLOAD);
            node.argument("plugin", ArgumentType.string(), plugin -> {
                plugin.suggests(context -> UpdateCatalogSupport.productNames(library));
                plugin.executes(context -> update(context, context.<String>get("plugin")));
            });
            node.literal("confirm", confirm -> confirm.argument("token", ArgumentType.string(),
                token -> token.executes(context -> rollbacks.confirm(context, context.<String>get("token")))));
            node.literal("rollback", rollback -> rollback.executes(rollbacks::rollbackPlan));
        });
        root.literal("support", node -> {
            node.permission(ADMIN, VIEW);
            node.executes(context -> ProxyReplies.say(context.getSender(), ChatTone.INFO, "Поддержка pnFolder: " + PnLibraryIdentity.SUPPORT_URL));
        });
    }

    /** {@code /pn} alone: the list of plugins for an administrator, otherwise the commands the sender may use. */
    public void overview(@NotNull CommandContext context, @NotNull List<Pair<String, String>> others) {
        CommandSender sender = context.getSender();
        if (AudienceExtensions.hasPermission(sender, ADMIN)) {
            catalog.showList(context);
            return;
        }
        List<String> available = new ArrayList<>();
        for (Pair<String, String> other : others) {
            if (AudienceExtensions.hasPermission(sender, other.getSecond())) {
                available.add(other.getFirst());
            }
        }
        ProxyReplies.say(sender, ChatTone.INFO, "Команды pnLibrary, доступные вам:", available.isEmpty() ? "нет" : String.join("  ", available));
    }

    private void status(CommandContext context, @Nullable String requested) {
        CommandSender sender = context.getSender();
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        if (requested == null) {
            for (UpdateRegistration product : library.getUpdates().getProducts().all()) {
                snapshots.add(product.getSnapshot());
            }
        } else {
            UpdateRegistration found = library.getUpdates().getProducts().get(requested);
            if (found == null) {
                ProxyReplies.notRegistered(sender, requested);
                return;
            }
            snapshots.add(found.getSnapshot());
        }
        String java = System.getProperty("java.version");
        List<Pair<String, String>> facts = new ArrayList<>();
        facts.add(new Pair<>("Java", java == null ? "?" : java));
        facts.add(new Pair<>(PnLibraryIdentity.NAME, library.getVersion().toString()));
        for (Component line : UpdateCatalogView.status(facts, snapshots, ProxyReplies.routes(sender), PnLibraryIdentity.SUPPORT_URL)) {
            sender.sendMessage(line);
        }
    }

    private void check(CommandContext context) {
        CommandSender sender = context.getSender();
        ProxyReplies.say(sender, ChatTone.PROGRESS, "Проверяю обновления…");
        library.getUpdates().getPlans().check(UpdateCatalogSupport.actorOf(sender)).whenComplete((result, error) -> {
            if (error != null) {
                ProxyReplies.fail(sender, "Не удалось проверить обновления", error);
            } else {
                catalog.showAvailable(context);
            }
        });
    }

    private void update(CommandContext context, String name) {
        CommandSender sender = context.getSender();
        if (!AudienceExtensions.hasPermission(sender, DOWNLOAD)) {
            ProxyReplies.say(sender, ChatTone.FAILURE, "Недостаточно прав для загрузки обновлений.");
            return;
        }
        UpdateRegistration registration = library.getUpdates().getProducts().get(name);
        if (registration == null) {
            ProxyReplies.notRegistered(sender, name);
            return;
        }
        ProxyReplies.say(sender, ChatTone.PROGRESS, "Ищу совместимое обновление " + registration.getSnapshot().getProduct() + "…");
        library.getUpdates().getPlans().check(UpdateCatalogSupport.actorOf(sender)).whenComplete((result, error) -> {
            if (error != null) {
                ProxyReplies.fail(sender, "Проверка обновления не удалась", error);
            } else {
                stage(sender, registration, result);
            }
        });
    }

    private void stage(CommandSender sender, UpdateRegistration registration, UpdatePlanSnapshot result) {
        ProductChange change = null;
        if (result.getPlan() != null) {
            for (ProductChange candidate : result.getPlan().getChanges()) {
                if (candidate.getProduct().getValue().equalsIgnoreCase(registration.getSnapshot().getProduct())) {
                    change = candidate;
                    break;
                }
            }
        }
        if (result.getState() != UpdateState.UPDATE_AVAILABLE || change == null) {
            ProxyReplies.notices(sender, UpdateStatusMessages.status(result, registration.getSnapshot()));
            return;
        }
        String transition = change.getProduct() + " v" + change.getFrom() + " » v" + change.getTo();
        ProxyReplies.say(sender, ChatTone.PROGRESS, "Найдено " + transition + ". Скачиваю и проверяю…");
        library.getUpdates().getPlans().stage(result.getId(), UpdateCatalogSupport.actorOf(sender)).whenComplete((done, error) -> {
            if (error != null) {
                ProxyReplies.fail(sender, "Не удалось подготовить обновление", error);
            } else {
                ProxyReplies.say(sender, ChatTone.SUCCESS, "Обновление " + transition + " подготовлено.",
                    "Перезапустите прокси, чтобы применить его.");
            }
        });
    }
}
