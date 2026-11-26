package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.CommandHint;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.updates.install.ComponentOperation;

/** Durable update events newest first, grouped by day, with who did what in the hover cards. */
final class HistoryScreen extends CatalogStyle {
    HistoryScreen(UpdateCatalogRoutes routes, Instant now, ZoneId zone) {
        super(routes, now, zone);
    }

    /** What an event led to: a short label for the row and the full wording for its hover card. */
    private static final class Outcome {
        final String label;
        final TextColor color;
        final String detail;

        Outcome(String label, TextColor color, String detail) {
            this.label = label;
            this.color = color;
            this.detail = detail;
        }

        Outcome(String label, TextColor color) {
            this(label, color, label);
        }
    }

    /** One page of history entries grouped by day. */
    List<Component> history(List<UpdateHistoryEntry> entries, int page, @Nullable Integer totalPages, @Nullable String product) {
        if (page <= 0) {
            throw new IllegalArgumentException("Page must be positive");
        }
        UpdateCatalogRoutes routes = getRoutes();
        List<Component> result = new ArrayList<>();
        result.add(header("История", product == null ? null : text(product, ChatPalette.SOFT)));
        LocalDate day = null;
        for (UpdateHistoryEntry entry : entries) {
            LocalDate entryDay = entry.getOccurredAt().atZone(getZone()).toLocalDate();
            if (!entryDay.equals(day)) {
                day = entryDay;
                result.add(blank());
                result.add(section(dayLabel(entryDay)));
            }
            result.addAll(rows(entry, product == null));
        }
        if (entries.isEmpty()) {
            result.add(blank());
            result.add(note(page == 1 ? "Событий пока нет." : "На этой странице пусто."));
        }
        result.add(blank());
        String base = product == null ? routes.getHistory() : routes.history(product);
        int pages = totalPages != null ? totalPages : entries.size() >= UpdateCatalogView.PAGE_SIZE ? page + 1 : page;
        Component line = actions(
            product == null ? null : button("[Все события]", routes.getHistory(), "История всех плагинов", ChatPalette.ACCENT),
            product == null ? null : button("[Карточка]", routes.info(product), "Сведения о плагине " + product, ChatPalette.ACCENT),
            button("[Плагины]", routes.getList(), "Все зарегистрированные плагины", ChatPalette.ACCENT));
        if (line != null) {
            result.add(line);
        }
        Component pager = pager(page, pages, base, "Новее", "Старее");
        if (pager != null) {
            result.add(pager);
        }
        if (!routes.getInteractive()) {
            result.addAll(commandHints(
                new CommandHint(base + " <страница>", "страница"),
                new CommandHint(routes.getRoot() + " history plugin <плагин>", "события одного плагина"),
                new CommandHint(routes.getList(), "плагины")));
        }
        return framed(result);
    }

    private static String cut(String text) {
        return text.length() > MAX_LINE ? text.substring(0, MAX_LINE) : text;
    }

    /** One row per changed component; clicking a row filters the history to that component. */
    private List<Component> rows(UpdateHistoryEntry entry, boolean offerFilter) {
        Outcome outcome = outcome(entry);
        Component lead = text("    ");
        Component tail = text("  ").append(text(outcome.label, outcome.color));
        List<Component> rows = new ArrayList<>();
        if (entry.getChanges().isEmpty()) {
            String message = entry.getMessage();
            String body = message != null && !message.trim().isEmpty() ? cut(message) : CatalogLabels.operationLabel(entry.getOperation());
            rows.add(lead.append(text(body, ChatPalette.TEXT)).append(tail).hoverEvent(card(entry, outcome, null, false)));
            return rows;
        }
        for (ProductChange change : entry.getChanges()) {
            String product = change.getProduct().toString();
            Component row = lead.append(text(product + "  ", ChatPalette.TEXT)).append(transition(entry, change)).append(tail)
                .hoverEvent(card(entry, outcome, product, offerFilter));
            if (offerFilter && getRoutes().getInteractive()) {
                row = row.clickEvent(ClickEvent.runCommand(getRoutes().history(product)));
            }
            rows.add(row);
        }
        return rows;
    }

    private Component transition(UpdateHistoryEntry entry, ProductChange change) {
        String operation = entry.getOperation();
        if (operation.equals("ROLLBACK") || operation.equals("HEALTH_ROLLBACK")) {
            return text("откат к " + (change.getFrom() == null ? "не установлено" : version(change.getFrom().toString())), ChatPalette.SOFT);
        }
        if (operation.equals(ComponentOperation.ADDED)) {
            return text(version(change.getTo().toString()), ChatPalette.SOFT);
        }
        if (operation.equals(ComponentOperation.REMOVED) || operation.equals(ComponentOperation.SETTINGS_CHANGED)) {
            return text(version((change.getFrom() != null ? change.getFrom() : change.getTo()).toString()), ChatPalette.SOFT);
        }
        return versions(change.getFrom() == null ? "-" : change.getFrom().toString(), change.getTo().toString());
    }

    private HoverEvent<Component> card(UpdateHistoryEntry entry, Outcome outcome, @Nullable String product, boolean offerFilter) {
        List<Component> lines = new ArrayList<>();
        if (product != null) {
            lines.add(text(product, ChatPalette.TEXT));
        }
        lines.add(hoverField("Событие", text(CatalogLabels.operationLabel(entry.getOperation()), ChatPalette.TEXT)));
        lines.add(hoverField("Итог", text(outcome.detail, outcome.color)));
        lines.add(hoverField("Кто", text(entry.getActor().getName(), ChatPalette.TEXT)));
        lines.add(hoverField("Когда", text(fullTime(entry.getOccurredAt()) + " · " + entry.getOccurredAt().atOffset(ZoneOffset.UTC),
            ChatPalette.TEXT)));
        lines.add(hoverField("План", text(entry.getPlanId().toString(), ChatPalette.LABEL)));
        String message = entry.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            lines.add(text(message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message, ChatPalette.SOFT));
        }
        if (product != null && offerFilter) {
            lines.add(blank());
            lines.add(text("Показать только " + product, ChatPalette.ACCENT));
            lines.add(text(getRoutes().history(product), ChatPalette.LABEL));
        }
        return tooltip(lines.toArray(new Component[0]));
    }

    private Outcome outcome(UpdateHistoryEntry entry) {
        String operation = entry.getOperation();
        UpdateState state = entry.getState();
        String message = entry.getMessage();
        if (operation.equals(ComponentOperation.ADDED)) {
            return new Outcome("установлен", ChatPalette.OK);
        }
        if (operation.equals(ComponentOperation.REMOVED)) {
            return new Outcome("удалён", ChatPalette.DANGER);
        }
        if (operation.equals(ComponentOperation.VERSION_CHANGED)) {
            return new Outcome("изменена вручную", ChatPalette.ORANGE, "версия изменена вручную");
        }
        if (operation.equals(ComponentOperation.SETTINGS_CHANGED)) {
            return new Outcome(message != null && !message.trim().isEmpty() ? cut(message) : "настройки изменены", ChatPalette.WARN);
        }
        if (state == UpdateState.FAILED) {
            return new Outcome("ошибка", ChatPalette.DANGER);
        }
        if (operation.equals("HEALTH") && state == UpdateState.UP_TO_DATE) {
            return new Outcome("применено", ChatPalette.OK, "применено после перезапуска");
        }
        if (operation.equals("HEALTH_ROLLBACK") && state == UpdateState.ROLLED_BACK) {
            return new Outcome("подтверждён", ChatPalette.OK, "откат подтверждён после перезапуска");
        }
        if (operation.equals("ROLLBACK") || state == UpdateState.ROLLED_BACK) {
            return new Outcome("ждёт перезапуска", ChatPalette.ORANGE, "откат подготовлен, применится после перезапуска");
        }
        if (state == UpdateState.UPDATE_STAGED) {
            return new Outcome("подготовлено", ChatPalette.ORANGE, "подготовлено, применится после перезапуска");
        }
        if (state == UpdateState.UPDATE_AVAILABLE) {
            return new Outcome("доступна новая", ChatPalette.INFO, "доступна новая версия");
        }
        return new Outcome(CatalogLabels.shortStateLabel(state), CatalogLabels.stateColor(state), CatalogLabels.stateLabel(state));
    }
}
