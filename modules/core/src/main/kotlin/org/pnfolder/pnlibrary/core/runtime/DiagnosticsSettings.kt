package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigNewLine
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal

/**
 * Content of `plugins/pnLibrary/config.yml`: only what an administrator has a reason to change.
 *
 * Everything else (what goes into a report, how long reports and history are kept, how a report is sent, the wording of
 * the standard command answers) is fixed by the defaults of [PnLibraryConfig], so there is nothing to get wrong.
 */
@ConfigComment(
    "pnLibrary: настройки (config.yml)",
    "",
    "Диагностический отчёт собирает сведения о сервере и плагинах, чтобы упростить поддержку.",
    "Отчёт всегда сохраняется на сервере и отправляется в службу поддержки только в зашифрованном виде.",
    "В отчёт всегда входят конфигурации плагинов (секретные значения скрываются) и последние записи журнала.",
    "",
    "Команды и права (все права выдаются операторам по умолчанию; их назначают в LuckPerms или другом менеджере прав):",
    "  /pn                 pnlibrary.admin               управление библиотекой, меню /pn gui",
    "  /pn status|check    pnlibrary.updates.view        состояние обновлений и проверка",
    "  /pn updates ...     pnlibrary.updates.view        каталог плагинов и история обновлений",
    "  /pn updates set     pnlibrary.updates.settings    канал, автозагрузка и пауза плагина; set all auto on|off",
    "  /pn update ...      pnlibrary.updates.download    скачать и проверить обновление",
    "  /pn update confirm  pnlibrary.updates.download    подтвердить чувствительное действие",
    "  /pn update restart  pnlibrary.updates.restart     перезапуск после подготовки обновления",
    "  /pn update rollback pnlibrary.updates.rollback    резервные копии и откат",
    "  /pn events          pnlibrary.events.view         журнал событий и ошибок",
    "  /pn bus             pnlibrary.events.bus          события плагинов: слушатели, вызовы",
    "  /pn tasks           pnlibrary.tasks               задачи плагинов, отмена",
    "  /pn stats           pnlibrary.stats               сводная статистика",
    "  /pn placeholders    pnlibrary.placeholders        плейсхолдеры",
    "  /pn debug           pnlibrary.debug               зашифрованный отчёт для поддержки",
    "  /pn groups          pnlibrary.groups              группы серверов сети",
    "  /pn servers         pnlibrary.servers             серверы сети и удалённые действия",
    "  /pn money list      pnlibrary.currency.list       список валют",
    "  уведомления         pnlibrary.updates.notify      сообщения об обновлениях в чат при входе и по таймеру",
    "  pnlibrary.updates.install  разрешать установку новых компонентов",
    "",
    "pnLibrary: settings (config.yml)",
    "",
    "The diagnostic report collects server and plugin details to make support easier.",
    "It is always saved on the server and sent to support only in encrypted form.",
    "It always contains plugin configurations (secret values are hidden) and the latest log entries.",
    "",
    "Commands and permissions (all are granted to operators by default; assign them in LuckPerms or another permission plugin):",
    "  /pn                 pnlibrary.admin               manage the library, the /pn gui menu",
    "  /pn status|check    pnlibrary.updates.view        update state and check",
    "  /pn updates ...     pnlibrary.updates.view        plugin catalog and update history",
    "  /pn updates set     pnlibrary.updates.settings    channel, automatic download and pause of a plugin; set all auto on|off",
    "  /pn update ...      pnlibrary.updates.download    download and verify an update",
    "  /pn update confirm  pnlibrary.updates.download    confirm a sensitive action",
    "  /pn update restart  pnlibrary.updates.restart     restart after an update is prepared",
    "  /pn update rollback pnlibrary.updates.rollback    backups and rollback",
    "  /pn events          pnlibrary.events.view         event and error journal",
    "  /pn bus             pnlibrary.events.bus          plugin events: listeners, calls",
    "  /pn tasks           pnlibrary.tasks               plugin tasks, cancelling",
    "  /pn stats           pnlibrary.stats               summary statistics",
    "  /pn placeholders    pnlibrary.placeholders        placeholders",
    "  /pn debug           pnlibrary.debug               encrypted support report",
    "  /pn groups          pnlibrary.groups              server groups of the network",
    "  /pn servers         pnlibrary.servers             network servers and remote actions",
    "  /pn money list      pnlibrary.currency.list       list of currencies",
    "  notifications       pnlibrary.updates.notify      update messages in chat on join and on a timer",
    "  pnlibrary.updates.install  allow installing new components",
)
internal class DiagnosticsSettings {
    @field:ConfigComment(
        "=== Приватность ===",
        "Скрывать ли в отчёте личные данные игроков: имена, UUID, адреса, пинг и названия текущих серверов.",
        "По умолчанию false: данные показываются, так проблему разобрать проще. Поставьте true, чтобы скрыть их.",
    )
    var privacy: Boolean = false

    @field:ConfigComment(
        "Значения в файлах настроек плагинов, которые никогда не попадут в отчёт.",
        "Пароли, токены и ключи pnLibrary скрывает сама. Здесь перечисляют то, что скрыть нужно дополнительно.",
        "",
        "Как заполнять. Допустим, у плагина есть файл plugins/pnExample/config.yml:",
        "",
        "    database:",
        "      host: localhost",
        "      password: qwerty123        <- не должен попасть в отчёт",
        "    discord:",
        "      webhook-url: https://...   <- и это тоже",
        "    messages:",
        "      welcome: Привет            <- а это можно показывать",
        "",
        "Тогда ниже пишем путь к каждому значению: раздел, точка, ключ (от корня файла).",
        "Путь без названия файла действует на файлы ВСЕХ плагинов. Чтобы скрыть значение только в одном файле",
        "одного плагина, впереди пишут папка плагина, файл и двоеточие:",
        "",
        "excluded-paths:",
        "  - database.password                          # во всех файлах всех плагинов",
        "  - pnExample/config.yml:discord.webhook-url   # только в этом файле этого плагина",
        "  - pnShop/messages.yml:database               # весь раздел database, только в этом файле",
        "",
        "Если указать только раздел (например, database), скроется весь раздел целиком.",
        "Плагин может сам пометить свои поля аннотацией @ConfigSecret: тогда они скрыты без записи здесь.",
    )
    var excludedPaths: List<String> = emptyList()

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Хранение ===",
        "Сколько дней хранить историю диагностики (от 30 до 365).",
    )
    @field:ConfigRange(min = 30.0, max = 365.0)
    var historyRetentionDays: Int = 30

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Журнал событий ===",
        "Писать ли в журнал предупреждения и ошибки плагинов, которые не подключены к pnLibrary.",
        "По умолчанию выключено: в журнале только pnLibrary и плагины, которые пишут через неё.",
    )
    var journalForeignPlugins: Boolean = false

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Время ===",
        "Часовой пояс для всех времён, которые показывает pnLibrary (журнал, обновления).",
        "Хранится время всегда в UTC, поэтому смена пояса ничего не портит. Примеры: UTC, Europe/Moscow.",
        "При создании файла подставляется часовой пояс сервера.",
    )
    var timeZone: String = java.time.ZoneId.systemDefault().id

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Сообщения ===",
        "Что видит игрок, если у него нет права на команду pnLibrary или другого плагина,",
        "у которого нет собственного сообщения (до 256 символов).",
    )
    var noPermissionMessage: String = "Недостаточно прав для этой команды."

    fun toConfig(): PnLibraryConfig = PnLibraryConfig(
        historyRetentionDays = historyRetentionDays,
        excludedPaths = excludedPaths.toList(),
        privacy = privacy,
        noPermissionMessage = noPermissionMessage,
        journalForeignPlugins = journalForeignPlugins,
        timeZone = timeZone.trim().takeIf { runCatchingNonFatal { java.time.ZoneId.of(it) }.isSuccess }
            ?: java.time.ZoneId.systemDefault().id,
    )
}
