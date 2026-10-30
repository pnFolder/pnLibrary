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
)
internal class DiagnosticsSettings {
    @field:ConfigComment(
        "=== Приватность ===",
        "Не включать в отчёт личные данные игроков: имена, UUID, адреса, пинг и названия текущих серверов.",
        "Ставьте false, только если эти данные нужны для разбора проблемы.",
    )
    var privacy: Boolean = true

    @field:ConfigComment(
        "Значения в конфигурациях плагинов, которые никогда не попадут в отчёт. Путь пишется через точку.",
        "Пример: чтобы скрыть пароль базы данных из database.yml, где он лежит в разделе database, ключ password:",
        "excluded-paths:",
        "  - database.password",
        "  - discord.webhook-url",
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

    @field:ConfigComment(
        "Показывать ли администратору при входе краткую сводку: что сломалось недавно, при запуске, только что",
        "или повторяется. Если всё в порядке, сообщение не показывается. Нужно право pnlibrary.events.view или OP.",
    )
    var journalJoinNotice: Boolean = true

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

    /** Converts the file model to the immutable runtime policy, which validates the remaining rules. */
    fun toConfig(): PnLibraryConfig = PnLibraryConfig(
        historyRetentionDays = historyRetentionDays,
        excludedPaths = excludedPaths.toList(),
        privacy = privacy,
        noPermissionMessage = noPermissionMessage,
        journalForeignPlugins = journalForeignPlugins,
        journalJoinNotice = journalJoinNotice,
        timeZone = timeZone.trim().takeIf { runCatchingNonFatal { java.time.ZoneId.of(it) }.isSuccess }
            ?: java.time.ZoneId.systemDefault().id,
    )
}
