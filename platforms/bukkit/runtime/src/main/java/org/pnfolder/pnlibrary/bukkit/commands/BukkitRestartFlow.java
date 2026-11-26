package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.Arrays;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.chat.notice.ConfirmChoice;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateAction;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens;

/** Asks for, confirms and cancels the server restart that applies a staged update plan. */
final class BukkitRestartFlow {
    private final PnLibrary library;
    private final BukkitCommandReplies replies;
    private final UpdateConfirmationTokens tokens;
    private final ServerRestarter restarter;

    BukkitRestartFlow(PnLibrary library, BukkitCommandReplies replies, UpdateConfirmationTokens tokens,
                      ServerRestarter restarter) {
        this.library = library;
        this.replies = replies;
        this.tokens = tokens;
        this.restarter = restarter;
    }

    /** Asks to confirm the restart that applies the staged plan. */
    void restartUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.");
            return;
        }
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        if (plan == null || plan.getState() != UpdateState.UPDATE_STAGED && plan.getState() != UpdateState.ROLLED_BACK) {
            replies.say(reply, ChatTone.WARNING, "Нет подготовленных обновлений, которым нужен перезапуск.");
            return;
        }
        askRestart(sender, reply, plan.getId(), plan.getRevision());
    }

    /** Offers the three ways to restart; each button carries its own confirmation. */
    private void askRestart(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, UUID plan,
                            long revision) {
        String script = restarter.script() == null
            ? "restart-script в spigot.yml не задан: сервер остановится и поднимется, только если его запустит панель или скрипт автозапуска."
            : "Сервер поднимется сам.";
        replies.confirmChoices(
            reply,
            "Перезапуск сервера",
            Arrays.asList("Игроков на сервере: " + Bukkit.getOnlinePlayers().size() + ".",
                "Подготовленные версии применятся после перезапуска.", script),
            Arrays.asList(
                new ConfirmChoice("Сейчас", command(sender, plan, revision, UpdateAction.RESTART),
                    "Перезапустить сразу, игрокам придёт отсчёт"),
                new ConfirmChoice("Через 5 мин", command(sender, plan, revision, UpdateAction.RESTART_LATER),
                    "Предупредить игроков и перезапустить через 5 минут"),
                new ConfirmChoice("Когда никого нет", command(sender, plan, revision, UpdateAction.RESTART_EMPTY),
                    "Перезапустить, как только все выйдут")));
    }

    private String command(CommandSender sender, UUID plan, long revision, UpdateAction action) {
        return BukkitControlCommand.confirmCommand(tokens.issue(ConfirmationIdentity.identityOf(sender), plan, revision,
            action, ConfirmationIdentity.CONFIRMATION_TIME));
    }

    /** Offers a restart when {@code sender} may restart, otherwise just says the server has to be restarted. */
    void suggest(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (sender.hasPermission("pnlibrary.updates.restart")) {
            restartUpdate(sender, reply);
        }
    }

    /** Withdraws a restart that is counting down or waiting for the server to empty. */
    void cancelRestart(org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (restarter.cancel()) {
            replies.say(reply, ChatTone.SUCCESS, "Перезапуск отменён.");
        } else {
            replies.say(reply, ChatTone.WARNING, "Перезапуск не запланирован.");
        }
    }

    /** Whether the confirmed update plan is still the current one, so the restart acts on what was approved. */
    boolean isStill(UpdateConfirmationTokens.Grant grant) {
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        return plan != null && plan.getId().equals(grant.getPlan()) && plan.getRevision() == grant.getRevision();
    }

    /** Starts the restart in {@code mode} after checking the permission and that {@code grant} still matches the plan. */
    void run(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply,
             UpdateConfirmationTokens.Grant grant, ServerRestarter.Mode mode) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.");
            return;
        }
        if (!isStill(grant)) {
            replies.say(reply, ChatTone.FAILURE, "План обновления изменился; запросите перезапуск заново.");
            return;
        }
        if (restarter.start(mode) == ServerRestarter.Started.COUNTDOWN) {
            replies.say(reply, ChatTone.SUCCESS, "Перезапуск запланирован.", "Отменить: /pn update restart cancel");
        } else {
            replies.say(reply, ChatTone.SUCCESS, "Перезапущу сервер, когда на нём никого не останется.",
                "Отменить: /pn update restart cancel");
        }
    }
}
