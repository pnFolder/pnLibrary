package org.pnfolder.pnlibrary.core.menu.screens.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook;
import org.pnfolder.pnlibrary.core.menu.style.ConfigNumbers;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.NumberStep;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/**
 * The two screens that edit a setting more comfortably than a click on its item: a number editor with steps of
 * different size, and a paged list of choices for a setting that has many.
 */
final class ConfigPickers extends MenuScreens {
    private static final int VALUE = 13;
    private static final int TYPE = 4;
    private static final int LOW = 20;
    private static final int HIGH = 24;
    private static final double MILLI = 1000.0;

    ConfigPickers(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    private ConfigSetting setting(String id, String path) {
        for (ConfigSetting setting : data().configSettings(id)) {
            if (setting.getPath().equals(path)) {
                return setting;
            }
        }
        return null;
    }

    private static Double numberOf(ConfigSetting setting) {
        try {
            return setting.getValue() == null ? null : Double.valueOf(setting.getValue().trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The number editor: the value in the middle, steps down on the left, steps up on the right. */
    void number(MenuViewer player, String id, String path, Consumer<MenuViewer> back, SettingWriter write,
                BiConsumer<MenuViewer, ConfigSetting> ask) {
        ConfigSetting setting = setting(id, path);
        Double found = setting == null ? null : numberOf(setting);
        if (found == null) {
            back.accept(player);
            return;
        }
        double current = found;
        String title = setting.getTitle();
        MenuBuilder builder = MenuItems.menuFrame(Menus.chest(MenuItems.menuTitle(
            title.length() > MenuConstants.TITLE_LIMIT ? title.substring(0, MenuConstants.TITLE_LIMIT) : title))
            .rows(MenuConstants.SMALL_ROWS + 1), MenuConstants.SMALL_ROWS + 1);
        Consumer<MenuViewer> again = who -> number(who, id, path, back, write, ask);
        builder.slot(VALUE, ConfigLook.setting(setting, true));
        List<NumberStep> ladder = ConfigNumbers.ladder(setting);
        for (int index = 0; index < ladder.size(); index++) {
            double amount = ladder.get(index).getAmount();
            String label = ladder.get(index).getLabel();
            builder.button(VALUE - 1 - index, stepItem("−", label, true),
                go(who -> write.write(who, setting, text(setting, current - amount), again)));
            builder.button(VALUE + 1 + index, stepItem("+", label, false),
                go(who -> write.write(who, setting, text(setting, current + amount), again)));
        }
        builder.button(TYPE, MenuItems.icon(Icons.CHANGELOG, "NAME_TAG", "Ввести значение", Style.lore(typingHelp(setting),
            Collections.emptyList(), Style.left("написать в чате"))), go(who -> ask.accept(who, setting)));
        if (setting.getMin() != null) {
            double limit = setting.getMin();
            builder.button(LOW, edge("Минимум", limit, setting), go(who -> write.write(who, setting, text(setting, limit), again)));
        }
        if (setting.getMax() != null) {
            double limit = setting.getMax();
            builder.button(HIGH, edge("Максимум", limit, setting), go(who -> write.write(who, setting, text(setting, limit), again)));
        }
        builder.button(MenuConstants.SMALL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private static List<String> typingHelp(ConfigSetting setting) {
        String base = setting.getKind() == ConfigSettingKind.INTEGER ? "Целое число." : "Число, дробная часть через точку.";
        String time = setting.getDuration() != null ? " Время можно писать так: 90m, 1h30m, 45с, 2д." : "";
        return CoreFormatting.wrapText(base + time, CoreFormatting.WRAP_WIDTH, 3);
    }

    private static ItemSpec stepItem(String sign, String label, boolean down) {
        return MenuItems.icon(null, down ? "RED_DYE" : "LIME_DYE", sign + " " + label, Style.lore(Collections.emptyList(),
            Collections.emptyList(), Style.left(down ? "уменьшить" : "увеличить")), down ? Tone.DANGER : Tone.GOOD);
    }

    private static ItemSpec edge(String name, double limit, ConfigSetting setting) {
        ConfigSetting at = setting.toBuilder().value(ConfigLook.number(limit)).build();
        return MenuItems.icon(Icons.CLOCK, "CLOCK", name, Style.lore(Collections.emptyList(),
            Fact.list("Значение", ConfigNumbers.shown(at)), Style.left("поставить")));
    }

    /** Rounds to what the field holds: whole numbers without a fraction, decimals to thousandths. */
    private static String text(ConfigSetting setting, double value) {
        double low = setting.getMin() == null ? -Double.MAX_VALUE : setting.getMin();
        double high = setting.getMax() == null ? Double.MAX_VALUE : setting.getMax();
        double bounded = Math.max(low, Math.min(value, high));
        if (setting.getKind() == ConfigSettingKind.INTEGER) {
            return Long.toString(Math.round(bounded));
        }
        return ConfigLook.number(Math.round(bounded * MILLI) / MILLI);
    }

    /** All choices of a setting in pages; a click picks one and returns to {@code done}. */
    void choice(MenuViewer player, String id, String path, int page, Consumer<MenuViewer> done, SettingWriter write) {
        ConfigSetting setting = setting(id, path);
        if (setting == null) {
            done.accept(player);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (String value : setting.getChoices()) {
            boolean chosen = value.equals(setting.getValue());
            List<String> lore = chosen ? Style.lore(Collections.emptyList(), Collections.emptyList())
                : Style.lore(Collections.emptyList(), Collections.emptyList(), Style.left("выбрать"));
            rows.add(new Row(MenuItems.icon(null, chosen ? "LIME_DYE" : "PAPER", value, lore, chosen ? Tone.GOOD : Tone.NORMAL),
                chosen ? null : go(who -> write.write(who, setting, value, done))));
        }
        String title = setting.getTitle();
        list(player, title.length() > MenuConstants.TITLE_LIMIT ? title.substring(0, MenuConstants.TITLE_LIMIT) : title, rows, page,
            MenuConstants.PAGE_SIZE, (who, to) -> choice(who, id, path, to, done, write), done, "Вариантов нет",
            builder -> builder.slot(MenuConstants.DATE_FILTER, ConfigLook.place("Настройка", setting.getTitle(),
                "Сейчас", setting.getValue() == null ? "" : setting.getValue(), "Вариантов", String.valueOf(setting.getChoices().size()))));
    }
}
