package org.pnfolder.pnlibrary.core.guide;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class SettingsGuide {
    private SettingsGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Настройки и конфигурации",
            Arrays.asList(
                GuideEntry.of(
                    "/pn settings [list] [страница]",
                    "pnlibrary.settings",
                    "Все настройки библиотеки со значениями; подробнее в файле Настройки.txt."),
                GuideEntry.of(
                    "/pn settings get <ключ>",
                    "pnlibrary.settings",
                    "Одна настройка: значение, границы, когда заработает."),
                GuideEntry.of(
                    "/pn settings set <ключ> <значение>",
                    "pnlibrary.settings",
                    "Изменить настройку; значение сохраняется в data/settings.json."),
                GuideEntry.of(
                    "/pn settings reset <ключ>",
                    "pnlibrary.settings",
                    "Вернуть настройку к значению по умолчанию."),
                GuideEntry.of(
                    "/pn settings pick <ключ> [часть названия]",
                    "pnlibrary.settings",
                    "Открыть в меню список значений настройки (часовой пояс: time-zone), только подходящие под " +
                        "часть названия; new york находит America/New_York.")
                    .scope(GuideScope.GAME_SERVER)
                    .note("Только для игрока."),
                GuideEntry.of(
                    "/pn configs [страница]",
                    "pnlibrary.configs",
                    "Файлы настроек всех плагинов: класс и состояние. Значений из файлов не показывает."),
                GuideEntry.of(
                    "/pn configs info <файл>",
                    "pnlibrary.configs",
                    "Один файл: где лежит, загружен ли, что пропущено."),
                GuideEntry.of(
                    "/pn configs reload <файл>",
                    "pnlibrary.configs",
                    "Прочитать файл заново; при ошибке остаётся старое значение."),
                GuideEntry.of(
                    "/pn configs validate <файл>",
                    "pnlibrary.configs",
                    "Проверить загруженное значение, ничего не записывая."),
                GuideEntry.of(
                    "/pn configs settings <файл>",
                    "pnlibrary.configs",
                    "Настройки файла, которые можно менять в игре."),
                GuideEntry.of(
                    "/pn configs types <тип>",
                    "pnlibrary.configs",
                    "Слова, которые понимает составной тип, и плагин, добавивший каждое."),
                GuideEntry.of(
                    "/pn configs edit <файл> <путь> <значение>",
                    "pnlibrary.configs.edit",
                    "Изменить одну такую настройку и сохранить файл.")
                    .note("Нужно и право pnlibrary.configs.")));
    }
}
