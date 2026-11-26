package org.pnfolder.pnlibrary.core.menu.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.screens.config.ConfigRow;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/**
 * How the configuration screens look: the icon, the name and the lines of every kind of button.
 *
 * <p>Names stay short (a file shows its own name, not its full address); everything else goes into the description
 * and into labelled facts.
 */
public final class ConfigLook {
    private static final int BAR_WIDTH = 12;
    private static final int PERCENT = 100;
    private static final int CHOICES_SHOWN = 8;
    private static final String GREEN = MenuColors.color("&#6EE7A0");
    private static final String RED = MenuColors.color("&#FF7A7A");
    private static final String GRAY = MenuColors.color("&#8B7A99");
    private static final String AMBER = MenuColors.color("&#FFB347");

    private ConfigLook() {
    }

    public static @NotNull String applyWord(@NotNull ConfigApply apply) {
        switch (apply) {
            case LIVE:
                return "сразу";
            case RELOAD:
                return "после перечитывания файла";
            default:
                return "после перезапуска";
        }
    }

    public static @NotNull String number(double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static List<String> about(String text) {
        return CoreFormatting.wrapText(text, CoreFormatting.WRAP_WIDTH, 3);
    }

    private static String editableWord(int count) {
        return count > 0 ? Integer.toString(count) : "нет";
    }

    private static List<Fact> pending(@Nullable ConfigApply apply) {
        return apply == null ? Collections.emptyList() : Collections.singletonList(new Fact("Ждёт", AMBER + applyWord(apply)));
    }

    public static @NotNull ItemSpec plugin(@NotNull String name, int modules, int files, int editable, @Nullable ConfigApply waiting) {
        List<Fact> facts = Fact.list("Модулей", Integer.toString(modules), "Файлов", Integer.toString(files),
            "Можно менять", editableWord(editable));
        facts.addAll(pending(waiting));
        return MenuItems.icon(Icons.PLUGIN, "BOOKSHELF", name,
            Style.lore(about("Конфигурации плагина. Откройте, чтобы выбрать модуль или файл."), facts, Style.left("открыть")),
            waiting != null ? Tone.WARN : Tone.NORMAL);
    }

    public static @NotNull ItemSpec module(@NotNull String plugin, @NotNull String module, int files, int editable,
                                           @Nullable ConfigApply waiting) {
        List<Fact> facts = Fact.list("Файлов", Integer.toString(files), "Можно менять", editableWord(editable));
        facts.addAll(pending(waiting));
        return MenuItems.icon(Icons.RELEASES, "CHEST", module,
            Style.lore(about("Модуль плагина " + plugin + ". Внутри его файлы настроек."), facts, Style.left("файлы модуля")),
            waiting != null ? Tone.WARN : Tone.NORMAL);
    }

    /** A file: its own name as the title; the plugin and module only when the list mixes several. */
    public static @NotNull ItemSpec file(@NotNull ConfigRow row, @NotNull String plugin, @NotNull String module,
                                         @NotNull String path, boolean withPlugin, boolean withModule) {
        int slash = path.lastIndexOf('/');
        String name = path.substring(slash + 1);
        String folder = slash < 0 ? "" : path.substring(0, slash);
        List<Fact> shown = new ArrayList<>();
        if (withPlugin) {
            shown.addAll(ConfigText.fact("Плагин", plugin));
        }
        if (withModule) {
            shown.addAll(ConfigText.fact("Модуль", module));
        }
        if (!folder.isEmpty()) {
            shown.addAll(ConfigText.fact("Папка", folder));
        }
        shown.addAll(ConfigText.fact("Класс", row.getModel()));
        shown.add(new Fact("Состояние", row.isLoaded() ? GREEN + "● загружен" : GRAY + "○ не загружен"));
        shown.add(new Fact("Можно менять", editableWord(row.getEditable())));
        if (row.isReadOnly()) {
            shown.add(new Fact("Режим", "только чтение"));
        }
        shown.addAll(pending(row.getPending()));
        boolean editable = row.getEditable() > 0;
        String text = editable ? "Настройки, которые можно менять в игре." : "В этом файле нет настроек для игры. Его правят вручную.";
        String[] hint = editable ? new String[] {Style.left("настройки")} : new String[0];
        return MenuItems.icon(row.isReadOnly() ? Icons.LOCKED : Icons.CHANGELOG, row.isReadOnly() ? "BARRIER" : "WRITABLE_BOOK", name,
            Style.lore(about(text), shown, hint),
            Tone.of(false, row.getPending() != null, editable && row.isLoaded(), !editable));
    }

    /** One setting: the value, what it may be, when a change starts to work, and what a click does. */
    public static @NotNull ItemSpec setting(@NotNull ConfigSetting setting, boolean readOnly) {
        boolean on = "true".equals(setting.getValue());
        boolean toggle = setting.getKind() == ConfigSettingKind.BOOLEAN;
        String texture;
        String material;
        switch (setting.getKind()) {
            case BOOLEAN:
                texture = Icons.TOGGLE;
                material = on ? "LIME_DYE" : "GRAY_DYE";
                break;
            case CHOICE:
                texture = Icons.SETTINGS;
                material = "COMPARATOR";
                break;
            case INTEGER:
            case DECIMAL:
                texture = Icons.CLOCK;
                material = setting.getDuration() != null ? "CLOCK" : "REPEATER";
                break;
            default:
                texture = Icons.CHANGELOG;
                material = "NAME_TAG";
                break;
        }
        List<String> description = setting.getDescription().trim().isEmpty()
            ? Collections.emptyList() : CoreFormatting.wrapText(setting.getDescription(), CoreFormatting.WRAP_WIDTH, 4);
        return MenuItems.icon(texture, material, setting.getTitle(),
            Style.lore(description, settingFacts(setting), choices(setting), ConfigHints.of(setting, readOnly)),
            Tone.of(false, false, toggle && on, toggle && !on));
    }

    private static List<Fact> settingFacts(ConfigSetting setting) {
        boolean numeric = setting.getKind() == ConfigSettingKind.INTEGER || setting.getKind() == ConfigSettingKind.DECIMAL;
        List<Fact> shown = new ArrayList<>();
        String value = setting.getValue();
        if (setting.getKind() == ConfigSettingKind.BOOLEAN) {
            shown.add(new Fact("Состояние", "true".equals(value) ? GREEN + "✔ включено" : RED + "✘ выключено"));
        } else if (numeric) {
            shown.addAll(ConfigText.fact("Значение", ConfigNumbers.shown(setting)));
        } else {
            shown.addAll(ConfigText.fact("Значение", value == null ? "?" : value));
        }
        if (setting.getChoices().size() > 1) {
            shown.add(new Fact("Вариант", (setting.getChoices().indexOf(value) + 1) + " из " + setting.getChoices().size()));
        }
        if (setting.getMin() != null || setting.getMax() != null) {
            shown.add(new Fact("Границы", (setting.getMin() == null ? "…" : number(setting.getMin())) + " … "
                + (setting.getMax() == null ? "…" : number(setting.getMax()))));
        }
        String scale = scale(setting);
        if (scale != null) {
            shown.add(new Fact("Шкала", scale));
        }
        if (numeric) {
            List<NumberStep> ladder = ConfigNumbers.ladder(setting);
            shown.add(new Fact("Шаг", ladder.get(0).getLabel()));
            shown.add(new Fact("С Shift", ladder.get(1).getLabel()));
        }
        if (!setting.getFormat().trim().isEmpty()) {
            shown.addAll(ConfigText.fact("Формат", setting.getFormat()));
        }
        if (!setting.getExamples().isEmpty()) {
            shown.addAll(ConfigText.fact("Примеры", String.join(", ", setting.getExamples())));
        }
        if (setting.getApply() != ConfigApply.LIVE) {
            shown.add(new Fact("Заработает", AMBER + applyWord(setting.getApply())));
        }
        return shown;
    }

    /** Where a number sits between its bounds, as a bar; only when both bounds are known. */
    private static String scale(ConfigSetting setting) {
        Double low = setting.getMin();
        Double high = setting.getMax();
        if (low == null || high == null || high <= low || setting.getValue() == null) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(setting.getValue().trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
        double share = Math.max(0.0, Math.min((value - low) / (high - low), 1.0));
        int filled = (int) Math.round(share * BAR_WIDTH);
        StringBuilder bar = new StringBuilder(GREEN);
        for (int index = 0; index < filled; index++) {
            bar.append('█');
        }
        bar.append(GRAY);
        for (int index = filled; index < BAR_WIDTH; index++) {
            bar.append('░');
        }
        return bar + " " + Math.round(share * PERCENT) + "%";
    }

    /** The choices one under another with the current one marked; a long list shows the window around it. */
    private static List<String> choices(ConfigSetting setting) {
        return window(setting.getChoices(), Math.max(setting.getChoices().indexOf(setting.getValue()), 0));
    }

    /**
     * The rolling switcher: the options one under another with the one at {@code at} marked, at most eight lines around
     * it, and a count of what is cut off above and below. Config settings and library settings share it.
     */
    public static @NotNull List<String> window(@NotNull List<String> options, int at) {
        if (options.isEmpty()) {
            return Collections.emptyList();
        }
        int from = Math.max(0, Math.min(at - CHOICES_SHOWN / 2, Math.max(options.size() - CHOICES_SHOWN, 0)));
        List<String> shown = options.subList(from, Math.min(from + CHOICES_SHOWN, options.size()));
        int below = options.size() - from - shown.size();
        List<String> lines = new ArrayList<>();
        if (from > 0) {
            lines.add(GRAY + "   ▲ выше ещё " + from);
        }
        lines.addAll(Style.choices(shown, at - from));
        if (below > 0) {
            lines.add(GRAY + "   ▼ ниже ещё " + below);
        }
        return lines;
    }

    /** The "where you are" item of a screen: the path down to the plugin, module or file. */
    public static @NotNull ItemSpec place(@NotNull String... labelsAndValues) {
        List<Fact> facts = new ArrayList<>();
        for (int index = 0; index + 1 < labelsAndValues.length; index += 2) {
            facts.addAll(ConfigText.fact(labelsAndValues[index], labelsAndValues[index + 1]));
        }
        return MenuItems.icon(Icons.PLUGINS, "COMPASS", "Где вы",
            Style.lore(Collections.emptyList(), facts));
    }

    /** Facts built from alternating labels and values, long values wrapped. */
    public static @NotNull List<Fact> facts(@NotNull String... labelsAndValues) {
        List<Fact> facts = new ArrayList<>();
        for (int index = 0; index + 1 < labelsAndValues.length; index += 2) {
            facts.addAll(ConfigText.fact(labelsAndValues[index], labelsAndValues[index + 1]));
        }
        return facts;
    }
}
