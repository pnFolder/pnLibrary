package org.pnfolder.pnlibrary.core.settings

/** Where a command exists. */
internal enum class GuideScope(val note: String?) {
    EVERYWHERE(null),
    GAME_SERVER("Есть только на игровом сервере."),
    PROXY("Есть только на прокси (BungeeCord, Velocity)."),
}

/**
 * One line of the command guide.
 *
 * @param usage the command as it is typed, or the name of a right that has no command
 * @param permission the permission node, or `null` when the entry needs none
 * @param description what it does, one short sentence
 * @param orAdmin whether `pnlibrary.admin` opens the command too
 * @param scope where the command exists
 * @param note one more thing the administrator should know, or `null`
 */
internal class GuideEntry(
    val usage: String,
    val permission: String?,
    val description: String,
    val orAdmin: Boolean = false,
    val scope: GuideScope = GuideScope.EVERYWHERE,
    val note: String? = null,
) {
    /** Whether [permission] is a pattern such as `pnlibrary.currency.<валюта>.list`, not one node of plugin.yml. */
    val isPattern: Boolean get() = permission?.contains('<') == true
}

internal class GuideSection(val title: String, val entries: List<GuideEntry>)

/**
 * The one list of every `/pn` command and every permission of the library: the text of `Команды.txt` is made from it and
 * a test keeps it equal to the permissions of `plugin.yml`.
 *
 * Whoever adds a command or a permission adds it here; nothing else lists them.
 */
internal object LibraryCommandGuide {
    private const val ADMIN = "pnlibrary.admin"

    val sections: List<GuideSection> = listOf(
        GuideSection(
            "Обновления",
            listOf(
                GuideEntry(
                    "/pn", ADMIN, "Список плагинов и их обновлений. Без этого права команда покажет только то, что разрешено вам.",
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
        ),
        GuideSection(
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
        ),
        GuideSection(
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
                GuideEntry("/pn placeholders test <адрес> [uuid игрока]", "pnlibrary.placeholders", "Вычислить плейсхолдер вручную."),
                GuideEntry("/pn deps", "pnlibrary.deps", "Недостающие плагины: ссылки и код для установки."),
                GuideEntry("/pn deps install <код>", "pnlibrary.deps", "Скачать недостающие плагины по коду; потом перезапустите сервер."),
                GuideEntry("/pn deps auto <on|off>", "pnlibrary.deps", "Скачивать недостающие плагины самим или только по команде."),
            ),
        ),
        GuideSection(
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
        ),
        GuideSection(
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
        ),
        GuideSection(
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
        ),
    )

    /** How to read the list: the rules that hold for all entries. */
    val notes: List<String> = listOf(
        "Консоль выполняет все команды без прав.",
        "«Право: A или pnlibrary.admin» значит, что подойдёт любое из двух. Если в заметке сказано «нужно и право …», нужны оба.",
        "Подкоманды /pn updates … требуют ещё pnlibrary.updates.view или pnlibrary.admin, " +
            "подкоманды /pn update … требуют ещё pnlibrary.updates.download или pnlibrary.admin.",
        "На игровом сервере команда называется /pn (есть и /pnlib, /pnlibrary). " +
            "На прокси она называется /pnproxy (есть и /pnp), в этом файле она везде записана как /pn.",
    )

    /** Every entry of every section. */
    val entries: List<GuideEntry> = sections.flatMap(GuideSection::entries)

    /** Every permission node the guide names, patterns excluded, plus `pnlibrary.admin`. */
    val permissions: Set<String> = entries.filterNot(GuideEntry::isPattern).mapNotNull(GuideEntry::permission).toSet() + ADMIN

    /** The permissions that exist only on a proxy and so are not declared in the Bukkit plugin.yml. */
    val proxyOnly: Set<String> = entries.partition { it.scope == GuideScope.PROXY }
        .let { (proxy, others) -> proxy.mapNotNull(GuideEntry::permission).toSet() - others.mapNotNull(GuideEntry::permission).toSet() - ADMIN }

    /** The lines of the guide between the heading and the signature of `Команды.txt`. */
    fun lines(): List<String> = buildList {
        for (section in sections) {
            add("")
            add(section.title)
            add("-".repeat(section.title.length))
            for (entry in section.entries) {
                add("")
                add(entry.usage)
                add("    Право: ${rights(entry)}")
                add("    ${entry.description}")
                entry.scope.note?.let { add("    $it") }
                entry.note?.let { add("    $it") }
            }
        }
    }

    private fun rights(entry: GuideEntry): String {
        val permission = entry.permission ?: return "не нужно"
        return when {
            permission == ADMIN -> permission
            entry.orAdmin -> "$permission или $ADMIN"
            else -> permission
        }
    }
}
