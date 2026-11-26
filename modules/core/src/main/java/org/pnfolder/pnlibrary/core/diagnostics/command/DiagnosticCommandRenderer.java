package org.pnfolder.pnlibrary.core.diagnostics.command;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;

/** Renders diagnostics state once for every native platform, as notices in the shared chat style. */
public final class DiagnosticCommandRenderer {
    private static final String DEFAULT_ROOT = "/pn debug";

    private DiagnosticCommandRenderer() {
    }

    /** Renders {@code event} with usage lines that start with {@code /pn debug}. */
    @NotNull
    public static List<Component> render(@NotNull DiagnosticCommandEvent event, @NotNull ChatKit kit) {
        return render(event, kit, DEFAULT_ROOT);
    }

    /** Renders {@code event} with usage lines that start with {@code root}. */
    @NotNull
    public static List<Component> render(@NotNull DiagnosticCommandEvent event, @NotNull ChatKit kit,
                                         @NotNull String root) {
        if (event instanceof DiagnosticCommandEvent.InvalidUsage) {
            return kit.notice(new ChatNotice(ChatTone.WARNING, "Неизвестные аргументы.",
                root + " [all|плагин] [--local]"));
        }
        if (event instanceof DiagnosticCommandEvent.CoolingDown) {
            long seconds = ((DiagnosticCommandEvent.CoolingDown) event).getSeconds();
            return kit.notice(new ChatNotice(ChatTone.WARNING,
                "Подождите " + seconds + " с перед следующим отчётом."));
        }
        if (event instanceof DiagnosticCommandEvent.Started) {
            String target = ((DiagnosticCommandEvent.Started) event).getTarget();
            return kit.notice(new ChatNotice(ChatTone.PROGRESS, "Собираю диагностический отчёт: " + target + "…"));
        }
        if (event instanceof DiagnosticCommandEvent.Completed) {
            return completed(kit, ((DiagnosticCommandEvent.Completed) event).getReport());
        }
        String message = ((DiagnosticCommandEvent.Failed) event).getMessage();
        return kit.notice(new ChatNotice(ChatTone.FAILURE, "Отчёт не создан.", message));
    }

    /** A finished report: its link with a button to open it, or the local file and why the upload failed. */
    private static List<Component> completed(ChatKit kit, DiagnosticReport report) {
        String link = report.getUploadedUrl();
        String error = report.getUploadError();
        if (link != null) {
            List<Component> lines = new ArrayList<>(kit.notice(new ChatNotice(ChatTone.SUCCESS, "Отчёт готов.", link)));
            Component actions = kit.actions(
                kit.linkButton("Открыть отчёт", link, "Открыть отчёт в браузере", ChatPalette.INFO));
            if (actions != null) {
                lines.add(actions);
            }
            return lines;
        }
        if (error != null) {
            return kit.notice(new ChatNotice(ChatTone.WARNING,
                "Отчёт сохранён только локально: загрузить не удалось.",
                report.getLocalFile() + " · " + error));
        }
        return kit.notice(new ChatNotice(ChatTone.SUCCESS, "Отчёт сохранён.", report.getLocalFile().toString()));
    }
}
