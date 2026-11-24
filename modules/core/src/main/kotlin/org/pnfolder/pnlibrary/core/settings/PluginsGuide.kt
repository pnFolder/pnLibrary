package org.pnfolder.pnlibrary.core.settings

internal val pluginsGuide = GuideSection(
    "Плагины, задачи и события",
    listOf(
        GuideEntry(
            "/pn tasks [today|week|older|any] [all|active|failed|done] [страница]",
            "pnlibrary.tasks",
            "Задачи всех плагинов; по умолчанию сегодняшние.",
        ),
        GuideEntry("/pn tasks view <номер>", "pnlibrary.tasks", "Одна задача: для чего она, владелец, времена, последняя ошибка."),
        GuideEntry("/pn tasks run <номер>", "pnlibrary.tasks", "Запустить ждущую задачу сразу, если плагин не запрещает."),
        GuideEntry("/pn tasks cancel <номер>", "pnlibrary.tasks", "Остановить незавершённую задачу, если плагин не запрещает."),
        GuideEntry("/pn bus [all|listened|called|denied] [страница]", "pnlibrary.events.bus", "События плагинов со счётчиками слушателей и вызовов."),
        GuideEntry("/pn bus view <событие>", "pnlibrary.events.bus", "Одно событие: счётчики и слушатели по порядку вызова."),
        GuideEntry("/pn bus <listeners|slow|errors> [страница]", "pnlibrary.events.bus", "Все слушатели, самые медленные и те, что падали."),
        GuideEntry("/pn bus plugin <плагин> [страница]", "pnlibrary.events.bus", "Слушатели одного плагина со временем и сбоями."),
        GuideEntry("/pn bus top [calls|denied]", "pnlibrary.events.bus", "Десять самых вызываемых или самых отменяемых событий."),
        GuideEntry("/pn bus range [1h|24h|7d|30d]", "pnlibrary.events.bus", "Вызовы за час, сутки, неделю или месяц."),
        GuideEntry(
            "/pn bus history [day <дата>|of <событие>] [страница]",
            "pnlibrary.events.bus",
            "История по дням, по одному дню или по одному событию.",
        ),
        GuideEntry("/pn bus export [csv|json]", "pnlibrary.events.bus", "Записать счётчики в файл рядом с историей."),
        GuideEntry("/pn bus trace [off] <событие>", "pnlibrary.events.bus", "Включить или выключить запись вызовов события."),
        GuideEntry("/pn bus traces [событие]", "pnlibrary.events.bus", "Записанные вызовы: что было до и после каждого слушателя."),
        GuideEntry("/pn bus clear", "pnlibrary.events.bus", "Забыть записанные вызовы."),
        GuideEntry("/pn placeholders [страница]", "pnlibrary.placeholders", "Список плейсхолдеров: тип, формат, имена в PlaceholderAPI."),
        GuideEntry("/pn placeholders info <адрес>", "pnlibrary.placeholders", "Как писать плейсхолдер: владелец, видимость, все формы адреса."),
        GuideEntry("/pn placeholders test <адрес> [uuid игрока]", "pnlibrary.placeholders", "Вычислить плейсхолдер вручную."),
        GuideEntry("/pn actions [страница]", "pnlibrary.actions", "Именованные действия, требования и функции плагинов: владелец и видимость."),
        GuideEntry("/pn actions info <имя>", "pnlibrary.actions", "Как писать действие, требование или функцию: все формы с владельцем."),
        GuideEntry("/pn deps", "pnlibrary.deps", "Недостающие плагины: ссылки и код для установки."),
        GuideEntry("/pn deps install <код>", "pnlibrary.deps", "Скачать недостающие плагины по коду; потом перезапустите сервер."),
        GuideEntry("/pn deps auto <on|off>", "pnlibrary.deps", "Скачивать недостающие плагины самим или только по команде."),
    ),
)
