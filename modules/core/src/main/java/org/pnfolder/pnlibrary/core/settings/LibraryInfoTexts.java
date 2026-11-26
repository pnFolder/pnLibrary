package org.pnfolder.pnlibrary.core.settings;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** The texts of the three information files: the help, the settings overview and the command guide. */
final class LibraryInfoTexts {
    /** The start of the line with the creation time, which is left out when files are compared. */
    static final String CREATED = "Создан: ";
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final LibraryPaths paths;
    private final Supplier<List<SettingValue>> values;
    private final String version;
    private final Clock clock;
    private final String supportUrl;
    private final String repositoryUrl;

    LibraryInfoTexts(LibraryPaths paths, Supplier<List<SettingValue>> values, String version, Clock clock,
                     String supportUrl, String repositoryUrl) {
        this.paths = paths;
        this.values = values;
        this.version = version;
        this.clock = clock;
        this.supportUrl = supportUrl;
        this.repositoryUrl = repositoryUrl;
    }

    @NotNull String help() {
        List<String> lines = lines(
            "Помощь pnLibrary",
            "================",
            "",
            "pnLibrary — общая библиотека плагинов pnFolder для серверов Minecraft.",
            "Она ведёт настройки, обновления, диагностику и меню для всех наших плагинов.",
            "",
            "Нужна помощь или есть вопрос?",
            "  Напишите нам в Discord: " + supportUrl,
            "",
            "Хотите улучшить плагин или заказать новый?",
            "  Идею, как улучшить плагин, присылайте туда же, в Discord: " + supportUrl,
            "  Бесплатный плагин тоже можно заказать там же: просто напишите нам.",
            "",
            "Код и документация",
            "  GitHub: " + repositoryUrl,
            "  Документация: " + repositoryUrl + "/tree/main/docs",
            "",
            "Все команды и права",
            "  Полный список с правами лежит в файле " + paths.getCommandGuide().getPreferred() + ".",
            "",
            "Самые полезные команды",
            "  /pn updates          каталог плагинов и их обновления",
            "  /pn check            проверить, есть ли новые версии",
            "  /pn update <плагин>  подготовить обновление плагина",
            "  /pn settings         настройки библиотеки (все они перечислены в файле "
                + paths.getSettingsOverview().getPreferred() + ")",
            "  /pn debug            собрать отчёт для поддержки",
            "  /pn support          ссылка на поддержку",
            "  /pn gui              меню",
            "",
            "Где библиотека хранит свои данные",
            "  В папке data/ рядом с этим файлом.",
            "",
            "Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся.",
            "");
        return join(finish(lines));
    }

    @NotNull String settings() {
        List<SettingValue> settings = values.get();
        List<String> lines = lines(
            "Настройки pnLibrary",
            "===================",
            "",
            "ВАЖНО: этот файл только для чтения, это справка.",
            "Здесь показано, какие настройки есть и какие у них сейчас значения.",
            "Если вы поменяете что-то в этом файле, ничего не изменится: библиотека перезапишет его.",
            "Менять настройки нужно командой или в меню, как описано ниже.",
            "",
            "Как изменить настройку:",
            "  /pn settings list            все настройки со значениями",
            "  /pn settings get <ключ>      одна настройка подробно",
            "  /pn settings set <ключ> <значение>   изменить",
            "  /pn settings reset <ключ>    вернуть значение по умолчанию",
            "  Или в меню: /pn gui, раздел «Настройки», «Параметры библиотеки».",
            "Изменённые значения хранятся в data/settings.json, править его руками не нужно.",
            "",
            "Этот файл создаётся заново при каждом запуске и после каждого изменения настройки, "
                + "ваши правки не сохранятся.",
            "",
            "Настроек: " + settings.size());
        for (SettingValue value : settings) {
            lines.addAll(describe(value));
        }
        lines.add("");
        return join(finish(lines));
    }

    @NotNull String commands() {
        List<String> lines = lines(
            "Команды и права pnLibrary",
            "=========================",
            "",
            "Все права по умолчанию у операторов; назначайте их в LuckPerms или другом менеджере прав.",
            "Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся.",
            "",
            "Как читать этот файл");
        for (String note : LibraryCommandGuide.getNotes()) {
            lines.add("  " + note);
        }
        lines.addAll(LibraryCommandGuide.lines());
        lines.add("");
        return join(finish(lines));
    }

    private List<String> describe(SettingValue value) {
        SettingSpec spec = value.getSpec();
        List<String> lines = new ArrayList<String>();
        lines.add("");
        lines.add(spec.getKey() + "  (" + spec.getTitle() + ")");
        lines.add("  Сейчас: " + spec.display(value.getCurrent())
            + (value.getChanged() ? "  (изменено администратором)" : "  (по умолчанию)"));
        lines.add("  По умолчанию: " + spec.display(spec.getDefault()));
        lines.add("  Тип: " + spec.getKind().getLabel());
        if (!spec.range().isEmpty()) {
            lines.add("  Границы: " + spec.range());
        }
        if (!spec.getExamples().isEmpty()) {
            lines.add("  Примеры: " + String.join(", ", spec.getExamples()));
        }
        lines.add("  Начнёт работать: " + spec.getApplies().getLabel());
        lines.add("  " + spec.getDescription());
        lines.add("  Изменить: /pn settings set " + spec.getKey() + " " + exampleOf(spec));
        return lines;
    }

    private static String exampleOf(SettingSpec spec) {
        if (!spec.getExamples().isEmpty()) {
            return spec.getExamples().get(0);
        }
        return spec.getKind() == SettingKind.SWITCH ? "включено" : "<значение>";
    }

    private List<String> finish(List<String> lines) {
        lines.add(PnLibraryIdentity.NAME + " " + version);
        lines.add(CREATED + TIME.format(clock.instant()) + " UTC");
        return lines;
    }

    private static List<String> lines(String... text) {
        List<String> lines = new ArrayList<String>();
        java.util.Collections.addAll(lines, text);
        return lines;
    }

    static String join(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }
}
