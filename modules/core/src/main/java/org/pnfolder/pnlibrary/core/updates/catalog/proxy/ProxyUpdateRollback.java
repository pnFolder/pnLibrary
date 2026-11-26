package org.pnfolder.pnlibrary.core.updates.catalog.proxy;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogSupport;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import net.kyori.adventure.text.Component;

/** Rollback of the proxy updates and the confirmation that guards it. */
final class ProxyUpdateRollback {
    private final PnLibrary library;
    private final UpdateConfirmationTokens tokens;

    ProxyUpdateRollback(PnLibrary library, UpdateConfirmationTokens tokens) {
        this.library = library;
        this.tokens = tokens;
    }

    void rollbackPlan(CommandContext context) {
        CommandSender sender = context.getSender();
        if (!AudienceExtensions.hasPermission(sender, ProxyUpdateCommand.ROLLBACK)) {
            ProxyReplies.say(sender, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.");
            return;
        }
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        if (plan == null) {
            ProxyReplies.say(sender, ChatTone.WARNING, "Нет плана обновления, который можно откатить.");
            return;
        }
        ask(sender, plan.getId(), plan.getRevision(), UpdateAction.PLAN_ROLLBACK, "Откат обновления",
            Arrays.asList("Откатится весь связанный план обновления, а не один плагин.", "Предыдущие JAR применятся после перезапуска."));
    }

    void rollbackSaved(CommandContext context, boolean restoreConfigurations) {
        CommandSender sender = context.getSender();
        UUID id;
        try {
            id = UUID.fromString(context.<String>get("transaction"));
        } catch (RuntimeException invalid) {
            ProxyReplies.say(sender, ChatTone.FAILURE, "Некорректный ID резервного набора.");
            return;
        }
        UpdateAction action = restoreConfigurations ? UpdateAction.ROLLBACK_CONFIGS : UpdateAction.ROLLBACK;
        ask(sender, id, 0, action, "Откат к резервному набору", Arrays.asList(
            restoreConfigurations ? "Вернутся сохранённые JAR и выбранные конфигурации." : "Вернутся сохранённые JAR, конфигурации не изменятся.",
            "Применится после полного перезапуска прокси."));
    }

    private void ask(CommandSender sender, UUID plan, long revision, UpdateAction action, String title, List<String> lines) {
        String token = tokens.issue(identityOf(sender), plan, revision, action, ProxyUpdateCommand.CONFIRMATION_TIME);
        String command = "/pn update confirm " + token;
        List<String> shown = new ArrayList<>(lines);
        shown.add("Подтверждение действует 30 секунд.");
        for (Component line : UpdateCatalogView.confirmation(title, shown, "[Подтвердить откат]", command, "Выполнить: " + command,
            ProxyReplies.routes(sender))) {
            sender.sendMessage(line);
        }
    }

    void confirm(CommandContext context, String token) {
        CommandSender sender = context.getSender();
        UpdateConfirmationTokens.Grant grant = tokens.redeem(token, identityOf(sender));
        if (grant == null) {
            ProxyReplies.say(sender, ChatTone.FAILURE, "Подтверждение недействительно, истекло или уже использовано.");
            return;
        }
        if (!AudienceExtensions.hasPermission(sender, ProxyUpdateCommand.ROLLBACK)) {
            ProxyReplies.say(sender, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.");
            return;
        }
        switch (grant.getAction()) {
            case PLAN_ROLLBACK:
                UpdatePlanSnapshot current = library.getUpdates().getPlans().current().orElse(null);
                if (current == null || !current.getId().equals(grant.getPlan()) || current.getRevision() != grant.getRevision()) {
                    ProxyReplies.say(sender, ChatTone.FAILURE, "План обновления изменился; запросите откат заново.");
                    return;
                }
                done(sender, library.getUpdates().getBackups().rollback(UpdateCatalogSupport.actorOf(sender)));
                break;
            case ROLLBACK:
                done(sender, library.getUpdates().getBackups().rollback(grant.getPlan(), UpdateCatalogSupport.actorOf(sender), false));
                break;
            case ROLLBACK_CONFIGS:
                done(sender, library.getUpdates().getBackups().rollback(grant.getPlan(), UpdateCatalogSupport.actorOf(sender), true));
                break;
            default:
                ProxyReplies.say(sender, ChatTone.WARNING, "Перезапустите прокси его обычной командой.");
                break;
        }
    }

    private void done(CommandSender sender, CompletionStage<?> stage) {
        stage.whenComplete((result, error) -> {
            if (error != null) {
                ProxyReplies.fail(sender, "Не удалось подготовить откат", error);
            } else {
                ProxyReplies.say(sender, ChatTone.SUCCESS, "Предыдущие версии подготовлены.", "Полностью перезапустите прокси.");
            }
        });
    }

    private static UUID identityOf(CommandSender sender) {
        return UUID.nameUUIDFromBytes(AudienceExtensions.id(sender).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
