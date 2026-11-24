package org.pnfolder.pnlibrary.core.settings

internal val settingsGuide = GuideSection(
    "Настройки и конфигурации",
    listOf(
        GuideEntry("/pn settings [list] [страница]", "pnlibrary.settings", "Все настройки библиотеки со значениями; подробнее в файле Настройки.txt."),
        GuideEntry("/pn settings get <ключ>", "pnlibrary.settings", "Одна настройка: значение, границы, когда заработает."),
        GuideEntry("/pn settings set <ключ> <значение>", "pnlibrary.settings", "Изменить настройку; значение сохраняется в data/settings.json."),
        GuideEntry("/pn settings reset <ключ>", "pnlibrary.settings", "Вернуть настройку к значению по умолчанию."),
        GuideEntry(
            "/pn settings pick <ключ> [часть названия]",
            "pnlibrary.settings",
            "Открыть в меню список значений настройки (часовой пояс: time-zone), только подходящие под часть названия; " +
                "new york находит America/New_York.",
            scope = GuideScope.GAME_SERVER,
            note = "Только для игрока.",
        ),
        GuideEntry("/pn configs [страница]", "pnlibrary.configs", "Файлы настроек всех плагинов: класс и состояние. Значений из файлов не показывает."),
        GuideEntry("/pn configs info <файл>", "pnlibrary.configs", "Один файл: где лежит, загружен ли, что пропущено."),
        GuideEntry("/pn configs reload <файл>", "pnlibrary.configs", "Прочитать файл заново; при ошибке остаётся старое значение."),
        GuideEntry("/pn configs validate <файл>", "pnlibrary.configs", "Проверить загруженное значение, ничего не записывая."),
        GuideEntry("/pn configs settings <файл>", "pnlibrary.configs", "Настройки файла, которые можно менять в игре."),
        GuideEntry("/pn configs types <тип>", "pnlibrary.configs", "Слова, которые понимает составной тип, и плагин, добавивший каждое."),
        GuideEntry(
            "/pn configs edit <файл> <путь> <значение>",
            "pnlibrary.configs.edit",
            "Изменить одну такую настройку и сохранить файл.",
            note = "Нужно и право pnlibrary.configs.",
        ),
    ),
)
