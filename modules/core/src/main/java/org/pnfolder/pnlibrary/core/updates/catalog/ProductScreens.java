package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.CommandHint;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/** The details card of one component; its settings card is {@link ProductSettingsScreen}. */
final class ProductScreens extends CatalogStyle {
    private final ProductSettingsScreen settingsScreen;

    ProductScreens(UpdateCatalogRoutes routes, Instant now, ZoneId zone) {
        super(routes, now, zone);
        this.settingsScreen = new ProductSettingsScreen(routes, now, zone);
    }

    List<Component> settings(UpdateSnapshot snapshot, @Nullable Instant heldUntil) {
        return settingsScreen.settings(snapshot, heldUntil);
    }

    /** Versions, release date, channels, automatic download, pause and requirements, each setting linked to settings. */
    List<Component> info(UpdateSnapshot snapshot, @Nullable Instant heldUntil) {
        UpdateCatalogRoutes routes = getRoutes();
        String product = snapshot.getProduct();
        String latest = snapshot.getLatestVersion();
        String update = latest != null && !latest.equals(snapshot.getCurrentVersion()) ? latest : null;
        List<Component> result = new ArrayList<>();
        result.add(header(product, text(CatalogLabels.stateLabel(snapshot.getState()), CatalogLabels.stateColor(snapshot.getState()))));
        String message = snapshot.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            result.add(note(message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message));
        }
        result.add(blank());
        Component version = field("Версия", versions(snapshot.getCurrentVersion(), update));
        String changelog = snapshot.getLatestChangelogUrl();
        if (changelog != null && safeLink(changelog) && update != null) {
            version = version.append(changelogButton(changelog));
        }
        result.add(version);
        Instant released = releasedAt(snapshot, update != null ? update : snapshot.getCurrentVersion());
        if (released != null) {
            result.add(field("Выпущена", text(released(released), ChatPalette.TEXT)));
        }
        result.add(field("Канал", channelTag(snapshot.getChannel()))
            .hoverEvent(tooltip(text(UpdateChannelLabels.hint(snapshot.getChannel()), ChatPalette.TEXT)))
            .append(change(product, "Выбрать другой канал обновлений")));
        Component others = otherChannels(snapshot);
        if (others != null) {
            result.add(others);
        }
        result.add(field("Автозагрузка", snapshot.getAutomaticDownload() ? text("включена", ChatPalette.OK) : text("выключена", ChatPalette.DANGER))
            .append(change(product, "Включить или выключить автоматическую загрузку")));
        boolean held = snapshot.getState() == UpdateState.FROZEN || heldUntil != null;
        result.add(field("Пауза", held ? text(holdText(heldUntil), ChatPalette.WARN) : text("нет", ChatPalette.TEXT))
            .append(change(product, "Приостановить обновления на время или снять паузу")));
        result.add(requirements(snapshot));
        result.add(blank());
        Function<String, String> builder = routes.getUpdate();
        String updateCommand = builder != null && snapshot.getState() == UpdateState.UPDATE_AVAILABLE ? builder.apply(product) : null;
        Component line = actions(
            updateCommand == null ? null : button("[Обновить]", updateCommand,
                "Скачать, проверить и подготовить обновление.\nПрименится после перезапуска.", ChatPalette.OK),
            button("[Настройки]", routes.settings(product), "Канал, автозагрузка и пауза обновлений", ChatPalette.ACCENT),
            button("[История]", routes.history(product), "События только этого плагина", ChatPalette.ACCENT),
            button("[Назад]", routes.getList(), "К списку плагинов", ChatPalette.SOFT));
        if (line != null) {
            result.add(line);
        }
        if (!routes.getInteractive()) {
            result.addAll(commandHints(new CommandHint(updateCommand, "обновить"),
                new CommandHint(routes.settings(product), "настройки"), new CommandHint(routes.history(product), "история плагина")));
        }
        return framed(result);
    }

    /** The newest release of every other channel; clicking one switches to its channel. */
    private @Nullable Component otherChannels(UpdateSnapshot snapshot) {
        List<Component> entries = new ArrayList<>();
        for (UpdateChannel channel : UpdateCatalogView.CHANNELS) {
            if (channel == snapshot.getChannel()) {
                continue;
            }
            ReleaseSummary release = newest(snapshot, channel);
            if (release == null) {
                continue;
            }
            Component entry = channelTag(channel).append(text(" " + version(release.getVersion()), ChatPalette.TEXT));
            List<Component> card = new ArrayList<>();
            card.add(text(UpdateChannelLabels.commandName(channel) + " " + version(release.getVersion()), UpdateChannelLabels.color(channel)));
            if (release.getPublishedAt() != null) {
                card.add(hoverField("Выпущена", text(released(release.getPublishedAt()), ChatPalette.TEXT)));
            }
            card.add(text(UpdateChannelLabels.hint(channel), ChatPalette.LABEL));
            if (isClickable()) {
                card.add(text("» Нажмите, чтобы переключиться на " + UpdateChannelLabels.commandName(channel), ChatPalette.LABEL));
            }
            Component shown = entry.hoverEvent(tooltip(card.toArray(new Component[0])));
            entries.add(isClickable() ? shown.clickEvent(ClickEvent.runCommand(
                getRoutes().set(snapshot.getProduct(), "channel", UpdateChannelLabels.commandName(channel)))) : shown);
        }
        if (entries.isEmpty()) {
            return null;
        }
        Component line = entries.get(0);
        for (Component entry : entries.subList(1, entries.size())) {
            line = line.append(dot()).append(entry);
        }
        return field("Другие каналы", line);
    }

    private static @Nullable ReleaseSummary newest(UpdateSnapshot snapshot, UpdateChannel channel) {
        List<ReleaseSummary> own = new ArrayList<>();
        for (ReleaseSummary release : snapshot.getAvailableReleases()) {
            if (release.getChannel() == channel) {
                own.add(release);
            }
        }
        if (own.isEmpty()) {
            return null;
        }
        return Collections.max(own, new Comparator<ReleaseSummary>() {
            @Override
            public int compare(ReleaseSummary a, ReleaseSummary b) {
                return (a.getPublishedAt() == null ? Instant.EPOCH : a.getPublishedAt())
                    .compareTo(b.getPublishedAt() == null ? Instant.EPOCH : b.getPublishedAt());
            }
        });
    }

    /** Java and API requirements; a Java version the server does not meet is shown in red with the actual one. */
    private Component requirements(UpdateSnapshot snapshot) {
        boolean javaMet = snapshot.getCurrentJava() >= snapshot.getRequiredJava();
        Component value = text("Java " + snapshot.getRequiredJava() + "+", javaMet ? ChatPalette.TEXT : ChatPalette.DANGER);
        if (!javaMet) {
            value = value.append(text(" (на сервере " + snapshot.getCurrentJava() + ")", ChatPalette.DANGER));
        }
        if (snapshot.getSupportedApi() != null) {
            value = value.append(dot()).append(text("API " + snapshot.getSupportedApi().getMinimum() + "–"
                + snapshot.getSupportedApi().getMaximum(), ChatPalette.TEXT));
        }
        return field("Требует", value).hoverEvent(tooltip(text("На сервере Java " + snapshot.getCurrentJava(), ChatPalette.TEXT)));
    }

    String holdText(@Nullable Instant until) {
        return until == null ? "не задана" : "до " + fullTime(until) + " · " + RussianText.remaining(Duration.between(getNow(), until));
    }

    /** A small button that opens the settings card, shown after the setting it changes. */
    private Component change(String product, String hint) {
        return !isClickable() ? Component.empty()
            : text(" ").append(button("[изменить]", getRoutes().settings(product), hint, ChatPalette.ACCENT));
    }
}
