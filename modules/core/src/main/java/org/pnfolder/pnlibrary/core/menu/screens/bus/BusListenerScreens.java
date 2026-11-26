package org.pnfolder.pnlibrary.core.menu.screens.bus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** The lists of event listeners: all of them, the slow or failing ones, and by plugin. */
final class BusListenerScreens extends MenuScreens {
    BusListenerScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    void listeners(MenuViewer player, ListenerQuery query, int page) {
        List<ListenerInfo> all = new ArrayList<>();
        for (ListenerInfo info : data().listeners()) {
            if (query.keep.test(info)) {
                all.add(info);
            }
        }
        if (query.order != null) {
            Collections.sort(all, query.order);
        }
        List<Row> rows = new ArrayList<>();
        for (ListenerInfo info : all) {
            rows.add(new Row(item(info), go(who -> routes.busEvent(who, info.getEventType(), v -> listeners(v, query, page)))));
        }
        list(player, query.title, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> listeners(who, query, to), query.back,
            "Слушателей нет");
    }

    private ItemSpec item(ListenerInfo info) {
        String owner = info.getModule() == null ? String.valueOf(info.getPlugin()) : info.getPlugin() + "/" + info.getModule();
        List<Fact> parameters = Fact.list("Плагин", routes.ownerLine(info.getPlugin().toString()),
            "Событие", CoreFormatting.shortName(info.getEventType()), "Приоритет", String.valueOf(info.getPriority()),
            "Запусков", String.valueOf(info.getInvocations()), "Среднее", CoreFormatting.nanosText(info.getAverageNanos()),
            "Максимум", CoreFormatting.nanosText(info.getMaxNanos()));
        if (info.getFailures() > 0) {
            parameters.add(new Fact("Ошибок", "✖ " + info.getFailures()));
        }
        if (info.getIgnoreDenied()) {
            parameters.add(new Fact("Особенность", "работает даже после отказа"));
        }
        String source = info.getSource();
        parameters.add(new Fact("Откуда", source.length() > MenuConstants.MESSAGE_PREVIEW
            ? source.substring(0, MenuConstants.MESSAGE_PREVIEW) : source));
        return MenuItems.icon(null, "COMPARATOR", owner,
            Style.lore(Collections.emptyList(), parameters, Style.left("открыть событие")), "REDSTONE_COMPARATOR",
            info.getFailures() > 0 ? Tone.DANGER : Tone.NORMAL);
    }

    void plugins(MenuViewer player, int page) {
        Map<String, List<ListenerInfo>> groups = new LinkedHashMap<>();
        for (ListenerInfo info : data().listeners()) {
            List<ListenerInfo> group = groups.get(info.getPlugin().toString());
            if (group == null) {
                group = new ArrayList<>();
                groups.put(info.getPlugin().toString(), group);
            }
            group.add(info);
        }
        List<Map.Entry<String, List<ListenerInfo>>> sorted = new ArrayList<>(groups.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, List<ListenerInfo>>>() {
            @Override
            public int compare(Map.Entry<String, List<ListenerInfo>> a, Map.Entry<String, List<ListenerInfo>> b) {
                return Integer.compare(b.getValue().size(), a.getValue().size());
            }
        });
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<ListenerInfo>> entry : sorted) {
            rows.add(pluginRow(entry.getKey(), entry.getValue(), page));
        }
        list(player, "События по плагинам", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> plugins(who, to),
            routes::busHome, "Слушателей нет");
    }

    private Row pluginRow(String id, List<ListenerInfo> group, int page) {
        long failures = 0;
        long invocations = 0;
        long nanos = 0;
        Set<String> events = new LinkedHashSet<>();
        for (ListenerInfo info : group) {
            failures += info.getFailures();
            invocations += info.getInvocations();
            nanos += info.getTotalNanos();
            events.add(info.getEventType());
        }
        List<Fact> parameters = Fact.list("Слушателей", String.valueOf(group.size()), "Событий", String.valueOf(events.size()),
            "Запусков", String.valueOf(invocations), "Время всего", CoreFormatting.nanosText(nanos));
        if (failures > 0) {
            parameters.add(new Fact("Ошибок", "✖ " + failures));
        }
        ListenerQuery query = new ListenerQuery("Плагин " + id, who -> plugins(who, page),
            info -> info.getPlugin().toString().equals(id), new Comparator<ListenerInfo>() {
                @Override
                public int compare(ListenerInfo a, ListenerInfo b) {
                    return Long.compare(b.getTotalNanos(), a.getTotalNanos());
                }
            });
        return new Row(MenuItems.icon(null, "BOOKSHELF", id,
            Style.lore(Collections.emptyList(), parameters, Style.left("слушатели плагина")),
            failures > 0 ? Tone.DANGER : Tone.NORMAL), go(who -> listeners(who, query, 0)));
    }
}
