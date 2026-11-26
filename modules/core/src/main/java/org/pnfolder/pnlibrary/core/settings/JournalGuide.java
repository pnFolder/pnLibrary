package org.pnfolder.pnlibrary.core.settings;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class JournalGuide {
    private JournalGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Журнал и диагностика",
            Arrays.asList(
                GuideEntry.of(
                    "/pn events [info|warning|error|all] [страница]",
                    "pnlibrary.events.view",
                    "Журнал событий и ошибок, повторы свёрнуты в одну строку.")
                    .note("Это же право показывает сводку ошибок при входе на сервер."),
                GuideEntry.of(
                    "/pn events <view|full|times> <номер>",
                    "pnlibrary.events.view",
                    "Карточка записи: полный текст, стек ошибки или все повторы."),
                GuideEntry.of(
                    "/pn debug [all|плагин] [--full|--config|--logs] [--local]",
                    "pnlibrary.debug",
                    "Собрать зашифрованный отчёт для поддержки. --config добавляет конфигурации, --logs журнал, " +
                        "--full всё сразу, --local не отправлять."),
                GuideEntry.of(
                    "/pn stats",
                    "pnlibrary.stats",
                    "Сводка: задачи, события, ошибки и предупреждения за сутки и прошлые срезы."),
                GuideEntry.of(
                    "/pn support",
                    "pnlibrary.updates.view",
                    "Ссылка на поддержку pnFolder.")
                    .orAdmin()));
    }
}
