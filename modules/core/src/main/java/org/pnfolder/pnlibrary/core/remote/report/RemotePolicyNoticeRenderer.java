package org.pnfolder.pnlibrary.core.remote.report;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.result.DenyAction;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyExplanation;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.console.ConsoleTree;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;

/** Prints the console card with the result of a remote compatibility check. */
public final class RemotePolicyNoticeRenderer {
    private static final String TITLE = "ПРОВЕРКА СОВМЕСТИМОСТИ";
    private static final String SUPPORT = "Напишите в поддержку: ";

    private final PlatformLogging logging;

    public RemotePolicyNoticeRenderer(@NotNull PlatformLogging logging) {
        this.logging = Objects.requireNonNull(logging, "logging");
    }

    public void render(@NotNull Object owner, @NotNull PluginMetadata metadata, @NotNull RemotePolicySettings policy,
                       boolean allowed, @NotNull RemotePolicyExplanation explanation) {
        card(owner, metadata, policy, allowed, explanation, null);
    }

    /** Prints the card for a policy decision, including the custom status, details, steps and links it carries. */
    public void render(@NotNull Object owner, @NotNull PluginMetadata metadata, @NotNull RemotePolicySettings policy,
                       @NotNull RemotePolicyResult result) {
        card(owner, metadata, policy, result.getAllowed(), result.getExplanation(), result);
    }

    private void card(Object owner, PluginMetadata metadata, RemotePolicySettings policy, boolean allowed,
                      RemotePolicyExplanation explanation, @Nullable RemotePolicyResult result) {
        ConsoleTheme theme = ConsoleStyle.theme(allowed ? ChatTone.SUCCESS : ChatTone.FAILURE);
        ConsoleCard.Builder card = ConsoleCard.builder(theme, TITLE)
            .mascot(allowed ? "^.^" : "x.x", metadata.getName(), headline(allowed, result))
            .blank()
            .detail("Установлена", metadata.getVersion());
        if (result != null) {
            for (Map.Entry<String, String> detail : result.getDetails().entrySet()) {
                card.detail(detail.getKey(), detail.getValue());
            }
        }
        card.lastDetail("Состояние", status(allowed, result))
            .blank()
            .section(allowed ? "Результат" : "Почему запуск остановлен")
            .tree(toConsoleTree(explanation));
        if (result != null) {
            steps(card, result.getSteps());
            links(card, result.getLinks());
        }
        if (!allowed) {
            card.section("Не получается?").lastItem(SUPPORT + PnLibraryIdentity.SUPPORT_URL);
        }
        card.blank().status(action(allowed, policy)).build().send(line -> logging.console(owner, line));
    }

    private static String headline(boolean allowed, RemotePolicyResult result) {
        String own = result == null ? null : result.getHeadline();
        if (own != null) {
            return own;
        }
        return allowed ? "версия поддерживается" : "для запуска требуется обновление";
    }

    private static String status(boolean allowed, RemotePolicyResult result) {
        String own = result == null ? null : result.getStatus();
        if (own != null) {
            return own;
        }
        return allowed ? "совместима" : "не поддерживается";
    }

    private static String action(boolean allowed, RemotePolicySettings policy) {
        if (allowed) {
            return "Плагин продолжает работу";
        }
        return policy.getOnDeny() == DenyAction.DISABLE_PLUGIN ? "Плагин безопасно отключён" : "Модуль безопасно остановлен";
    }

    private static void steps(ConsoleCard.Builder card, List<String> steps) {
        if (steps.isEmpty()) {
            return;
        }
        card.section("Что делать");
        for (String step : steps.subList(0, steps.size() - 1)) {
            card.item(step);
        }
        card.lastItem(steps.get(steps.size() - 1));
    }

    private static void links(ConsoleCard.Builder card, Map<String, String> links) {
        if (links.isEmpty()) {
            return;
        }
        card.section("Ссылки");
        List<Map.Entry<String, String>> entries = new ArrayList<>(links.entrySet());
        for (Map.Entry<String, String> link : entries.subList(0, entries.size() - 1)) {
            card.item(link.getKey() + ": " + link.getValue());
        }
        Map.Entry<String, String> last = entries.get(entries.size() - 1);
        card.lastItem(last.getKey() + ": " + last.getValue());
    }

    /**
     * A policy that could not be checked while the plugin keeps running: why, what to do, and when it is retried.
     * {@code network} adds the hint that the check needs internet access.
     */
    public void unchecked(@NotNull Object owner, @NotNull PluginMetadata metadata, @NotNull RemotePolicySettings policy,
                          @NotNull String reason, boolean network) {
        ConsoleCard.Builder card = ConsoleCard.builder(ConsoleStyle.theme(ChatTone.WARNING), TITLE)
            .mascot("o.o", metadata.getName(), "проверку выполнить не удалось")
            .blank()
            .detail("Установлена", metadata.getVersion())
            .lastDetail("Следующая попытка", "через " + RussianText.period(policy.getCheckEvery()))
            .blank()
            .section("Причина")
            .lastItem(reason)
            .section("Что делать");
        if (network) {
            card.item("Для проверки нужен интернет").lastItem("Откройте серверу доступ к raw.githubusercontent.com");
        } else {
            card.lastItem("Пришлите этот лог автору плагина");
        }
        card.section("Не получается?").lastItem(SUPPORT + PnLibraryIdentity.SUPPORT_URL);
        card.blank().status("Плагин продолжает работу").build().send(line -> logging.console(owner, line));
    }

    private static ConsoleTree toConsoleTree(RemotePolicyExplanation explanation) {
        ConsoleTree.Builder tree = ConsoleTree.builder(explanation.getText());
        for (RemotePolicyExplanation child : explanation.getChildren()) {
            tree.child(toConsoleTree(child));
        }
        return tree.build();
    }
}
