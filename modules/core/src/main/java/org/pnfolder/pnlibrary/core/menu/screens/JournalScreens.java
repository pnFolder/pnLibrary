package org.pnfolder.pnlibrary.core.menu.screens;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.menu.ClickType;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuText;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/** Menu screens for the journal of recorded problems. */
public final class JournalScreens extends MenuScreens {
    private static final List<String> LEVEL_CYCLE = Arrays.asList(null, "error", "warning", "info");
    private static final Map<String, String> LEVELS = levels();
    private static final int TRACE_LINES = 12;
    private static final int DETAILS_SHOWN = 8;
    private static final int TIMES_PREVIEW = 5;

    public JournalScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    private static Map<String, String> levels() {
        Map<String, String> levels = new LinkedHashMap<>();
        levels.put("error", "ошибки");
        levels.put("warning", "предупреждения");
        levels.put("info", "сведения");
        return levels;
    }

    private static String levelWord(@Nullable String level) {
        return level == null ? "все" : LEVELS.get(level);
    }

    public void journal(MenuViewer player, @Nullable String level, int page) {
        String title = "Журнал сервера" + (level == null ? "" : " " + LEVELS.get(level));
        int at = LEVEL_CYCLE.indexOf(level);
        String next = LEVEL_CYCLE.get((at + 1) % LEVEL_CYCLE.size());
        List<String> words = new ArrayList<>();
        for (String name : LEVEL_CYCLE) {
            words.add(levelWord(name));
        }
        ItemSpec cycle = MenuItems.icon(Icons.SETTINGS, "HOPPER", "Уровень: " + levelWord(level), Style.lore(
            Collections.singletonList("Какие события показывать."), Collections.<Fact>emptyList(), Style.choices(words, at),
            Style.left("переключить")));
        list(player, title, journalRows(level, who -> journal(who, level, page)), page, MenuConstants.PAGE_SIZE,
            (who, to) -> journal(who, level, to), routes::home, "За сутки событий нет",
            builder -> builder.button(MenuConstants.FILTER, cycle, go(who -> journal(who, next, 0))));
    }

    public List<Row> journalRows(@Nullable String level, Consumer<MenuViewer> back) {
        List<Row> rows = new ArrayList<>();
        for (JournalGroup group : data().journalGroups(level, MenuConstants.JOURNAL_HOURS, MenuConstants.JOURNAL_LIMIT)) {
            rows.add(new Row(journalItem(group), goClick((who, click) -> {
                if (click == ClickType.RIGHT) {
                    chat(who, "pn events times " + group.getId());
                } else if (click == ClickType.SHIFT_LEFT) {
                    chat(who, "pn events full " + group.getId());
                } else {
                    journalEvent(who, group.getId(), back);
                }
            })));
        }
        return rows;
    }

    private void chat(MenuViewer player, String command) {
        player.close();
        runCommand(player, command);
    }

    private static ItemSpec journalItem(JournalGroup group) {
        List<Fact> parameters = Fact.list("Источник", group.getSource(),
            "Когда", MenuText.clockTime(Instant.ofEpochMilli(group.getLastMillis())), "Повторов", String.valueOf(group.getCount()));
        String message = group.getMessage();
        List<String> lore = Style.lore(
            Collections.singletonList(message.length() > MenuConstants.MESSAGE_PREVIEW ? message.substring(0, MenuConstants.MESSAGE_PREVIEW) : message),
            parameters, Style.left("открыть событие"), Style.right("все повторы в чат"), Style.shiftLeft("полный текст в чат"));
        return MenuItems.icon(null, journalMaterial(group.getLevel()), LEVELS.get(group.getLevel()) + ": " + group.getSource(), lore,
            Tone.of(group.getLevel().equals("error"), group.getLevel().equals("warning"), false, false));
    }

    private void journalEvent(MenuViewer player, String id, Consumer<MenuViewer> back) {
        JournalDetail detail = data().journalDetail(id);
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.");
            back.accept(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle("Событие")).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        builder.slot(4, journalSummary(detail));
        if (detail.getStackTrace() != null) {
            builder.button(20, traceItem(detail.getStackTrace()), go(who -> chat(who, "pn events full " + id)));
        }
        eventTimesAndFacts(builder, detail, id, back);
        builder.button(30, MenuItems.icon(Icons.CHECK, "COMPASS", "Показать в чате", Style.lore(
            Collections.singletonList("То же самое, что команда /pn events view."), Collections.<Fact>emptyList(),
            Style.left("открыть в чате"))), go(who -> chat(who, "pn events view " + id)));
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private static ItemSpec journalSummary(JournalDetail detail) {
        Instant last = Instant.ofEpochMilli(detail.getTimes().get(0));
        List<Fact> facts = Fact.list("Важность", LEVELS.get(detail.getLevel()), "Источник", detail.getSource(),
            "Повторов", String.valueOf(detail.getTimes().size()), "Последний раз", MenuText.clockStamp(last), "", MenuText.ago(last));
        return MenuItems.icon(null, journalMaterial(detail.getLevel()), "Событие " + detail.getSource(),
            Style.lore(CoreFormatting.wrapText(detail.getMessage()), facts),
            Tone.of(detail.getLevel().equals("error"), detail.getLevel().equals("warning"), false, false));
    }

    private static ItemSpec traceItem(String trace) {
        List<String> danger = new ArrayList<>();
        for (String line : CoreFormatting.wrapText(trace, CoreFormatting.WRAP_WIDTH, TRACE_LINES)) {
            danger.add(Style.danger(line));
        }
        return MenuItems.icon(Icons.PROBLEMS, "BOOK", "Текст ошибки",
            Style.lore(Collections.<String>emptyList(), Collections.<Fact>emptyList(), danger, Style.left("весь текст в чат")), Tone.DANGER);
    }

    private void eventTimesAndFacts(MenuBuilder builder, JournalDetail detail, String id, Consumer<MenuViewer> back) {
        List<Instant> moments = new ArrayList<>();
        for (Long millis : detail.getTimes()) {
            moments.add(Instant.ofEpochMilli(millis));
        }
        int more = moments.size() - TIMES_PREVIEW;
        List<String> shown = new ArrayList<>();
        for (Instant moment : moments.subList(0, Math.min(TIMES_PREVIEW, moments.size()))) {
            shown.add(MenuText.clockStamp(moment));
        }
        List<String> extra = more > 0 ? Collections.singletonList(Style.description("и ещё " + more)) : Collections.<String>emptyList();
        String[] hints = more > 0 ? new String[] {Style.left("все " + moments.size() + " раз")} : new String[0];
        List<String> timesLore = Style.lore(shown, Fact.list("Пояс", DisplayZone.getZone().getId()), extra, hints);
        builder.button(22, MenuItems.icon(Icons.CLOCK, "CLOCK", "Когда случалось", timesLore, "WATCH"), go(who -> {
            if (more > 0) {
                journalTimes(who, id, 0, back);
            }
        }));
        if (!detail.getDetails().isEmpty()) {
            List<Fact> facts = new ArrayList<>();
            for (Map.Entry<String, String> entry : detail.getDetails().entrySet()) {
                if (facts.size() == DETAILS_SHOWN) {
                    break;
                }
                String value = entry.getValue();
                facts.add(new Fact(entry.getKey(), value.length() > MenuConstants.MESSAGE_PREVIEW
                    ? value.substring(0, MenuConstants.MESSAGE_PREVIEW) : value));
            }
            builder.slot(24, MenuItems.icon(Icons.CHANGELOG, "PAPER", "Подробности", Style.lore(Collections.<String>emptyList(), facts)));
        }
    }

    private void journalTimes(MenuViewer player, String id, int page, Consumer<MenuViewer> back) {
        JournalDetail detail = data().journalDetail(id);
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.");
            back.accept(player);
            return;
        }
        List<Row> rows = new ArrayList<>();
        int size = detail.getTimes().size();
        for (int index = 0; index < size; index++) {
            Instant moment = Instant.ofEpochMilli(detail.getTimes().get(index));
            List<String> lore = Style.lore(Collections.<String>emptyList(),
                Fact.list("Было", MenuText.ago(moment), "Номер", (size - index) + " из " + size));
            rows.add(new Row(MenuItems.icon(Icons.CLOCK, "CLOCK", MenuText.clockStamp(moment), lore, "WATCH")));
        }
        list(player, "Когда случалось", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> journalTimes(who, id, to, back),
            who -> journalEvent(who, id, back), "Времени нет");
    }

    private static String journalMaterial(String level) {
        switch (level) {
            case "error":
                return "BARRIER";
            case "warning":
                return "GOLD_INGOT";
            default:
                return "PAPER";
        }
    }
}
