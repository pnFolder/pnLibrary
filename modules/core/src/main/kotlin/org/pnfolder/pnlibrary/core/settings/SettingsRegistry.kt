package org.pnfolder.pnlibrary.core.settings

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.time.Duration
import java.time.ZoneId
import kotlin.reflect.KProperty1

/**
 * The one list of every setting of the library: key, type, default, limits, description and when a change works.
 *
 * Nothing about the settings is described anywhere else; the command, the menu and the documentation read this list.
 * A test checks that each property of [LibrarySettings] appears here exactly once.
 */
internal object SettingsRegistry {
    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val DAY = 24 * 60 * MINUTE

    val all: List<SettingSpec> = listOf(
        switch(
            "privacy", "Приватность отчёта", LibrarySettings::privacy, SettingApply.RESTART,
            "Скрывать ли в диагностическом отчёте личные данные игроков: имена, UUID, адреса, пинг и названия текущих серверов.",
        ) { s, v -> s.copy(privacy = v) },
        number(
            "history-retention-days", "Хранение истории", LibrarySettings::historyRetentionDays, SettingApply.RESTART, 30, 365,
            "Сколько дней хранить историю диагностики.",
        ) { s, v -> s.copy(historyRetentionDays = v) },
        switch(
            "journal-foreign-plugins", "Журнал: чужие плагины", LibrarySettings::journalForeignPlugins, SettingApply.RESTART,
            "Писать ли в журнал предупреждения и ошибки плагинов, которые не подключены к pnLibrary.",
        ) { s, v -> s.copy(journalForeignPlugins = v) },
        zone(
            "time-zone", "Часовой пояс", LibrarySettings::timeZone, SettingApply.LIVE,
            "Часовой пояс для всех времён, которые показывает pnLibrary. Хранится время всегда в UTC, смена пояса ничего не портит.",
            listOf("UTC", "Europe/Moscow"),
        ) { s, v -> s.copy(timeZone = v) },
        text(
            "no-permission-message", "Текст без права", LibrarySettings::noPermissionMessage, SettingApply.RESTART, 256,
            "Что видит игрок без права на команду pnLibrary или другого плагина, у которого нет собственного сообщения.",
        ) { s, v -> s.copy(noPermissionMessage = v) },
        switch(
            "downloads.enabled", "Загрузки", LibrarySettings::downloadsEnabled, SettingApply.LIVE,
            "Система загрузок. Выключена: все запросы плагинов на загрузку файлов отклоняются.",
        ) { s, v -> s.copy(downloadsEnabled = v) },
        switch(
            "downloads.automatic", "Автозагрузка", LibrarySettings::downloadsAutomatic, SettingApply.LIVE,
            "Разрешить загрузки, которые плагин запускает сам, без команды администратора (в том числе недостающие плагины).",
        ) { s, v -> s.copy(downloadsAutomatic = v) },
        switch(
            "downloads.destinations.data-folder", "Загрузки в папку плагина", LibrarySettings::downloadsToDataFolder, SettingApply.LIVE,
            "Разрешить сохранять скачанные файлы в папку данных плагина, который их запросил.",
        ) { s, v -> s.copy(downloadsToDataFolder = v) },
        switch(
            "downloads.destinations.cache", "Загрузки в кэш", LibrarySettings::downloadsToCache, SettingApply.LIVE,
            "Разрешить сохранять скачанные файлы в общий кэш pnLibrary.",
        ) { s, v -> s.copy(downloadsToCache = v) },
        time(
            "updates.check-interval", "Проверка обновлений", LibrarySettings::updateCheckInterval, SettingApply.RESTART, 15 * MINUTE, 7 * DAY,
            "Как часто опрашивать каталоги релизов о новых версиях; первая проверка идёт при запуске.",
            listOf("6h", "30m"),
        ) { s, v -> s.copy(updateCheckInterval = v) },
        time(
            "updates.connect-timeout", "Ожидание соединения", LibrarySettings::updateConnectTimeout, SettingApply.RESTART, SECOND, 2 * MINUTE,
            "Сколько ждать соединения при скачивании файла обновления.",
            listOf("10s"),
        ) { s, v -> s.copy(updateConnectTimeout = v) },
        time(
            "updates.read-timeout", "Ожидание данных", LibrarySettings::updateReadTimeout, SettingApply.RESTART, 5 * SECOND, 10 * MINUTE,
            "Сколько ждать данных от молчащего сервера при скачивании файла обновления.",
            listOf("30s", "2m"),
        ) { s, v -> s.copy(updateReadTimeout = v) },
    )

    private val byKey: Map<String, SettingSpec> = all.associateBy { it.key }

    /** The setting named [key], or `null`. */
    operator fun get(key: String): SettingSpec? = byKey[key]

    private fun switch(
        key: String, title: String, property: KProperty1<LibrarySettings, Boolean>, applies: SettingApply, description: String,
        write: (LibrarySettings, Boolean) -> LibrarySettings,
    ) = SettingSpec(
        key, title, property.name, SettingKind.SWITCH, description, applies,
        read = { property.get(it) }, write = { s, v -> write(s, v as Boolean) },
        parse = SettingTexts::switch, show = { it.toString() },
    )

    private fun number(
        key: String, title: String, property: KProperty1<LibrarySettings, Int>, applies: SettingApply, min: Long, max: Long, description: String,
        write: (LibrarySettings, Int) -> LibrarySettings,
    ) = SettingSpec(
        key, title, property.name, SettingKind.NUMBER, description, applies, min, max,
        read = { property.get(it) }, write = { s, v -> write(s, v as Int) },
        parse = { SettingTexts.number(it, min, max) }, show = { it.toString() },
    )

    private fun text(
        key: String, title: String, property: KProperty1<LibrarySettings, String>, applies: SettingApply, maxLength: Long, description: String,
        write: (LibrarySettings, String) -> LibrarySettings,
    ) = SettingSpec(
        key, title, property.name, SettingKind.TEXT, description, applies, max = maxLength,
        read = { property.get(it) }, write = { s, v -> write(s, v as String) },
        parse = {
            val value = it.trim()
            if (value.isEmpty() || value.length > maxLength) throw SettingException("Текст должен быть от 1 до $maxLength символов.")
            value
        },
        show = { it.toString() },
    )

    private fun zone(
        key: String, title: String, property: KProperty1<LibrarySettings, String>, applies: SettingApply, description: String, examples: List<String>,
        write: (LibrarySettings, String) -> LibrarySettings,
    ) = SettingSpec(
        key, title, property.name, SettingKind.TEXT, description, applies, examples = examples,
        read = { property.get(it) }, write = { s, v -> write(s, v as String) },
        parse = {
            val value = it.trim()
            if (runCatchingNonFatal { ZoneId.of(value) }.isFailure) {
                throw SettingException("Неизвестный часовой пояс «$value». Примеры: UTC, Europe/Moscow.")
            }
            value
        },
        show = { it.toString() },
    )

    private fun time(
        key: String, title: String, property: KProperty1<LibrarySettings, Duration>, applies: SettingApply, min: Long, max: Long, description: String,
        examples: List<String>, write: (LibrarySettings, Duration) -> LibrarySettings,
    ) = SettingSpec(
        key, title, property.name, SettingKind.TIME, description, applies, min, max, examples,
        read = { property.get(it) }, write = { s, v -> write(s, v as Duration) },
        parse = { SettingTexts.duration(it, min, max) }, show = { SettingTexts.duration(it as Duration) },
    )
}
