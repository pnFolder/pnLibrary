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
        time("updates.check-interval", "Проверка обновлений", "updateCheckInterval", SettingApply.RESTART,
            15 * MINUTE, 7 * DAY,
            "Как часто опрашивать каталоги релизов о новых версиях; первая проверка идёт при запуске.",
            Arrays.asList("6h", "30m"),
            LibrarySettings::getUpdateCheckInterval, LibrarySettings::withUpdateCheckInterval),
        flag("updates.restart.auto", "Перезапускать самой", "restart.auto", SettingApply.LIVE,
            "Перезапустить сервер самой, когда игроков не больше updates.restart.max-players столько времени, "
                + "сколько задано в updates.restart.quiet-time. Не раньше чем через 10 минут после запуска "
                + "и не после отката. Прокси сами не перезапускаются.",
            settings -> settings.getRestart().getAuto(),
            (settings, value) -> settings.withRestart(settings.getRestart().withAuto(value))),
        number("updates.restart.max-players", "Игроков для перезапуска", "restart.max-players", SettingApply.LIVE,
            0, 200,
            "Сколько игроков ещё считается «мало»: для автоперезапуска и кнопки «когда игроков не больше N».",
            settings -> settings.getRestart().getMaxPlayers(),
            (settings, value) -> settings.withRestart(settings.getRestart().withMaxPlayers(value))),
        time("updates.restart.quiet-time", "Тишина перед перезапуском", "restart.quiet-time", SettingApply.LIVE,
            10 * SECOND, 60 * MINUTE,
            "Сколько времени подряд игроков должно быть не больше updates.restart.max-players, "
                + "чтобы сервер перезапустился.",
            Arrays.asList("2m", "30s"),
            settings -> settings.getRestart().getQuietTime(),
            (settings, value) -> settings.withRestart(settings.getRestart().withQuietTime(value)))));

    /** Settings that became constants in code; an old file may still carry them and that is not worth a warning. */
    private static final List<String> RETIRED = Collections.unmodifiableList(Arrays.asList(
        "downloads.destinations.data-folder", "downloads.destinations.cache",
        "updates.connect-timeout", "updates.read-timeout"));

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

    /** Whether {@code key} was a setting once and is now fixed in code, so a stored value is dropped silently. */
    public static boolean isRetired(@NotNull String key) {
        return RETIRED.contains(key);
    }

    private static Map<String, SettingSpec> index() {
        Map<String, SettingSpec> byKey = new LinkedHashMap<>();
        for (SettingSpec spec : ALL) {
            byKey.put(spec.getKey(), spec);
        }
        return byKey;
    }

    private static SettingSpec flag(String key, String title, String field, SettingApply applies, String description,
                                    Function<LibrarySettings, Boolean> read,
                                    BiFunction<LibrarySettings, Boolean, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.SWITCH, description, applies, null, null,
            Collections.emptyList(), null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (Boolean) value),
            SettingTexts::toSwitch, String::valueOf);
    }

    private static SettingSpec number(String key, String title, String field, SettingApply applies, long min,
                                      long max, String description, Function<LibrarySettings, Integer> read,
                                      BiFunction<LibrarySettings, Integer, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.NUMBER, description, applies, min, max,
            Collections.emptyList(), null,
            settings -> read.apply(settings), (settings, value) -> write.apply(settings, (Integer) value),
            text -> SettingTexts.number(text, min, max), String::valueOf);
    }

    private static SettingSpec text(String key, String title, String field, SettingApply applies, long maxLength,
                                    String description, Function<LibrarySettings, String> read,
                                    BiFunction<LibrarySettings, String, LibrarySettings> write) {
        return new SettingSpec(key, title, field, SettingKind.TEXT, description, applies, null, maxLength,
            Collections.emptyList(), null,
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
