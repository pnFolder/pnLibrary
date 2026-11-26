package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.menu.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.data.EventAbout;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Menu screens for event bus overview, events and listeners. */
public final class BusScreens extends MenuScreens {
    private final BusListenerScreens listenerScreens;

    public BusScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.listenerScreens = new BusListenerScreens(session, routes);
    }

    public void busHome(MenuViewer player) {
        List<EventInfo> events = data().events();
        List<ListenerInfo> listeners = data().listeners();
        LocalDate today = LocalDate.now(DisplayZone.getZone());
        long calls = 0;
        for (EventDay day : data().eventHistory(null)) {
            if (day.getDate().equals(today)) {
                calls += day.getCalls();
            }
        }
        long failures = 0;
        for (ListenerInfo listener : listeners) {
            failures += listener.getFailures();
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("События плагинов")).rows(MenuConstants.HOME_ROWS), MenuConstants.HOME_ROWS);
        builder.slot(4, MenuItems.icon(Icons.OVERVIEW, "NETHER_STAR", "События плагинов", Style.lore(
            CoreFormatting.wrapText("Так плагины сообщают друг другу, что произошло. Одни вызывают событие, другие слушают его и отвечают."),
            Fact.list("Событий", String.valueOf(events.size()), "Слушателей", String.valueOf(listeners.size()),
                "Вызовов сегодня", String.valueOf(calls), "Ошибок", failures > 0 ? "✖ " + failures : "✔ нет"))));
        listButtons(builder, events.size(), listeners.size());
        diagnosticButtons(builder, failures, data().tracedEvents().size());
        builder.button(MenuConstants.HOME_CLOSE, MenuItems.backItem(), go(routes::home));
        show(player, builder.build());
    }

    private void listButtons(MenuBuilder builder, int events, int listeners) {
        builder.button(20, MenuItems.section(Icons.UPDATES, "REDSTONE_TORCH", "Все события",
            "Какие события есть и как часто их вызывают.", new Fact("Всего", String.valueOf(events))),
            go(who -> events(who, BusFilter.ALL, 0)));
        builder.button(22, MenuItems.section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто и на какие события подписан.",
            new Fact("Всего", String.valueOf(listeners))),
            go(who -> listenerScreens.listeners(who, new ListenerQuery("Слушатели", routes::busHome), 0)));
        builder.button(24, MenuItems.section(Icons.RELEASES, "BOOKSHELF", "По плагинам", "Слушатели, сгруппированные по плагинам."),
            go(who -> listenerScreens.plugins(who, 0)));
    }

    private void diagnosticButtons(MenuBuilder builder, long failures, int traced) {
        builder.button(28, MenuItems.section(Icons.CLOCK, "CLOCK", "Медленные", "Слушатели, которые дольше всех работают."), go(who ->
            listenerScreens.listeners(who, new ListenerQuery("Медленные", routes::busHome, info -> info.getInvocations() > 0,
                new Comparator<ListenerInfo>() {
                    @Override
                    public int compare(ListenerInfo a, ListenerInfo b) {
                        return Long.compare(b.getAverageNanos(), a.getAverageNanos());
                    }
                }), 0)));
        builder.button(30, MenuItems.icon(Icons.PROBLEMS, "BARRIER", "Ошибки слушателей", Style.lore(
            Collections.singletonList("Слушатели, которые падали."), Fact.list("Ошибок", String.valueOf(failures)),
            Style.left("открыть")), failures > 0 ? Tone.DANGER : Tone.NORMAL), go(who ->
            listenerScreens.listeners(who, new ListenerQuery("Ошибки", routes::busHome, info -> info.getFailures() > 0,
                new Comparator<ListenerInfo>() {
                    @Override
                    public int compare(ListenerInfo a, ListenerInfo b) {
                        return Long.compare(b.getFailures(), a.getFailures());
                    }
                }), 0)));
        builder.button(32, MenuItems.section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записи вызовов",
            "Подробный разбор записанных вызовов.", new Fact("Записывается", String.valueOf(traced))),
            go(who -> routes.busTraces(who, null, 0)));
        builder.button(34, MenuItems.section(Icons.HISTORY, "BOOK", "История по дням",
            "Сколько раз вызывали события каждый день."), go(who -> routes.busDays(who, null, 0)));
        builder.button(38, MenuItems.icon(Icons.BACKUPS, "CHEST", "Выгрузить в файл", Style.lore(
            Collections.singletonList("Записать счётчики в CSV рядом с историей."), Collections.<Fact>emptyList(),
            Style.left("выгрузить"))), go(this::export));
    }

    private void events(MenuViewer player, BusFilter filter, int page) {
        BusFilter next = BusFilter.values()[(filter.ordinal() + 1) % BusFilter.values().length];
        List<EventInfo> shown = new ArrayList<>();
        for (EventInfo info : data().events()) {
            if (filter.keep(info)) {
                shown.add(info);
            }
        }
        Collections.sort(shown, new Comparator<EventInfo>() {
            @Override
            public int compare(EventInfo a, EventInfo b) {
                if (a.getLastCalledAt() != null && b.getLastCalledAt() != null) {
                    int byTime = b.getLastCalledAt().compareTo(a.getLastCalledAt());
                    if (byTime != 0) {
                        return byTime;
                    }
                } else if (a.getLastCalledAt() != null || b.getLastCalledAt() != null) {
                    return a.getLastCalledAt() == null ? 1 : -1;
                }
                return Long.compare(b.getCalls(), a.getCalls());
            }
        });
        List<Row> rows = new ArrayList<>();
        for (EventInfo info : shown) {
            rows.add(new Row(eventItem(info), goClick((who, click) -> {
                if (click == ClickType.RIGHT) {
                    switchTrace(info.getEventType());
                    events(who, filter, page);
                } else {
                    busEvent(who, info.getEventType(), v -> events(v, filter, page));
                }
            })));
        }
        List<String> labels = new ArrayList<>();
        for (BusFilter each : BusFilter.values()) {
            labels.add(each.getLabel());
        }
        ItemSpec cycle = MenuItems.icon(Icons.SETTINGS, "HOPPER", "Показать: " + filter.getLabel(), Style.lore(
            Collections.singletonList("Какие события показывать."), Collections.<Fact>emptyList(),
            Style.choices(labels, filter.ordinal()), Style.left("переключить")));
        list(player, "События: " + filter.getLabel(), rows, page, MenuConstants.PAGE_SIZE, (who, to) -> events(who, filter, to),
            routes::busHome, "Событий нет",
            builder -> builder.button(MenuConstants.FILTER, cycle, go(who -> events(who, next, 0))));
    }

    private ItemSpec eventItem(EventInfo info) {
        boolean recording = data().tracedEvents().contains(info.getEventType());
        EventAbout about = routes.aboutOf(info.getEventType());
        List<String> purpose = about != null && !about.getPurpose().trim().isEmpty()
            ? CoreFormatting.wrapText(about.getPurpose(), CoreFormatting.WRAP_WIDTH, 3) : Collections.<String>emptyList();
        List<Fact> parameters = new ArrayList<>();
        parameters.add(new Fact("Плагин", about != null && about.getOwner() != null
            ? routes.versioned(about.getOwner()) : "сервер или библиотека"));
        parameters.add(new Fact("Слушателей", String.valueOf(info.getListeners())));
        parameters.add(new Fact("Вызовов", String.valueOf(info.getCalls())));
        if (info.getLastCalledAt() != null) {
            parameters.add(new Fact("Последний", MenuText.clockStamp(info.getLastCalledAt()) + ", " + MenuText.ago(info.getLastCalledAt())));
        }
        if (info.getDenied() > 0) {
            parameters.add(new Fact("Отказов", String.valueOf(info.getDenied())));
        }
        parameters.add(new Fact("Запись", recording ? "● идёт" : "не ведётся"));
        boolean listened = info.getListeners() > 0;
        List<String> lore = Style.lore(purpose, parameters, Style.left("открыть событие"),
            Style.right(recording ? "остановить запись" : "начать запись"));
        return MenuItems.icon(null, listened ? "REDSTONE_TORCH" : "COAL", CoreFormatting.shortName(info.getEventType()), lore,
            listened ? "REDSTONE_TORCH_ON" : null, info.getDenied() > 0 ? Tone.WARN : Tone.NORMAL);
    }

    public void busEvent(MenuViewer player, String name, Consumer<MenuViewer> back) {
        EventInfo info = null;
        for (EventInfo candidate : data().events()) {
            if (candidate.getEventType().equals(name)) {
                info = candidate;
            }
        }
        if (info == null) {
            tell(player, ChatTone.INFO, "Такого события больше нет.");
            back.accept(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle(CoreFormatting.shortName(name))).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        builder.slot(4, eventCard(name, info));
        eventButtons(builder, name, info, back);
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private ItemSpec eventCard(String name, EventInfo info) {
        EventAbout about = routes.aboutOf(name);
        List<Fact> parameters = new ArrayList<>(routes.ownerParameters(about == null ? null : about.getOwner()));
        parameters.add(new Fact("Слушателей", String.valueOf(info.getListeners())));
        parameters.add(new Fact("Вызовов", String.valueOf(info.getCalls())));
        parameters.add(new Fact("Отказов", String.valueOf(info.getDenied())));
        parameters.add(new Fact("Последний вызов", info.getLastCalledAt() == null ? "с запуска не было"
            : MenuText.clockStamp(info.getLastCalledAt()) + ", " + MenuText.ago(info.getLastCalledAt())));
        parameters.addAll(eventDays(name));
        parameters.addAll(routes.labelled("Класс", name));
        if (about != null && !about.getUsage().trim().isEmpty()) {
            parameters.addAll(routes.labelled("Как использовать", about.getUsage()));
        }
        List<String> purpose = about != null && !about.getPurpose().trim().isEmpty()
            ? CoreFormatting.wrapText(about.getPurpose()) : Collections.singletonList("Автор плагина не описал это событие.");
        return MenuItems.icon(null, "NETHER_STAR", CoreFormatting.shortName(name), Style.lore(purpose, parameters));
    }

    private List<Fact> eventDays(String name) {
        LocalDate first = null;
        LocalDate last = null;
        for (EventDay day : data().eventHistory(name)) {
            if (first == null || day.getDate().isBefore(first)) {
                first = day.getDate();
            }
            if (last == null || day.getDate().isAfter(last)) {
                last = day.getDate();
            }
        }
        if (first == null) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now(DisplayZone.getZone());
        return Fact.list("Первый день", CoreFormatting.dayTitle(first, today), "Последний день", CoreFormatting.dayTitle(last, today));
    }

    private void eventButtons(MenuBuilder builder, String name, EventInfo info, Consumer<MenuViewer> back) {
        boolean recording = data().tracedEvents().contains(name);
        List<String> record = Style.lore(Collections.singletonList("Запоминать каждый вызов: кто ответил и что изменил."),
            Fact.list("Сейчас", recording ? "● запись идёт" : "не ведётся"), Style.left(recording ? "остановить" : "начать"));
        builder.button(20, MenuItems.icon(Icons.TOGGLE, "LEVER", "Запись вызовов", record, recording ? Tone.GOOD : Tone.NORMAL),
            go(who -> {
                switchTrace(name);
                busEvent(who, name, back);
            }));
        builder.button(22, MenuItems.section(Icons.PLUGINS, "COMPARATOR", "Слушатели", "Кто слушает это событие, по порядку вызова.",
            new Fact("Всего", String.valueOf(info.getListeners()))), go(who -> listenerScreens.listeners(who,
            new ListenerQuery("Слушатели " + CoreFormatting.shortName(name), v -> busEvent(v, name, back),
                listener -> listener.getEventType().equals(name)), 0)));
        builder.button(24, MenuItems.section(Icons.HISTORY, "BOOK", "История", "Вызовы этого события по дням."),
            go(who -> routes.busDays(who, name, 0)));
        builder.button(30, MenuItems.section(Icons.CHANGELOG, "WRITTEN_BOOK", "Записанные вызовы", "Что случалось при каждом вызове."),
            go(who -> routes.busTraces(who, name, 0)));
    }

    private void switchTrace(String name) {
        data().setTracing(name, !data().tracedEvents().contains(name));
    }

    private void export(MenuViewer player) {
        String file = data().exportEventHistoryCsv();
        if (file == null) {
            tell(player, ChatTone.FAILURE, "Выгрузка недоступна.", "История выключена или файл не записать.");
        } else {
            tell(player, ChatTone.SUCCESS, "Счётчики записаны.", file);
        }
    }
}
