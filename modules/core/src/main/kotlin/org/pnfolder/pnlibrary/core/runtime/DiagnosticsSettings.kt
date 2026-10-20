package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigNewLine
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig

/**
 * Content of `plugins/pnLibrary/config.yml`: diagnostic reports, their privacy and storage.
 *
 * Only options an administrator may reasonably change are written to the file. The transport of an
 * uploaded report (encryption, services, keys) is fixed by [PnLibraryConfig] defaults.
 */
@ConfigComment(
    "pnLibrary: диагностика (config.yml)",
    "",
    "Диагностический отчёт собирает сведения о сервере и плагинах, чтобы упростить поддержку.",
    "Отчёт всегда сохраняется на сервере. Отправка во внешний сервис всегда идёт в зашифрованном виде.",
)
internal class DiagnosticsSettings {
    @field:ConfigComment(
        "=== Отправка отчёта ===",
        "Отправлять ли готовый отчёт в службу поддержки. false: отчёт остаётся только на сервере.",
    )
    var upload: Boolean = true

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Состав отчёта ===",
        "Не включать личные данные игроков: имена, UUID, адреса, пинг и названия текущих серверов.",
        "Ставьте false, только если эти данные нужны для разбора проблемы.",
    )
    var privacy: Boolean = true

    @field:ConfigComment("Прикладывать конфигурационные файлы плагинов. Секретные значения скрываются.")
    var configs: Boolean = true

    @field:ConfigComment("Прикладывать последние предупреждения и ошибки из журнала.")
    var logs: Boolean = true

    @field:ConfigComment("Сколько последних записей журнала прикладывать (от 1 до 2000).")
    @field:ConfigRange(min = 1.0, max = 2000.0)
    var logRecords: Int = 200

    @field:ConfigComment("Пути к значениям в конфигурациях (через точку, например database.password), которые не попадают в отчёт.")
    var excludedPaths: List<String> = emptyList()

    @field:ConfigComment("Регулярные выражения для имён ключей: значения совпавших ключей скрываются.")
    var secretKeyPatterns: List<String> = emptyList()

    @field:ConfigComment("Регулярные выражения для значений: совпавшие фрагменты скрываются.")
    var redactValuePatterns: List<String> = emptyList()

    @field:ConfigNewLine
    @field:ConfigComment("=== Хранение и ограничения ===", "Сколько дней хранить историю диагностики (от 1 до 365).")
    @field:ConfigRange(min = 1.0, max = 365.0)
    var historyRetentionDays: Int = 30

    @field:ConfigComment("Максимальный размер истории диагностики в байтах: от 1 до 256 МиБ (33554432 = 32 МиБ).")
    @field:ConfigRange(min = 1_048_576.0, max = 268_435_456.0)
    var historyMaxBytes: Int = 32 * 1024 * 1024

    @field:ConfigComment("Сколько последних отчётов хранить на сервере (от 1 до 1000).")
    @field:ConfigRange(min = 1.0, max = 1000.0)
    var keepReports: Int = 10

    @field:ConfigComment("Максимальный размер одного отчёта в байтах: от 64 КиБ до 64 МиБ (8388608 = 8 МиБ).")
    @field:ConfigRange(min = 65_536.0, max = 67_108_864.0)
    var maxReportBytes: Int = 8 * 1024 * 1024

    @field:ConfigComment("Через сколько дней удалять старые отчёты (от 0 до 3650). 0: не удалять по возрасту.")
    @field:ConfigRange(min = 0.0, max = 3650.0)
    var deleteAfterDays: Int = 90

    @field:ConfigComment("Минимальная пауза между запусками команды диагностики одним пользователем, в секундах (от 0 до 3600).")
    @field:ConfigRange(min = 0.0, max = 3600.0)
    var cooldownSeconds: Int = 10

    @field:ConfigNewLine
    @field:ConfigComment(
        "=== Журнал событий ===",
        "Сколько дней хранить информационные записи журнала (от 1 до 3650).",
    )
    @field:ConfigRange(min = 1.0, max = 3650.0)
    var journalInfoDays: Int = 7

    @field:ConfigComment("Сколько дней хранить предупреждения (от 1 до 3650).")
    @field:ConfigRange(min = 1.0, max = 3650.0)
    var journalWarningDays: Int = 30

    @field:ConfigComment("Сколько дней хранить ошибки (от 1 до 3650).")
    @field:ConfigRange(min = 1.0, max = 3650.0)
    var journalErrorDays: Int = 90

    @field:ConfigComment(
        "Писать ли в журнал предупреждения и ошибки плагинов, которые не подключены к pnLibrary.",
        "По умолчанию выключено: в журнале только pnLibrary и плагины, которые пишут через неё.",
    )
    var journalForeignPlugins: Boolean = false

    @field:ConfigComment(
        "Показывать ли администратору при входе краткую сводку: что сломалось недавно, при запуске, только что",
        "или повторяется. Если всё в порядке, сообщение не показывается. Нужно право pnlibrary.events.view или OP.",
    )
    var journalJoinNotice: Boolean = true

    @field:ConfigComment("За сколько последних часов считать события в этой сводке (от 1 до 720).")
    @field:ConfigRange(min = 1.0, max = 720.0)
    var journalJoinNoticeHours: Int = 24

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
        upload = upload,
        configs = configs,
        logs = logs,
        logRecords = logRecords,
        historyRetentionDays = historyRetentionDays,
        historyMaxBytes = historyMaxBytes,
        cooldownSeconds = cooldownSeconds,
        keepReports = keepReports,
        maxReportBytes = maxReportBytes,
        deleteAfterDays = deleteAfterDays,
        excludedPaths = excludedPaths.toList(),
        secretKeyPatterns = secretKeyPatterns.toList(),
        redactValuePatterns = redactValuePatterns.toList(),
        privacy = privacy,
        journalInfoDays = journalInfoDays,
        journalWarningDays = journalWarningDays,
        journalErrorDays = journalErrorDays,
        noPermissionMessage = noPermissionMessage,
        journalForeignPlugins = journalForeignPlugins,
        journalJoinNotice = journalJoinNotice,
        journalJoinNoticeHours = journalJoinNoticeHours,
        timeZone = timeZone.trim().takeIf { runCatching { java.time.ZoneId.of(it) }.isSuccess }
            ?: java.time.ZoneId.systemDefault().id,
    )
}
