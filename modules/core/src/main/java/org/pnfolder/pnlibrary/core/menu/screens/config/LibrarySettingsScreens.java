package org.pnfolder.pnlibrary.core.menu.screens.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.core.menu.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.SettingOutcome;
import org.pnfolder.pnlibrary.core.menu.data.SettingRow;
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.settings.ChoiceSource;
import org.pnfolder.pnlibrary.core.settings.SettingTexts;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/**
 * The page with every setting of the library and its value. A switch flips on a click, a number goes up and down
 * (Shift for ten at a time), a text or a time is typed in the chat, a choice among many values rolls to the next one
 * (Shift opens a picker), the wheel returns a setting to its default. The same settings are changed by {@code /pn settings};
 * the list comes from the server whose menu this is.
 */
public final class LibrarySettingsScreens extends MenuScreens {
    private static final int BIG_STEP = 10;
    private static final String KIND_SWITCH = "SWITCH";
    private static final String KIND_NUMBER = "NUMBER";
    private static final String KIND_TIME = "TIME";
    private static final String KIND_CHOICE = "CHOICE";

    private final SettingChoicePicker picker;

    public LibrarySettingsScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.picker = new SettingChoicePicker(session, routes);
    }

    public void librarySettings(MenuViewer player, int page) {
        List<SettingRow> all = data().settings();
        List<Row> rows = new ArrayList<>();
        int changed = 0;
        for (SettingRow row : all) {
            changed += row.isChanged() ? 1 : 0;
            rows.add(new Row(item(row), goClick((who, how) -> click(who, row, how, page))));
        }
        String changedCount = String.valueOf(changed);
        list(player, "Параметры библиотеки", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> librarySettings(who, to),
            routes::settings, "Настроек нет", builder -> builder.slot(MenuConstants.DATE_FILTER,
                ConfigLook.place("Настроек", String.valueOf(all.size()), "Изменено", changedCount)));
    }

    private ItemSpec item(SettingRow row) {
        boolean toggle = row.getKind().equals(KIND_SWITCH);
        ChoiceSource source = ChoiceSource.named(row.getChoiceSource());
        List<Fact> parameters = new ArrayList<>();
        if (!toggle) {
            parameters.add(new Fact("Значение", row.getCurrent()));
        }
        if (source != null) {
            parameters.add(new Fact("Вариант", variant(source, row.getCurrent())));
            for (Pair<String, String> pair : source.lore(row.getCurrent())) {
                parameters.add(new Fact(pair.getFirst(), pair.getSecond()));
            }
        }
        if (row.isChanged() && !toggle) {
            parameters.add(new Fact("По умолчанию", row.getDefaultValue()));
        }
        if (!row.getRange().isEmpty()) {
            parameters.add(new Fact("Допустимо", row.getRange()));
        }
        if (!row.getExamples().isEmpty() && source == null) {
            parameters.add(new Fact("Примеры", String.join(", ", row.getExamples())));
        }
        if (!row.isLive()) {
            parameters.add(new Fact("Заработает", "после перезапуска"));
        }
        boolean on = row.getCurrent().equals("true");
        List<String> choices = Collections.emptyList();
        if (toggle) {
            choices = Style.choices(Arrays.asList(SettingTexts.switchWord(true), SettingTexts.switchWord(false)), on ? 0 : 1,
                row.getDefaultValue().equals("true") ? 0 : 1);
        } else if (source != null) {
            List<String> options = source.rolling(row.getCurrent());
            choices = ConfigLook.window(options, options.indexOf(row.getCurrent()));
        }
        String texture;
        String material;
        switch (row.getKind()) {
            case KIND_SWITCH:
                texture = Icons.TOGGLE;
                material = on ? "LIME_DYE" : "GRAY_DYE";
                break;
            case KIND_NUMBER:
                texture = Icons.CLOCK;
                material = "REPEATER";
                break;
            case KIND_TIME:
                texture = Icons.CLOCK;
                material = "CLOCK";
                break;
            case KIND_CHOICE:
                texture = Icons.SETTINGS;
                material = "COMPASS";
                break;
            default:
                texture = Icons.CHANGELOG;
                material = "NAME_TAG";
                break;
        }
        return MenuItems.icon(texture, material, row.getTitle(),
            Style.lore(CoreFormatting.wrapText(row.getDescription(), CoreFormatting.WRAP_WIDTH, 5), parameters, choices, hints(row)),
            Tone.of(false, row.isChanged() && !toggle, toggle && on, toggle && !on));
    }

    /** {@code 3 из 21}; a value outside the rolled set is not counted in it: {@code — из 21}. */
    private static String variant(ChoiceSource source, String current) {
        int at = source.getCycle().indexOf(current);
        return (at >= 0 ? String.valueOf(at + 1) : "—") + " из " + source.getCycle().size();
    }

    private static String[] hints(SettingRow row) {
        List<String> hints = new ArrayList<>();
        switch (row.getKind()) {
            case KIND_SWITCH:
                hints.add(Style.left("включить или выключить"));
                break;
            case KIND_NUMBER:
                hints.addAll(Arrays.asList(Style.left("больше"), Style.right("меньше"), Style.shiftLeft("на десять")));
                break;
            case KIND_CHOICE:
                if (ChoiceSource.named(row.getChoiceSource()) != null) {
                    hints.addAll(Arrays.asList(Style.left("следующий"), Style.right("предыдущий"), Style.shiftLeft("весь список")));
                } else {
                    hints.add(Style.left("ввести в чате"));
                }
                break;
            default:
                hints.add(Style.left("ввести в чате"));
                break;
        }
        if (row.isChanged()) {
            hints.add(Style.middle("вернуть значение по умолчанию"));
        }
        return hints.toArray(new String[0]);
    }

    private void click(MenuViewer player, SettingRow row, ClickType how, int page) {
        if (how == ClickType.MIDDLE) {
            if (row.isChanged()) {
                change(player, row, null, page);
            }
            return;
        }
        switch (row.getKind()) {
            case KIND_SWITCH:
                change(player, row, String.valueOf(!row.getCurrent().equals("true")), page);
                break;
            case KIND_NUMBER:
                number(player, row, how, page);
                break;
            case KIND_CHOICE:
                choose(player, row, how, page);
                break;
            default:
                type(player, row);
                break;
        }
    }

    private void type(MenuViewer player, SettingRow row) {
        allowed(player, MenuPermissions.LIBRARY_SETTINGS, () -> suggestCommand(player,
            ("Введите новое значение для «" + row.getTitle() + "». " + hint(row)).trim(),
            "/pn settings set " + row.getKey() + " " + row.getCurrent()));
    }

    /**
     * A click rolls to the next value, a right click to the previous one, Shift + click opens the whole list; a setting
     * without a list asks for the value in the chat.
     */
    private void choose(MenuViewer player, SettingRow row, ClickType how, int page) {
        ChoiceSource source = ChoiceSource.named(row.getChoiceSource());
        if (source == null) {
            type(player, row);
        } else if (how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT) {
            pick(player, row, source, "", page);
        } else if (how == ClickType.RIGHT) {
            change(player, row, source.previous(row.getCurrent()), page);
        } else {
            change(player, row, source.next(row.getCurrent()), page);
        }
    }

    /**
     * Opens the list of values of the choice setting {@code key} narrowed to {@code filter}, or tells that there is no
     * such setting. {@code /pn settings pick} opens it this way.
     */
    public void pick(MenuViewer player, String key, String filter) {
        SettingRow found = null;
        for (SettingRow row : data().settings()) {
            if (row.getKey().equals(key)) {
                found = row;
            }
        }
        ChoiceSource source = found == null ? null : ChoiceSource.named(found.getChoiceSource());
        if (found == null || source == null) {
            tell(player, ChatTone.FAILURE, "Нет настройки с выбором из списка: " + key + ".");
            return;
        }
        pick(player, found, source, filter, 0);
    }

    private void pick(MenuViewer player, SettingRow row, ChoiceSource source, String filter, int page) {
        allowed(player, MenuPermissions.LIBRARY_SETTINGS, () -> picker.open(player,
            new SettingChoicePicker.Target(row, source, (who, value) -> change(who, row, value, page), who -> type(who, row),
                who -> librarySettings(who, page)), filter));
    }

    private static String hint(SettingRow row) {
        List<String> parts = new ArrayList<>();
        if (!row.getRange().isEmpty()) {
            parts.add("Допустимо: " + row.getRange() + ".");
        }
        if (!row.getExamples().isEmpty()) {
            parts.add("Например: " + String.join(", ", row.getExamples()) + ".");
        }
        return String.join(" ", parts);
    }

    private void number(MenuViewer player, SettingRow row, ClickType how, int page) {
        long current;
        try {
            current = Long.parseLong(row.getCurrent().trim());
        } catch (NumberFormatException notANumber) {
            return;
        }
        int step = how == ClickType.SHIFT_LEFT || how == ClickType.SHIFT_RIGHT ? BIG_STEP : 1;
        int direction = how == ClickType.RIGHT || how == ClickType.SHIFT_RIGHT ? -1 : 1;
        change(player, row, String.valueOf(current + direction * step), page);
    }

    private void change(MenuViewer player, SettingRow row, String value, int page) {
        allowed(player, MenuPermissions.LIBRARY_SETTINGS, () -> host().async(() -> {
            SettingOutcome outcome;
            try {
                outcome = data().changeSetting(row.getKey(), value, player.getActor());
            } catch (Exception error) {
                outcome = new SettingOutcome(false, error.getMessage() != null ? error.getMessage() : "Не удалось сохранить настройку");
            }
            SettingOutcome result = outcome;
            host().sync(() -> {
                if (!result.isOk()) {
                    tell(player, ChatTone.FAILURE, "Не изменено.", result.getMessage());
                } else if (!result.isLive()) {
                    tell(player, ChatTone.WARNING, "Сохранено: " + row.getTitle() + " = " + result.getMessage() + ".",
                        "Заработает после перезапуска сервера.");
                }
                librarySettings(player, page);
            });
        }));
    }
}
