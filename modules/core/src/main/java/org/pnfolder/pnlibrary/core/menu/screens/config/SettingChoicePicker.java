package org.pnfolder.pnlibrary.core.menu.screens.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.SettingRow;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.settings.ChoiceOrder;
import org.pnfolder.pnlibrary.core.settings.ChoiceSource;

/**
 * The screen that chooses one value of a setting that has many (a time zone): one paged list of all values, like the
 * other choice lists, but each value tells about itself, and the list can be sorted, narrowed to the popular values or
 * searched by a part of the name. It knows nothing about the settings screen: the screen hands over what to do on a
 * pick, on typing and on going back.
 */
final class SettingChoicePicker extends MenuScreens {
    private static final int TOGGLE = 51;
    private static final int SEARCH = 52;

    /** What the picker works on: pick writes the chosen value, type asks for it in the chat, back leaves the picker. */
    static final class Target {
        final SettingRow row;
        final ChoiceSource source;
        final BiConsumer<MenuViewer, String> pick;
        final Consumer<MenuViewer> type;
        final Consumer<MenuViewer> back;

        Target(SettingRow row, ChoiceSource source, BiConsumer<MenuViewer, String> pick, Consumer<MenuViewer> type,
               Consumer<MenuViewer> back) {
            this.row = row;
            this.source = source;
            this.pick = pick;
            this.type = type;
            this.back = back;
        }
    }

    /** How the list is shown: the number of the sort order, whether only the popular values, and the text searched for. */
    private static final class View {
        final int order;
        final boolean popular;
        final String filter;

        View(int order, boolean popular, String filter) {
            this.order = order;
            this.popular = popular;
            this.filter = filter;
        }

        View withOrder(int value) {
            return new View(value, popular, filter);
        }

        View withPopular(boolean value) {
            return new View(order, value, filter);
        }

        View withFilter(String value) {
            return new View(order, popular, value);
        }
    }

    SettingChoicePicker(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** Opens the list of values, already narrowed to those that contain {@code filter} when it is not empty. */
    void open(MenuViewer player, Target target, String filter) {
        values(player, target, new View(0, false, filter.trim()), 0);
    }

    private void values(MenuViewer player, Target target, View view, int page) {
        SettingRow row = target.row;
        ChoiceSource source = target.source;
        Instant now = Instant.now();
        List<String> shown = source.view(view.order, view.popular, view.filter, now);
        List<Row> rows = new ArrayList<>();
        for (String value : shown) {
            boolean chosen = value.equals(row.getCurrent());
            List<Fact> facts = new ArrayList<>();
            for (Pair<String, String> pair : source.lore(value, now)) {
                facts.add(new Fact(pair.getFirst(), pair.getSecond()));
            }
            List<String> lore = chosen ? Style.lore(Collections.emptyList(), facts)
                : Style.lore(Collections.emptyList(), facts, Style.left("выбрать"));
            rows.add(new Row(MenuItems.icon(null, chosen ? "LIME_DYE" : "PAPER", value, lore, chosen ? Tone.GOOD : Tone.NORMAL),
                chosen ? null : go(who -> target.pick.accept(who, value))));
        }
        String empty = view.filter.isEmpty() ? "Вариантов нет" : "Ничего не найдено";
        list(player, title(row, view), rows, page, MenuConstants.PAGE_SIZE, (who, to) -> values(who, target, view, to),
            target.back, empty, builder -> footer(builder, target, view, shown.size()));
    }

    private void footer(MenuBuilder builder, Target target, View view, int shown) {
        SettingRow row = target.row;
        ChoiceSource source = target.source;
        builder.button(MenuConstants.DATE_FILTER, typeItem(row, shown), go(target.type));
        if (!view.popular) {
            View next = view.withOrder((view.order + 1) % source.getOrders().size());
            builder.button(MenuConstants.FILTER, orderItem(source, view), goClick((who, how) -> values(who, target, next, 0)));
        }
        if (!view.filter.isEmpty()) {
            builder.button(TOGGLE, resetItem(view), go(who -> values(who, target, view.withFilter(""), 0)));
        } else if (!source.getPopular().isEmpty()) {
            builder.button(TOGGLE, popularItem(view), go(who -> values(who, target, view.withPopular(!view.popular), 0)));
        }
        builder.button(SEARCH, searchItem(view), goClick((who, how) -> search(who, target, view, how)));
    }

    private static String title(SettingRow row, View view) {
        String text;
        if (!view.filter.isEmpty()) {
            text = row.getTitle() + ": «" + view.filter + "»";
        } else if (view.popular) {
            text = row.getTitle() + ": популярные";
        } else {
            text = row.getTitle();
        }
        return text.length() > MenuConstants.TITLE_LIMIT ? text.substring(0, MenuConstants.TITLE_LIMIT) : text;
    }

    /** A click asks for the text in the chat; the wheel clears the search. */
    private void search(MenuViewer player, Target target, View view, ClickType how) {
        if (how == ClickType.MIDDLE) {
            if (!view.filter.isEmpty()) {
                values(player, target, view.withFilter(""), 0);
            }
            return;
        }
        suggestCommand(player, "Напишите часть названия для «" + target.row.getTitle() + "», например new york.",
            "/pn settings pick " + target.row.getKey() + " ");
    }

    private static ItemSpec orderItem(ChoiceSource source, View view) {
        List<String> labels = new ArrayList<>();
        for (ChoiceOrder order : source.getOrders()) {
            labels.add(order.getLabel());
        }
        return MenuItems.icon(null, "HOPPER", "Сортировка", Style.lore(Collections.emptyList(), Collections.emptyList(),
            Style.choices(labels, view.order, 0), Style.left("следующий порядок")));
    }

    private static ItemSpec popularItem(View view) {
        return MenuItems.icon(null, "NETHER_STAR", "Популярные", Style.lore(Collections.emptyList(), Collections.emptyList(),
            Style.choices(java.util.Arrays.asList("Все", "Популярные"), view.popular ? 1 : 0, 0), Style.left("переключить")),
            view.popular ? Tone.GOOD : Tone.NORMAL);
    }

    private static ItemSpec searchItem(View view) {
        List<String> hints = new ArrayList<>();
        hints.add(Style.left("искать по названию"));
        List<Fact> found = new ArrayList<>();
        if (!view.filter.isEmpty()) {
            hints.add(Style.middle("сбросить поиск"));
            found.add(new Fact("Ищем", view.filter));
        }
        return MenuItems.icon(null, "COMPASS", "Поиск", Style.lore(Collections.emptyList(), found, hints.toArray(new String[0])));
    }

    private static ItemSpec resetItem(View view) {
        return MenuItems.icon(null, "BARRIER", "Сбросить поиск", Style.lore(Collections.emptyList(),
            Fact.list("Ищем", view.filter), Style.left("показать всё")), Tone.DANGER);
    }

    private static ItemSpec typeItem(SettingRow row, int shown) {
        return MenuItems.icon(Icons.CHANGELOG, "NAME_TAG", "Ввести в чате", Style.lore(Collections.emptyList(),
            Fact.list("Сейчас", row.getCurrent(), "Показано", String.valueOf(shown)), Style.left("написать значение")));
    }
}
