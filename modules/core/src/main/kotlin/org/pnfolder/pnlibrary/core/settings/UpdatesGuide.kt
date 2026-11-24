package org.pnfolder.pnlibrary.core.settings

internal val updatesGuide = GuideSection(
    "Обновления",
    listOf(
        GuideEntry(
            "/pn", GUIDE_ADMIN, "Список плагинов и их обновлений. Без этого права команда покажет только то, что разрешено вам.",
            note = "Сама команда /pn доступна всем.",
        ),
        GuideEntry("/pn status [плагин]", "pnlibrary.updates.view", "Состояние обновлений: версии, канал, что готово к установке.", orAdmin = true),
        GuideEntry("/pn check", "pnlibrary.updates.view", "Проверить прямо сейчас, есть ли новые версии плагинов.", orAdmin = true),
        GuideEntry(
            "/pn updates [list|available] [страница]", "pnlibrary.updates.view",
            "Каталог плагинов; available показывает только те, что можно обновить.", orAdmin = true,
        ),
        GuideEntry("/pn updates info <плагин>", "pnlibrary.updates.view", "Карточка плагина: версии, канал, состояние.", orAdmin = true),
        GuideEntry(
            "/pn updates settings <плагин>", "pnlibrary.updates.view", "Настройки обновления плагина: канал, автозагрузка, пауза.",
            orAdmin = true,
        ),
        GuideEntry("/pn updates history [страница]", "pnlibrary.updates.view", "История обновлений всех плагинов.", orAdmin = true),
        GuideEntry("/pn updates history plugin <плагин> [страница]", "pnlibrary.updates.view", "История обновлений одного плагина.", orAdmin = true),
        GuideEntry(
            "/pn updates set <плагин> channel <dev|default>",
            "pnlibrary.updates.settings",
            "Выбрать канал выпусков; default возвращает выбор плагина.",
            note = "Сейчас публикуется только канал dev.",
        ),
        GuideEntry(
            "/pn updates set <плагин> auto <on|off>",
            "pnlibrary.updates.settings",
            "Включить или выключить автозагрузку обновлений одного плагина.",
        ),
        GuideEntry(
            "/pn updates set <плагин> hold <срок|off>",
            "pnlibrary.updates.settings",
            "Поставить обновления плагина на паузу от 1m до 7d (например 3d) или снять паузу.",
        ),
        GuideEntry("/pn updates set all auto <on|off>", "pnlibrary.updates.settings", "Включить или выключить автозагрузку сразу для всех плагинов."),
        GuideEntry(
            "/pn updates backups [plugin <плагин>] [since <7d|дата>] [sort <new|old|plugin>] [страница]",
            "pnlibrary.updates.rollback",
            "Резервные наборы, сохранённые перед обновлениями; можно отфильтровать и отсортировать.",
        ),
        GuideEntry(
            "/pn updates backups restore <набор> <плагин> [confirm] <база>",
            "pnlibrary.updates.settings",
            "Вернуть сохранённую базу данных плагина; без confirm только предупредит.",
            note = "Нужно и право pnlibrary.updates.rollback.",
        ),
        GuideEntry(
            "/pn updates backups files <набор> <плагин>",
            "pnlibrary.updates.settings",
            "Выложить сохранённые файлы плагина в папку, чтобы взять их руками.",
            note = "Нужно и право pnlibrary.updates.rollback.",
        ),
        GuideEntry(
            "/pn updates backups delete <набор>",
            "pnlibrary.updates.settings",
            "Удалить резервный набор.",
            note = "Нужно и право pnlibrary.updates.rollback.",
        ),
        GuideEntry(
            "/pn updates rollback <набор> [configs]",
            "pnlibrary.updates.rollback",
            "Откатиться к резервному набору (с configs вместе с конфигурациями); спросит подтверждение.",
        ),
        GuideEntry(
            "/pn update <плагин>",
            "pnlibrary.updates.download",
            "Найти, скачать и проверить обновление плагина; оно применится после перезапуска.",
            orAdmin = true,
        ),
        GuideEntry(
            "/pn update confirm <код>",
            "pnlibrary.updates.download",
            "Подтвердить действие по коду из сообщения (откат, перезапуск).",
            orAdmin = true,
            note = "Само действие требует своего права: rollback или restart.",
        ),
        GuideEntry(
            "/pn update rollback",
            "pnlibrary.updates.rollback",
            "Откатить весь подготовленный план обновления; спросит подтверждение.",
            note = "Нужно и право на /pn update (updates.download или admin).",
        ),
        GuideEntry(
            "/pn update restart",
            "pnlibrary.updates.restart",
            "Перезапустить сервер, чтобы применить подготовленные обновления: сразу, через 5 минут или когда все выйдут.",
            scope = GuideScope.GAME_SERVER,
            note = "Нужно и право на /pn update. На прокси перезапускайте прокси как обычно.",
        ),
        GuideEntry(
            "/pn update restart cancel",
            "pnlibrary.updates.download",
            "Отменить запланированный перезапуск.",
            orAdmin = true,
            scope = GuideScope.GAME_SERVER,
        ),
        GuideEntry(
            "(уведомления об обновлениях)",
            "pnlibrary.updates.notify",
            "Получать сообщения об обновлениях при входе и по таймеру.",
            orAdmin = true,
        ),
        GuideEntry(
            "(установка новых компонентов)",
            "pnlibrary.updates.install",
            "Зарезервировано под установку новых компонентов.",
            note = "Сейчас ни одна команда это право не проверяет.",
        ),
    ),
)
