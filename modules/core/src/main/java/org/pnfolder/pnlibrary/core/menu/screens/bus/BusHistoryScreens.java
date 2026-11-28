package org.pnfolder.pnlibrary.core.menu.screens.bus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Menu screens listing recent event bus history. */
public final class BusHistoryScreens extends MenuScreens {
    private static final int TRACE_STEPS = 6;

    public BusHistoryScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    private Consumer<MenuViewer> eventBack(@Nullable String event) {
        return event == null ? routes::busHome : who -> routes.busEvent(who, event, routes::busHome);
    }

    public void busDays(MenuViewer player, @Nullable String event, int page) {
        LocalDate today = LocalDate.now(DisplayZone.getZone());
        Map<LocalDate, List<EventDay>> byDate = new LinkedHashMap<>();
        List<EventDay> all = new ArrayList<>(data().eventHistory(event));
        Collections.sort(all, (a, b) -> b.getDate().compareTo(a.getDate()));
        for (EventDay day : all) {
            List<EventDay> entries = byDate.get(day.getDate());
            if (entries == null) {
                entries = new ArrayList<>();
                byDate.put(day.getDate(), entries);
            }
            entries.add(day);
        }
        long busiest = 1L;
        boolean first = true;
        for (List<EventDay> entries : byDate.values()) {
            busiest = first ? calls(entries) : Math.max(busiest, calls(entries));
            first = false;
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EventDay>> entry : byDate.entrySet()) {
            rows.add(dayRow(entry.getKey(), entry.getValue(), event, busiest, today, page));
        }
        String title = event == null ? "История событий" : "История " + CoreFormatting.shortName(event);
        String empty = data().eventHistoryDays() <= 0 ? "История выключена в коде" : "Вызовов за это время не было";
        list(player, title, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> busDays(who, event, to), eventBack(event), empty);
    }

    private static long calls(List<EventDay> entries) {
        long total = 0;
        for (EventDay day : entries) {
            total += day.getCalls();
        }
        return total;
    }

    private Row dayRow(LocalDate date, List<EventDay> entries, String event, long busiest, LocalDate today, int page) {
        long total = calls(entries);
        long denied = 0;
        for (EventDay day : entries) {
            denied += day.getDenied();
        }
        List<Fact> parameters = Fact.list("Вызовов", String.valueOf(total));
        if (event == null) {
            parameters.add(new Fact("Событий", String.valueOf(entries.size())));
        }
        if (denied > 0) {
            parameters.add(new Fact("Отказов", String.valueOf(denied)));
        }
        parameters.add(new Fact("Нагрузка", MenuText.progressBar(total, busiest)));
        String hint = Style.left(event == null ? "события этого дня" : "открыть событие");
        MenuClickHandler click = event == null
            ? go(who -> busDay(who, date, 0, page))
            : go(who -> routes.busEvent(who, event, v -> busDays(v, event, page)));
        return new Row(MenuItems.icon(null, "PAPER", CoreFormatting.dayTitle(date, today),
            Style.lore(Collections.emptyList(), parameters, hint)), click);
    }

    private void busDay(MenuViewer player, LocalDate date, int page, int daysPage) {
        List<EventDay> entries = new ArrayList<>();
        for (EventDay day : data().eventHistory(null)) {
            if (day.getDate().equals(date)) {
                entries.add(day);
            }
        }
        Collections.sort(entries, new Comparator<EventDay>() {
            @Override
            public int compare(EventDay a, EventDay b) {
                return Long.compare(b.getCalls(), a.getCalls());
            }
        });
        long busiest = entries.isEmpty() ? 1L : entries.get(0).getCalls();
        List<Row> rows = new ArrayList<>();
        for (EventDay day : entries) {
            List<Fact> parameters = Fact.list("Вызовов", String.valueOf(day.getCalls()));
            if (day.getDenied() > 0) {
                parameters.add(new Fact("Отказов", String.valueOf(day.getDenied())));
            }
            parameters.add(new Fact("Нагрузка", MenuText.progressBar(day.getCalls(), busiest)));
            rows.add(new Row(MenuItems.icon(null, "PAPER", CoreFormatting.shortName(day.getEventType()),
                Style.lore(Collections.emptyList(), parameters, Style.left("открыть событие")),
                day.getDenied() > 0 ? Tone.WARN : Tone.NORMAL),
                go(who -> routes.busEvent(who, day.getEventType(), v -> busDay(v, date, page, daysPage)))));
        }
        list(player, CoreFormatting.dayTitle(date, LocalDate.now(DisplayZone.getZone())), rows, page, MenuConstants.PAGE_SIZE,
            (who, to) -> busDay(who, date, to, daysPage), who -> busDays(who, null, daysPage), "В этот день вызовов не было");
    }

    public void busTraces(MenuViewer player, @Nullable String event, int page) {
        List<EventTrace> all = new ArrayList<>();
        for (EventTrace trace : data().traces()) {
            if (event == null || trace.getEventName().equals(event)) {
                all.add(trace);
            }
        }
        Collections.reverse(all);
        List<Row> rows = new ArrayList<>();
        for (EventTrace trace : all) {
            rows.add(new Row(traceItem(trace), go(who -> busTrace(who, trace, 0, v -> busTraces(v, event, page)))));
        }
        ItemSpec clear = MenuItems.icon(Icons.RESET, "TNT", "Очистить записи", Style.lore(
            Collections.singletonList("Забыть все записанные вызовы."), Collections.emptyList(), Style.left("очистить")),
            Tone.DANGER);
        list(player, "Записанные вызовы", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> busTraces(who, event, to),
            eventBack(event), "Пока ничего не записано. Включите запись на странице события.", builder -> {
                if (!all.isEmpty()) {
                    builder.button(MenuConstants.FILTER, clear, go(who -> {
                        data().clearTraces();
                        busTraces(who, event, 0);
                    }));
                }
            });
    }

    private static String owner(EventTrace.Step step) {
        return step.getModule() == null ? step.getPlugin().toString() : step.getPlugin() + "/" + step.getModule();
    }

    private static ItemSpec traceItem(EventTrace trace) {
        List<Fact> parameters = new ArrayList<>();
        if (trace.getAt() != null) {
            parameters.add(new Fact("Когда", MenuText.clockStamp(trace.getAt())));
            parameters.add(new Fact("", MenuText.ago(trace.getAt())));
        }
        parameters.add(new Fact("Результат", trace.getResult() == null ? "-" : trace.getResult()));
        parameters.add(new Fact("Режим", trace.getMode() == org.pnfolder.pnlibrary.api.events.bus.DispatchMode.ASYNC
            ? "async, мир не трогать" : "sync"));
        parameters.add(new Fact("Поток", trace.getThread()));
        parameters.add(new Fact("Ответили", String.valueOf(trace.getSteps().size())));
        List<EventTrace.Step> steps = trace.getSteps();
        for (EventTrace.Step step : steps.subList(0, Math.min(TRACE_STEPS, steps.size()))) {
            String action;
            if (step.getSkipped()) {
                action = "пропущен";
            } else if (step.getFailed()) {
                action = "упал";
            } else if (step.getChangedResult()) {
                action = step.getResultBefore() + " → " + step.getResultAfter();
            } else {
                action = "без изменений";
            }
            parameters.add(new Fact("", owner(step) + " · " + action));
        }
        String verdict = trace.getAllowed() ? "разрешено" : "отклонено";
        return MenuItems.icon(null, "PAPER", CoreFormatting.shortName(trace.getEventName()) + ": " + verdict,
            Style.lore(Collections.emptyList(), parameters, Style.left("все ответы")),
            trace.getAllowed() ? Tone.NORMAL : Tone.DANGER);
    }

    private void busTrace(MenuViewer player, EventTrace trace, int page, Consumer<MenuViewer> back) {
        List<Row> rows = new ArrayList<>();
        List<EventTrace.Step> steps = trace.getSteps();
        for (int index = 0; index < steps.size(); index++) {
            EventTrace.Step step = steps.get(index);
            String action;
            if (step.getSkipped()) {
                action = "пропущен";
            } else if (step.getFailed()) {
                action = "упал";
            } else if (step.getChangedResult()) {
                action = "изменил результат";
            } else {
                action = "без изменений";
            }
            List<Fact> parameters = Fact.list("Плагин", routes.ownerLine(step.getPlugin().toString()),
                "Порядок", (index + 1) + " из " + steps.size(), "Приоритет", String.valueOf(step.getPriority()), "Что сделал", action);
            if (step.getChangedResult()) {
                parameters.add(new Fact("Результат", step.getResultBefore() + " → " + step.getResultAfter()));
            }
            if (!step.getSkipped()) {
                parameters.add(new Fact("Время", CoreFormatting.nanosText(step.getNanos())));
            }
            rows.add(new Row(MenuItems.icon(null, "PAPER", owner(step), Style.lore(Collections.emptyList(), parameters),
                Tone.of(step.getFailed(), step.getChangedResult(), false, step.getSkipped()))));
        }
        String title = CoreFormatting.shortName(trace.getEventName())
            + (trace.getAt() == null ? "" : ", " + MenuText.clockStamp(trace.getAt()));
        list(player, title.length() > MenuConstants.TITLE_LIMIT ? title.substring(0, MenuConstants.TITLE_LIMIT) : title, rows, page,
            MenuConstants.PAGE_SIZE, (who, to) -> busTrace(who, trace, to, back), back, "Никто не отвечал");
    }
}
