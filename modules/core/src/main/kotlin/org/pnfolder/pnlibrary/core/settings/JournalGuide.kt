package org.pnfolder.pnlibrary.core.settings

internal val journalGuide = GuideSection(
    "Журнал и диагностика",
    listOf(
        GuideEntry(
            "/pn events [info|warning|error|all] [страница]",
            "pnlibrary.events.view",
            "Журнал событий и ошибок, повторы свёрнуты в одну строку.",
            note = "Это же право показывает сводку ошибок при входе на сервер.",
        ),
        GuideEntry("/pn events <view|full|times> <номер>", "pnlibrary.events.view", "Карточка записи: полный текст, стек ошибки или все повторы."),
        GuideEntry(
            "/pn debug [all|плагин] [--full|--config|--logs] [--local]",
            "pnlibrary.debug",
            "Собрать зашифрованный отчёт для поддержки. --config добавляет конфигурации, --logs журнал, --full всё сразу, --local не отправлять.",
        ),
        GuideEntry("/pn stats", "pnlibrary.stats", "Сводка: задачи, события, ошибки и предупреждения за сутки и прошлые срезы."),
        GuideEntry("/pn support", "pnlibrary.updates.view", "Ссылка на поддержку pnFolder.", orAdmin = true),
    ),
)
