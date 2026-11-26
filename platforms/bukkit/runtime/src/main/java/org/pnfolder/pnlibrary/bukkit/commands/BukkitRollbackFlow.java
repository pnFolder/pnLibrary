package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.Arrays;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateAction;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens;

/** The rollbacks of the {@code /pn} update flow: asked for first, carried out once confirmed. */
final class BukkitRollbackFlow {
    private final PnLibrary library;
    private final BukkitCommandReplies replies;
    private final UpdateConfirmationTokens tokens;
    private final BukkitRestartFlow restart;

    BukkitRollbackFlow(PnLibrary library, BukkitCommandReplies replies, UpdateConfirmationTokens tokens,
                       BukkitRestartFlow restart) {
        this.library = library;
        this.replies = replies;
        this.tokens = tokens;
        this.restart = restart;
    }

    /** Asks to confirm a rollback to one saved backup set; the confirmation prepares it for the next restart. */
    void rollbackSaved(CommandContext context, boolean restoreConfigurations) {
        org.pnfolder.pnlibrary.api.commands.CommandSender reply = context.getSender();
        if (!replies.ready(reply) || !(reply instanceof BukkitCommandSender)) {
            return;
        }
        CommandSender nativeSender = ((BukkitCommandSender) reply).getNative();
        UUID id;
        try {
            id = UUID.fromString(context.<String>get("transaction"));
        } catch (RuntimeException error) {
            replies.say(reply, ChatTone.FAILURE, "Некорректный ID резервного набора.");
            return;
        }
        UpdateAction action = restoreConfigurations ? UpdateAction.ROLLBACK_CONFIGS : UpdateAction.ROLLBACK;
        askToConfirm(nativeSender, reply, id, 0, action, "Откат к резервному набору",
            Arrays.asList(
                restoreConfigurations ? "Вернутся сохранённые JAR и выбранные конфигурации."
                    : "Вернутся сохранённые JAR, конфигурации не изменятся.",
                "Применится после полного перезапуска сервера."),
            "[Подтвердить откат]");
    }

    void rollbackUpdate(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.");
            return;
        }
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        if (plan == null) {
            replies.say(reply, ChatTone.WARNING, "Нет плана обновления, который можно откатить.");
            return;
        }
        askToConfirm(sender, reply, plan.getId(), plan.getRevision(), UpdateAction.PLAN_ROLLBACK, "Откат обновления",
            Arrays.asList("Откатится весь связанный план обновления, а не один плагин.",
                "Предыдущие JAR применятся после перезапуска."),
            "[Подтвердить откат]");
    }

    void runPlanRollback(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply,
                         UpdateConfirmationTokens.Grant grant) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.");
            return;
        }
        if (!restart.isStill(grant)) {
            replies.say(reply, ChatTone.FAILURE, "План обновления изменился; запросите откат заново.");
            return;
        }
        library.getUpdates().getBackups().rollback(replies.updateActor(sender)).whenComplete((ignored, error) ->
            finished(sender, reply, error, "Предыдущие версии плагинов подготовлены.", "Полностью перезапустите сервер."));
    }

    void runSavedRollback(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, UUID id,
                          boolean restoreConfigurations) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.");
            return;
        }
        library.getUpdates().getBackups().rollback(id, replies.updateActor(sender), restoreConfigurations)
            .whenComplete((ignored, error) -> finished(sender, reply, error, "Резервный набор подготовлен.",
                "Полностью перезапустите сервер, чтобы применить его."));
    }

    private void finished(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, Throwable error,
                          String done, String detail) {
        replies.onServerThread(() -> {
            if (error != null) {
                replies.fail(reply, "Не удалось подготовить откат", error);
            } else {
                replies.say(reply, ChatTone.SUCCESS, done, detail);
                restart.suggest(sender, reply);
            }
        });
    }

    private void askToConfirm(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply, UUID plan,
                              long revision, UpdateAction action, String title, java.util.List<String> lines,
                              String label) {
        String token = tokens.issue(ConfirmationIdentity.identityOf(sender), plan, revision, action,
            ConfirmationIdentity.CONFIRMATION_TIME);
        replies.confirm(reply, title, lines, label, BukkitControlCommand.confirmCommand(token));
    }
}
