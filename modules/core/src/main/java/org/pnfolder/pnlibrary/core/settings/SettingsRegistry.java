package org.pnfolder.pnlibrary.core.settings;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The one list of every setting of the library: key, type, default, limits, description and when a change works.
 *
 * <p>Nothing about the settings is described anywhere else; the command, the menu and the documentation read this
 * list. A test checks that each property of {@link LibrarySettings} appears here exactly once.
 */
public final class SettingsRegistry {
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long DAY = 24 * 60 * MINUTE;

    private static final List<SettingSpec> ALL = Collections.unmodifiableList(Arrays.asList(
        flag("privacy", "Приватность отчёта", "privacy", SettingApply.RESTART,
            "Скрывать ли в диагностическом отчёте личные данные игроков: имена, UUID, адреса, пинг и названия текущих серверов.",
            LibrarySettings::getPrivacy, LibrarySettings::withPrivacy),
        number("history-retention-days", "Хранение истории", "historyRetentionDays", SettingApply.RESTART, 30, 365,
            "Сколько дней хранить историю диагностики.",
            LibrarySettings::getHistoryRetentionDays, LibrarySettings::withHistoryRetentionDays),
        flag("journal-foreign-plugins", "Журнал: чужие плагины", "journalForeignPlugins", SettingApply.RESTART,
            "Писать ли в журнал предупреждения и ошибки плагинов, которые не подключены к pnLibrary.",
            LibrarySettings::getJournalForeignPlugins, LibrarySettings::withJournalForeignPlugins),
        zone("time-zone", "Часовой пояс", "timeZone", SettingApply.LIVE,
            "Часовой пояс для всех времён, которые показывает pnLibrary. "
                + "Хранится время всегда в UTC, смена пояса ничего не портит.",
            Arrays.asList("UTC", "Europe/Moscow"), LibrarySettings::getTimeZone, LibrarySettings::withTimeZone),
        text("no-permission-message", "Текст без права", "noPermissionMessage", SettingApply.RESTART, 256,
            "Что видит игрок без права на команду pnLibrary или другого плагина, у которого нет собственного сообщения.",
            LibrarySettings::getNoPermissionMessage, LibrarySettings::withNoPermissionMessage),
        flag("downloads.enabled", "Загрузки", "downloadsEnabled", SettingApply.LIVE,
            "Система загрузок. Выключена: все запросы плагинов на загрузку файлов отклоняются.",
            LibrarySettings::getDownloadsEnabled, LibrarySettings::withDownloadsEnabled),
        flag("downloads.automatic", "Автозагрузка", "downloadsAutomatic", SettingApply.LIVE,
            "Разрешить загрузки, которые плагин запускает сам, без команды администратора "
                + "(в том числе недостающие плагины).",
            LibrarySettings::getDownloadsAutomatic, LibrarySettings::withDownloadsAutomatic),
        flag("downloads.destinations.data-folder", "Загрузки в папку плагина", "downloadsToDataFolder",
            SettingApply.LIVE,
            "Разрешить сохранять скачанные файлы в папку данных плагина, который их запросил.",
            LibrarySettings::getDownloadsToDataFolder, LibrarySettings::withDownloadsToDataFolder),
        flag("downloads.destinations.cache", "Загрузки в кэш", "downloadsToCache", SettingApply.LIVE,
            "Разрешить сохранять скачанные файлы в общий кэш pnLibrary.",
            LibrarySettings::getDownloadsToCache, LibrarySettings::withDownloadsToCache),
        time("updates.check-interval", "Проверка обновлений", "updateCheckInterval", SettingApply.RESTART,
            15 * MINUTE, 7 * DAY,
            "Как часто опрашивать каталоги релизов о новых версиях; первая проверка идёт при запуске.",
            Arrays.asList("6h", "30m"),
            LibrarySettings::getUpdateCheckInterval, LibrarySettings::withUpdateCheckInterval),
        time("updates.connect-timeout", "Ожидание соединения", "updateConnectTimeout", SettingApply.RESTART,
            SECOND, 2 * MINUTE,
            "Сколько ждать соединения при скачивании файла обновления.",
            Arrays.asList("10s"),
            LibrarySettings::getUpdateConnectTimeout, LibrarySettings::withUpdateConnectTimeout),
        time("updates.read-timeout", "Ожидание данных", "updateReadTimeout", SettingApply.RESTART,
            5 * SECOND, 10 * MINUTE,
            "Сколько ждать данных от молчащего сервера при скачивании файла обновления.",
            Arrays.asList("30s", "2m"),
            LibrarySettings::getUpdateReadTimeout, LibrarySettings::withUpdateReadTimeout)));

    private static final Map<String, SettingSpec> BY_KEY = index();

    private SettingsRegistry() {
    }

    /** Every setting in the order the menu and the command list them. */
    public static @NotNull List<SettingSpec> getAll() {
        return ALL;
    }

    /** The setting named {@code key}, or null. */
    public static @Nullable SettingSpec get(@NotNull String key) {
        return BY_KEY.get(key);
    }

    private static Map<String, SettingSpec> index() {
        Map<String, SettingSpec> byKey = new LinkedHashMap<String, SettingSpec>();
        for (SettingSpec spec : ALL) {
            byKey.put(spec.getKey(), spec);
        }
        return byKey;
    }

    private static SettingSpec flag(String key, String title, String field, SettingApply applies, String description,
                                    Function<LibrarySettings, Boolean> read,
                                    BiFunction<LibrarySettings, Boolean, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.SWITCH, description, applies, null, null,
            Collections.<String>emptyList(), null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (Boolean) value),
            SettingTexts::toSwitch, String::valueOf);
    }

    private static SettingSpec number(String key, String title, String field, SettingApply applies, long min,
                                      long max, String description, Function<LibrarySettings, Integer> read,
                                      BiFunction<LibrarySettings, Integer, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.NUMBER, description, applies, min, max,
            Collections.<String>emptyList(), null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (Integer) value),
            text -> SettingTexts.number(text, min, max), String::valueOf);
    }

    private static SettingSpec text(String key, String title, String field, SettingApply applies, long maxLength,
                                    String description, Function<LibrarySettings, String> read,
                                    BiFunction<LibrarySettings, String, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.TEXT, description, applies, null, maxLength,
            Collections.<String>emptyList(), null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (String) value),
            typed -> {
                String value = typed.trim();
                if (value.isEmpty() || value.length() > maxLength) {
                    throw new SettingException("Текст должен быть от 1 до " + maxLength + " символов.");
                }
                return value;
            }, String::valueOf);
    }

    private static SettingSpec zone(String key, String title, String field, SettingApply applies,
                                    String description, List<String> examples,
                                    Function<LibrarySettings, String> read,
                                    BiFunction<LibrarySettings, String, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.CHOICE, description, applies, null, null, examples,
            ChoiceSource.ZONES,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (String) value),
            typed -> {
                String value = typed.trim();
                try {
                    ZoneId.of(value);
                } catch (RuntimeException error) {
                    throw new SettingException("Неизвестный часовой пояс «" + value + "». Примеры: UTC, Europe/Moscow.");
                }
                return value;
            }, String::valueOf);
    }

    private static SettingSpec time(String key, String title, String field, SettingApply applies, long min, long max,
                                    String description, List<String> examples,
                                    Function<LibrarySettings, Duration> read,
                                    BiFunction<LibrarySettings, Duration, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.TIME, description, applies, min, max, examples, null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (Duration) value),
            typed -> SettingTexts.duration(typed, min, max), value -> SettingTexts.duration((Duration) value));
    }
}
