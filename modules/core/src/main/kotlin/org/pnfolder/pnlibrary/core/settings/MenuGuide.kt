package org.pnfolder.pnlibrary.core.settings

internal val menuGuide = GuideSection(
    "Меню и валюты",
    listOf(
        GuideEntry(
            "/pn gui",
            "pnlibrary.updates.view",
            "Меню в инвентаре: плагины, обновления, история, ошибки, настройки.",
            orAdmin = true,
            scope = GuideScope.GAME_SERVER,
            note = "Только игрок: в консоли нет инвентаря.",
        ),
        GuideEntry("/pn money list", "pnlibrary.currency.list", "Список валют."),
        GuideEntry(
            "/pn money <валюта> <balance|history|add|take|set|reset|pay> …",
            "pnlibrary.currency.<валюта>.<действие>",
            "Баланс, история, начисление, списание, установка, обнуление и перевод игроку.",
            scope = GuideScope.GAME_SERVER,
            note = "Право своё для каждой валюты и действия; плагин валюты может задать другое начало через permissionPrefix.",
        ),
        GuideEntry(
            "/pn money <confirm|cancel> <код>",
            null,
            "Подтвердить или отменить опасную операцию с валютой.",
            scope = GuideScope.GAME_SERVER,
            note = "Только консоль.",
        ),
    ),
)
