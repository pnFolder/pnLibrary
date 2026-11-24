package org.pnfolder.pnlibrary.core.settings

internal val networkGuide = GuideSection(
    "Сеть серверов",
    listOf(
        GuideEntry("/pn servers", "pnlibrary.servers", "Серверы сети: версия pnLibrary, игроки, плагины.", orAdmin = true),
        GuideEntry("/pn servers <сервер>", "pnlibrary.servers", "Один сервер: платформа и состояние обновления каждого плагина.", orAdmin = true),
        GuideEntry(
            "/pn servers <сервер> check",
            "pnlibrary.updates.view",
            "Попросить сервер проверить обновления.",
            note = "Нужно и право на /pn servers.",
        ),
        GuideEntry(
            "/pn servers <сервер> update <плагин>",
            "pnlibrary.updates.download",
            "Попросить сервер скачать обновление плагина.",
            note = "Нужно и право на /pn servers.",
        ),
        GuideEntry(
            "/pn servers <сервер> auto <плагин> <on|off>",
            "pnlibrary.updates.settings",
            "Включить или выключить автозагрузку плагина на сервере.",
            note = "Нужно и право на /pn servers.",
        ),
        GuideEntry("/pn groups", "pnlibrary.groups", "Группы серверов и сколько серверов в порядке.", orAdmin = true, scope = GuideScope.PROXY),
        GuideEntry(
            "/pn groups <info|delete> <группа>",
            "pnlibrary.groups",
            "Состав группы или удаление группы.",
            orAdmin = true,
            scope = GuideScope.PROXY,
        ),
        GuideEntry("/pn groups create <группа>", "pnlibrary.groups", "Создать пустую группу.", orAdmin = true, scope = GuideScope.PROXY),
        GuideEntry(
            "/pn groups <join|leave> <группа> <сервер>",
            "pnlibrary.groups",
            "Добавить сервер в группу или убрать.",
            orAdmin = true,
            scope = GuideScope.PROXY,
        ),
        GuideEntry(
            "/pn groups <require|drop> <группа> <плагин>",
            "pnlibrary.groups",
            "Плагин обязателен для группы или больше нет.",
            orAdmin = true,
            scope = GuideScope.PROXY,
        ),
        GuideEntry(
            "/pn groups <server|skip|extra|reset> <группа> <сервер> [плагин]",
            "pnlibrary.groups",
            "Чем один сервер отличается от группы: показать, не нужен плагин, нужен лишний, сбросить.",
            orAdmin = true,
            scope = GuideScope.PROXY,
        ),
        GuideEntry("/pn groups of <сервер>", "pnlibrary.groups", "В каких группах состоит сервер.", orAdmin = true, scope = GuideScope.PROXY),
        GuideEntry(
            "/pn groups check <группа>",
            "pnlibrary.updates.view",
            "Все серверы группы проверят обновления.",
            scope = GuideScope.PROXY,
            note = "Нужно и право на /pn groups.",
        ),
        GuideEntry(
            "/pn groups update <группа>",
            "pnlibrary.updates.download",
            "Все серверы группы скачают доступные обновления.",
            scope = GuideScope.PROXY,
            note = "Нужно и право на /pn groups.",
        ),
    ),
)
