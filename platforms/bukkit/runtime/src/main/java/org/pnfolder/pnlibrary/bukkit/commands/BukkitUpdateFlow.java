package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.pnfolder.pnlibrary.common.lang.Pair;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.bukkit.updates.UpdateViews;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages;

/**
 * The {@code /pn} update flow: status, check, stage, rollback, restart, and the one confirmation they share.
 *
 * <p>Rollback and restart are asked for first and happen on {@code /pn update confirm <token>}: the token says which
 * of them it grants, so there is a single confirmation command. A token belongs to the player (or the console) that
 * asked, works once and expires; asynchronous results are delivered on the server thread.
 */
final class BukkitUpdateFlow {
    private static final long PROGRESS_TICKS = 100L;
    private static final long TIMEOUT_TICKS = 1_200L;

    private final PnLibrary library;
    private final UpdateCatalogCommands catalog;
    private final BukkitCommandReplies replies;
    private final ServerScheduler scheduler;
    private final UpdateConfirmationTokens tokens = new UpdateConfirmationTokens();
    private final BukkitRestartFlow restart;
    private final BukkitRollbackFlow rollback;

    BukkitUpdateFlow(Plugin plugin, PnLibrary library, UpdateCatalogCommands catalog, BukkitCommandReplies replies,
                     ServerScheduler scheduler) {
        this.library = library;
        this.catalog = catalog;
        this.replies = replies;
        this.scheduler = scheduler;
        ServerRestarter restarter = new ServerRestarter(scheduler, () -> library.getLogging().logger(plugin, "pnLibrary"));
        this.restart = new BukkitRestartFlow(library, replies, tokens, restarter);
        this.rollback = new BukkitRollbackFlow(library, replies, tokens, restart);
    }

    /** Replies immediately, then shows the refreshed list of updates on the server thread. */
    void checkUpdates(CommandContext context) {
        if (!replies.ready(context.getSender()) || !(context.getSender() instanceof BukkitCommandSender)) {
            return;
        }
        CommandSender nativeSender = ((BukkitCommandSender) context.getSender()).getNative();
        replies.say(context.getSender(), ChatTone.PROGRESS, "Проверяю обновления…");
        library.getUpdates().getPlans().check(replies.updateActor(nativeSender)).whenComplete((ignored, error) ->
            replies.onServerThread(() -> {
                if (error != null) {
                    replies.fail(context.getSender(), "Не удалось проверить обновления", error);
                } else {
                    catalog.showAvailable(context);
                }
            }));
    }

    void rollbackSaved(CommandContext context, boolean restoreConfigurations) {
        rollback.rollbackSaved(context, restoreConfigurations);
    }

    void rollbackUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        rollback.rollbackUpdate(sender, reply);
    }

    void restartUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        restart.restartUpdate(sender, reply);
    }

    void cancelRestart(org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        restart.cancelRestart(reply);
    }

    // --- status -------------------------------------------------------------------------------------------------

    /** The detailed console card, or the server state card styled like the catalog. */
    void sendStatus(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, String requested) {
        List<UpdateRegistration> entries = registrations(requested);
        if (sender instanceof ConsoleCommandSender && requested == null) {
            for (String line : BukkitConsoleStatusRenderer.render(entries)) {
                sender.sendMessage(line);
            }
            return;
        }
        if (requested != null && entries.isEmpty()) {
            replies.notRegistered(reply, requested);
            return;
        }
        List<Pair<String, String>> facts = new ArrayList<>();
        facts.add(new Pair<>("Ядро", Bukkit.getName() + " " + Bukkit.getBukkitVersion()));
        facts.add(new Pair<>("Java", BukkitStatusLabels.javaRuntime()));
        facts.add(new Pair<>(PnLibraryIdentity.NAME, library.getVersion().toString()));
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (UpdateRegistration entry : entries) {
            snapshots.add(entry.getSnapshot());
        }
        for (net.kyori.adventure.text.Component line : UpdateViews.status(facts, snapshots, replies.routes(reply),
            PnLibraryIdentity.SUPPORT_URL)) {
            reply.sendMessage(line);
        }
    }

    private List<UpdateRegistration> registrations(String requested) {
        if (requested == null) {
            return library.getUpdates().getProducts().all();
        }
        UpdateRegistration found = library.getUpdates().getProducts().get(requested);
        return found == null ? Collections.<UpdateRegistration>emptyList() : Collections.singletonList(found);
    }

    // --- update -------------------------------------------------------------------------------------------------

    /** Validates download permissions and selects the requested update registration. */
    void update(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, String name) {
        if (!sender.hasPermission("pnlibrary.updates.download")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для загрузки обновлений.");
            return;
        }
        UpdateRegistration registration = library.getUpdates().getProducts().get(name);
        if (registration == null) {
            replies.notRegistered(reply, name);
            return;
        }
        checkAndInstallUpdate(sender, reply, registration);
    }

    /** Checks the update plan with progress messages and a bounded user-facing timeout. */
    private void checkAndInstallUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply,
                                       UpdateRegistration registration) {
        String product = registration.getSnapshot().getProduct();
        replies.say(reply, ChatTone.PROGRESS, "Ищу совместимое обновление " + product + "…");
        AtomicBoolean finished = new AtomicBoolean(false);
        scheduleProgress(reply, finished);
        scheduler.later(TIMEOUT_TICKS, () -> {
            if (finished.compareAndSet(false, true)) {
                replies.say(reply, ChatTone.FAILURE, "Проверка прервана: источники не ответили за 60 секунд.",
                    "Проверьте доступ сервера к raw.githubusercontent.com и повторите команду.");
            }
        });
        library.getUpdates().getPlans().check(replies.updateActor(sender)).whenComplete((result, error) -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            replies.onServerThread(() -> {
                if (error != null) {
                    replies.fail(reply, "Проверка обновления не удалась", error);
                } else {
                    handleUpdateCheck(sender, reply, registration, result);
                }
            });
        });
    }

    private void scheduleProgress(org.pnfolder.pnlibrary.api.commands.CommandSender reply, AtomicBoolean finished) {
        scheduler.later(PROGRESS_TICKS, () -> {
            if (finished.get()) {
                return;
            }
            replies.say(reply, ChatTone.PROGRESS, "Проверка ещё идёт: жду ответ каталога релизов…");
            scheduleProgress(reply, finished);
        });
    }

    private void handleUpdateCheck(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply,
                                   UpdateRegistration registration, UpdatePlanSnapshot result) {
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
            replies.notices(reply, UpdateStatusMessages.status(result, registration.getSnapshot()));
            return;
        }
        stageUpdate(sender, reply, result, change);
    }

    private void stageUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply,
                             UpdatePlanSnapshot plan, ProductChange change) {
        String transition = change.getProduct() + " v" + change.getFrom() + " » v" + change.getTo();
        replies.say(reply, ChatTone.PROGRESS, "Найдено " + transition + ". Скачиваю и проверяю…");
        library.getUpdates().getPlans().stage(plan.getId(), replies.updateActor(sender)).whenComplete((ignored, error) ->
            replies.onServerThread(() -> {
                if (error != null) {
                    replies.fail(reply, "Не удалось подготовить обновление", error);
                } else {
                    replies.say(reply, ChatTone.SUCCESS, "Обновление " + transition + " подготовлено.",
                        "Перезапустите сервер, чтобы применить его.");
                    restart.suggest(sender, reply);
                }
            }));
    }

    /** Runs what {@code token} grants: a rollback of the plan, a rollback to a saved backup set, or the restart. */
    void confirm(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, String token) {
        UpdateConfirmationTokens.Grant grant = tokens.redeem(token, ConfirmationIdentity.identityOf(sender));
        if (grant == null) {
            replies.say(reply, ChatTone.FAILURE, "Подтверждение недействительно, истекло или уже использовано.");
            return;
        }
        UUID plan = grant.getPlan();
        switch (grant.getAction()) {
            case PLAN_ROLLBACK:
                rollback.runPlanRollback(sender, reply, grant);
                break;
            case RESTART:
                restart.run(sender, reply, grant, ServerRestarter.Mode.NOW);
                break;
            case RESTART_LATER:
                restart.run(sender, reply, grant, ServerRestarter.Mode.IN_FIVE_MINUTES);
                break;
            case RESTART_EMPTY:
                restart.run(sender, reply, grant, ServerRestarter.Mode.WHEN_EMPTY);
                break;
            case ROLLBACK:
                rollback.runSavedRollback(sender, reply, plan, false);
                break;
            default:
                rollback.runSavedRollback(sender, reply, plan, true);
        }
    }

    /** Shows the status of the current update plan for {@code requested} or for every registered product. */
    void sendUpdateStatus(org.pnfolder.pnlibrary.api.commands.CommandSender reply, String requested) {
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        if (plan == null) {
            replies.say(reply, ChatTone.WARNING, "Проверка обновлений ещё не завершалась.",
                "Запустите её командой /pn check.");
            return;
        }
        List<UpdateRegistration> entries = registrations(requested);
        if (entries.isEmpty()) {
            replies.notRegistered(reply, requested != null ? requested : "с указанным именем");
            return;
        }
        List<ChatNotice> notices = new ArrayList<>();
        for (UpdateRegistration entry : entries) {
            notices.addAll(UpdateStatusMessages.status(plan, entry.getSnapshot()));
        }
        replies.notices(reply, notices);
    }
}
