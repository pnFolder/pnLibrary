package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.CommandHint;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** The plugin list, the server status card and the join notice: pages built from catalog rows. */
final class CatalogScreen extends CatalogStyle {
    CatalogScreen(UpdateCatalogRoutes routes, Instant now, ZoneId zone) {
        super(routes, now, zone);
    }

    private static List<UpdateSnapshot> sorted(List<UpdateSnapshot> snapshots) {
        List<UpdateSnapshot> copy = new ArrayList<>(snapshots);
        Collections.sort(copy, new Comparator<UpdateSnapshot>() {
            @Override
            public int compare(UpdateSnapshot a, UpdateSnapshot b) {
                return a.getProduct().toLowerCase(Locale.ROOT).compareTo(b.getProduct().toLowerCase(Locale.ROOT));
            }
        });
        return copy;
    }

    private static void addIfPresent(List<Component> result, Component component) {
        if (component != null) {
            result.add(component);
        }
    }

    /** One page of the plugin list, optionally only those with pending updates. */
    List<Component> catalog(List<UpdateSnapshot> snapshots, int page, boolean updatesOnly) {
        if (page <= 0) {
            throw new IllegalArgumentException("Page must be positive");
        }
        UpdateCatalogRoutes routes = getRoutes();
        List<UpdateSnapshot> chosen = new ArrayList<>();
        for (UpdateSnapshot snapshot : snapshots) {
            if (!updatesOnly || CatalogLabels.PENDING.contains(snapshot.getState())) {
                chosen.add(snapshot);
            }
        }
        List<UpdateSnapshot> selected = sorted(chosen);
        List<UpdateSnapshot> entries = onPage(selected, page);
        List<Component> result = new ArrayList<>();
        result.add(header(updatesOnly ? "Обновления" : "Каталог", summary(snapshots)));
        if (routes.getInteractive() && !entries.isEmpty()) {
            result.add(note("Нажмите на плагин, чтобы открыть его карточку."));
        }
        result.add(blank());
        for (UpdateSnapshot entry : entries) {
            result.addAll(rows(entry));
        }
        if (entries.isEmpty()) {
            if (!selected.isEmpty()) {
                result.add(note("На этой странице пусто."));
            } else if (updatesOnly) {
                result.add(text("  Все плагины актуальны.", ChatPalette.OK));
            } else {
                result.add(note("Пока нет плагинов, зарегистрированных в pnLibrary."));
            }
        }
        result.add(blank());
        addIfPresent(result, actions(
            button("[Проверить]", routes.getCheck(), "Запросить новые версии у каталогов", ChatPalette.OK),
            updatesOnly ? button("[Все]", routes.getList(), "Все зарегистрированные плагины", ChatPalette.ACCENT)
                : button("[Обновления]", routes.getAvailable(), "Только доступные и подготовленные обновления", ChatPalette.ACCENT),
            button("[История]", routes.getHistory(), "Кто, когда и что обновлял", ChatPalette.ACCENT),
            button("[Резервы]", routes.getBackups(), "Сохранённые версии для отката", ChatPalette.ACCENT)));
        addIfPresent(result, pager(page, pageCount(selected.size()), updatesOnly ? routes.getAvailable() : routes.getList(),
            "Предыдущая", "Следующая"));
        if (!routes.getInteractive()) {
            Function<String, String> update = routes.getUpdate();
            result.addAll(commandHints(
                new CommandHint(routes.getCheck(), "проверить обновления"),
                new CommandHint(routes.getAvailable(), "только обновления"),
                new CommandHint(routes.getRoot() + " info <плагин>", "карточка плагина"),
                new CommandHint(routes.getRoot() + " settings <плагин>", "настройки"),
                new CommandHint(routes.getHistory(), "история"),
                new CommandHint(routes.getBackups(), "резервы"),
                new CommandHint(update == null ? null : update.apply("<плагин>"), "обновить")));
        }
        return framed(result);
    }

    /** The summary screen: key facts, counters and available actions. */
    List<Component> status(List<Pair<String, String>> facts, List<UpdateSnapshot> snapshots, @Nullable String support) {
        UpdateCatalogRoutes routes = getRoutes();
        List<Component> result = new ArrayList<>();
        result.add(header("Состояние", snapshots.isEmpty() ? null : summary(snapshots)));
        result.add(blank());
        for (Pair<String, String> fact : facts) {
            result.add(field(fact.getFirst(), text(fact.getSecond(), ChatPalette.TEXT)));
        }
        result.add(blank());
        if (snapshots.isEmpty()) {
            result.add(note("Пока нет плагинов, зарегистрированных в pnLibrary."));
        } else {
            for (UpdateSnapshot snapshot : sorted(snapshots)) {
                result.addAll(rows(snapshot));
            }
        }
        result.add(blank());
        addIfPresent(result, actions(
            button("[Каталог]", routes.getList(), "Все плагины с настройками и историей", ChatPalette.ACCENT),
            button("[Проверить]", routes.getCheck(), "Запросить новые версии у каталогов", ChatPalette.OK),
            support == null ? null : supportButton(support)));
        return framed(result);
    }

    /** The support card: a friendly line and the button that opens the link. */
    List<Component> support(String link) {
        List<Component> result = new ArrayList<>();
        result.add(header("Поддержка pnFolder"));
        result.add(blank());
        result.add(note("Если возникла проблема с плагином, напишите нам: мы всегда рады помочь."));
        result.add(blank());
        addIfPresent(result, actions(supportButton(link)));
        return framed(result);
    }

    /** Only the components with an available or prepared update, or nothing when there are none. */
    List<Component> announcement(List<UpdateSnapshot> snapshots, @Nullable String support) {
        UpdateCatalogRoutes routes = getRoutes();
        List<UpdateSnapshot> actionable = new ArrayList<>();
        for (UpdateSnapshot snapshot : snapshots) {
            if (snapshot.getState() == UpdateState.UPDATE_AVAILABLE || snapshot.getState() == UpdateState.UPDATE_STAGED) {
                actionable.add(snapshot);
            }
        }
        if (actionable.isEmpty()) {
            return new ArrayList<>();
        }
        actionable = sorted(actionable);
        List<Component> result = new ArrayList<>();
        result.add(header("Обновления pnFolder", summary(actionable)));
        result.add(blank());
        for (UpdateSnapshot snapshot : actionable) {
            result.addAll(rows(snapshot));
        }
        result.add(blank());
        addIfPresent(result, actions(
            button("[Обновления]", routes.getAvailable(), "Доступные и подготовленные обновления", ChatPalette.ACCENT),
            button("[Проверить]", routes.getCheck(), "Повторно проверить все обновления", ChatPalette.OK),
            support == null ? null : supportButton(support)));
        return framed(result);
    }

    /** Counters beside the title; zero counters are left out and nothing pending reads as all clear. */
    private Component summary(List<UpdateSnapshot> snapshots) {
        int available = 0;
        int staged = 0;
        int broken = 0;
        for (UpdateSnapshot snapshot : snapshots) {
            UpdateState state = snapshot.getState();
            available += state == UpdateState.UPDATE_AVAILABLE ? 1 : 0;
            staged += state == UpdateState.UPDATE_STAGED || state == UpdateState.DOWNLOADING ? 1 : 0;
            broken += CatalogLabels.PROBLEMS.contains(state) ? 1 : 0;
        }
        List<Component> chips = new ArrayList<>();
        chips.add(counter(snapshots.size(), "плагин", "плагина", "плагинов"));
        if (available > 0) {
            chips.add(counter(available, "к обновлению", "к обновлению", "к обновлению"));
        }
        if (staged > 0) {
            chips.add(counter(staged, "к перезапуску", "к перезапуску", "к перезапуску", ChatPalette.ORANGE));
        }
        if (broken > 0) {
            chips.add(counter(broken, "ошибка", "ошибки", "ошибок", ChatPalette.DANGER));
        }
        if (available + staged == 0 && !snapshots.isEmpty()) {
            chips.add(text("всё актуально", ChatPalette.OK));
        }
        Component line = chips.get(0);
        for (Component chip : chips.subList(1, chips.size())) {
            line = line.append(dot()).append(chip);
        }
        return line;
    }

    /** A plugin row: name, versions, channel and state words; the whole row opens the plugin card. */
    private List<Component> rows(UpdateSnapshot snapshot) {
        UpdateCatalogRoutes routes = getRoutes();
        String update = updateTarget(snapshot);
        String link = snapshot.getLatestChangelogUrl();
        String changelog = link != null && safeLink(link) && CatalogLabels.PENDING.contains(snapshot.getState()) ? link : null;
        Component row = text("  ").append(text(snapshot.getProduct(), ChatPalette.TEXT)).append(text(" "))
            .append(versions(snapshot.getCurrentVersion(), update, routes.getInteractive() ? changelog : null));
        if (snapshot.getChannel() != UpdateChannel.STABLE) {
            row = row.append(text("  ")).append(channelTag(snapshot.getChannel()));
        }
        if (snapshot.getState() != UpdateState.UP_TO_DATE && snapshot.getState() != UpdateState.UPDATE_AVAILABLE) {
            row = row.append(text("  ")).append(text(CatalogLabels.shortStateLabel(snapshot.getState()),
                CatalogLabels.stateColor(snapshot.getState())));
        }
        Function<String, String> command = routes.getUpdate();
        if (command != null && snapshot.getState() == UpdateState.UPDATE_AVAILABLE) {
            row = row.append(spaced(button("[Обновить]", command.apply(snapshot.getProduct()),
                "Скачать, проверить и подготовить " + version(update == null ? "" : update) + ".\nПрименится после перезапуска.",
                ChatPalette.OK)));
        }
        List<Component> card = card(snapshot, update);
        row = row.hoverEvent(tooltip(card.toArray(new Component[0])));
        if (routes.getInteractive()) {
            row = row.clickEvent(ClickEvent.runCommand(routes.info(snapshot.getProduct())));
        }
        List<Component> rows = new ArrayList<>();
        rows.add(row);
        if (!routes.getInteractive() && changelog != null) {
            rows.add(text("      изменения: " + changelog, ChatPalette.LABEL));
        }
        return rows;
    }

    private List<Component> card(UpdateSnapshot snapshot, String update) {
        List<Component> card = new ArrayList<>();
        card.add(text(snapshot.getProduct(), ChatPalette.TEXT));
        card.add(hoverField("Статус", text(CatalogLabels.stateLabel(snapshot.getState()), CatalogLabels.stateColor(snapshot.getState()))));
        card.add(hoverField("Версия", versions(snapshot.getCurrentVersion(), update)));
        Instant released = releasedAt(snapshot, update != null ? update : snapshot.getCurrentVersion());
        if (released != null) {
            card.add(hoverField("Выпущена", text(released(released), ChatPalette.TEXT)));
        }
        card.add(hoverField("Канал", channelTag(snapshot.getChannel())));
        card.add(hoverField("Автозагрузка", text(snapshot.getAutomaticDownload() ? "включена" : "выключена", ChatPalette.TEXT)));
        String message = snapshot.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            card.add(text(message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message, ChatPalette.LABEL));
        }
        if (getRoutes().getInteractive()) {
            card.add(blank());
            card.add(text("Нажмите, чтобы открыть карточку", ChatPalette.ACCENT));
        }
        return card;
    }
}
