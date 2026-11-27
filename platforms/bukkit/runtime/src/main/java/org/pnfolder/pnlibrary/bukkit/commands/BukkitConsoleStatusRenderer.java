package org.pnfolder.pnlibrary.bukkit.commands;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/** Builds the detailed console-only pnLibrary status card. */
final class BukkitConsoleStatusRenderer {
    private static final SemanticVersion ZERO_VERSION = SemanticVersion.parse("0.0.0");
    private static final List<UpdateChannel> DISPLAYED_CHANNELS = Arrays.asList(
        UpdateChannel.STABLE, UpdateChannel.RC, UpdateChannel.BETA, UpdateChannel.ALPHA, UpdateChannel.DEV);
    private static final DateTimeFormatter UTC_DATE_TIME = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private BukkitConsoleStatusRenderer() {
    }

    static List<String> render(List<UpdateRegistration> entries) {
        ConsoleCard.Builder card = ConsoleCard.builder(ConsoleStyle.THEME, "СОСТОЯНИЕ PNFOLDER")
            .owner("^.^", PnLibraryIdentity.NAME, null, PnLibraryIdentity.VERSION, "библиотека платформы")
            .blank()
            .firstDetail("Продукт", PnLibraryIdentity.NAME)
            .detail("Назначение", "общая библиотека pnFolder")
            .detail("Платформа", "Bukkit / Paper")
            .detail("Ядро", Bukkit.getBukkitVersion())
            .lastDetail("Java", BukkitStatusLabels.javaRuntime())
            .blank()
            .divider("ОБНОВЛЕНИЯ")
            .blank();
        if (entries.isEmpty()) {
            card.lastItem("зарегистрированных обновлений нет");
        } else {
            renderRegisteredUpdates(card, entries);
        }
        return card.blank()
            .divider("ПРОВЕРКА")
            .blank()
            .firstDetail("Последняя проверка", UTC_DATE_TIME.format(Instant.now()))
            .lastDetail("Следующая проверка", "по расписанию библиотеки")
            .support(PnLibraryIdentity.SUPPORT_URL)
            .blank()
            .status(status(entries))
            .build()
            .render();
    }

    private static void renderRegisteredUpdates(ConsoleCard.Builder card, List<UpdateRegistration> entries) {
        UpdateSnapshot first = entries.get(0).getSnapshot();
        card.firstDetail("Выбранный канал", BukkitStatusLabels.channel(first))
            .detail("Доступные каналы", "Stable · RC · Beta · Alpha · Dev")
            .lastDetail("Установленная версия", first.getCurrentVersion())
            .blank()
            .divider("ПОСЛЕДНИЕ ВЕРСИИ")
            .blank();
        Map<String, UpdateSnapshot> selectedByProduct = new HashMap<>();
        for (UpdateRegistration entry : entries) {
            selectedByProduct.put(entry.getSnapshot().getProduct().toLowerCase(Locale.ROOT), entry.getSnapshot());
        }
        for (int index = 0; index < entries.size(); index++) {
            UpdateSnapshot snapshot = entries.get(index).getSnapshot();
            UpdateSnapshot selected = selectedByProduct.get(snapshot.getProduct().toLowerCase(Locale.ROOT));
            renderReleaseHistory(card, snapshot, selected == null ? null : selected.getLatestVersion());
            if (index != entries.size() - 1) {
                card.blank();
            }
        }
        UpdateSnapshot available = firstAvailable(entries);
        if (available == null) {
            card.blank().lastItem("Новых совместимых обновлений не найдено");
            return;
        }
        renderAvailable(card, available);
    }

    private static void renderAvailable(ConsoleCard.Builder card, UpdateSnapshot selected) {
        String release = selected.getReleaseUrl();
        card.blank()
            .divider("ДОСТУПНО ОБНОВЛЕНИЕ")
            .blank()
            .firstDetail("Установлена", selected.getCurrentVersion())
            .lastDetail("Новая версия", selected.getLatestVersion() != null ? selected.getLatestVersion() : "не указана")
            .blank()
            .divider("СВЕДЕНИЯ ОБ ОБНОВЛЕНИИ")
            .blank()
            .firstDetail("Канал", BukkitStatusLabels.channel(selected))
            .detail("Источник", release == null || release.trim().isEmpty() ? "не указан" : "GitHub Releases")
            .detail("Платформа", "Bukkit / Paper")
            .detail("Совместимость API", selected.getSupportedApi() != null
                ? selected.getSupportedApi().getMinimum() + "–" + selected.getSupportedApi().getMaximum() : "не указана")
            .detail("Java", selected.getRequiredJava() + "+")
            .detail("Почему выбрана", "версия новее и совместима")
            .lastDetail("Установка", selected.getAutomaticDownload() ? "автоматическая" : "вручную, через /pn update");
    }

    private static void renderReleaseHistory(ConsoleCard.Builder card, UpdateSnapshot snapshot, String selectedVersion) {
        List<ReleaseSummary> history = new ArrayList<>(snapshot.getAvailableReleases());
        mergeSelectedRelease(history, snapshot, selectedVersion != null ? selectedVersion : snapshot.getLatestVersion());
        List<ReleaseSummary> channels = new ArrayList<>();
        List<UpdateChannel> shown = new ArrayList<>();
        collectNewest(history, channels, shown);
        if (channels.isEmpty()) {
            card.firstDetail("Продукт", BukkitStatusLabels.product(snapshot.getProduct()))
                .lastDetail("Версия", snapshot.getLatestVersion() != null ? snapshot.getLatestVersion() : snapshot.getCurrentVersion());
            return;
        }
        card.blank();
        for (int index = 0; index < channels.size(); index++) {
            UpdateChannel channel = shown.get(index);
            ReleaseSummary release = channels.get(index);
            String title = UpdateChannelLabels.title(channel);
            card.section(title.isEmpty() ? title : title.substring(0, 1).toUpperCase(Locale.ROOT) + title.substring(1))
                .detail("Версия", ConsoleStyle.paint(release.getVersion(), UpdateChannelLabels.color(channel)))
                .lastDetail("Опубликована", release.getPublishedAt() != null
                    ? publishedAtLabel(release.getPublishedAt()) : "дата неизвестна");
            if (index != channels.size() - 1) {
                card.blank();
            }
        }
    }

    private static void collectNewest(List<ReleaseSummary> history, List<ReleaseSummary> releases,
                                      List<UpdateChannel> channels) {
        for (UpdateChannel channel : DISPLAYED_CHANNELS) {
            ReleaseSummary release = newest(history, channel);
            if (release != null) {
                releases.add(release);
                channels.add(channel);
            }
        }
    }

    private static ReleaseSummary newest(List<ReleaseSummary> history, UpdateChannel channel) {
        ReleaseSummary best = null;
        for (ReleaseSummary release : history) {
            if (release.getChannel() == channel && (best == null || best.getVersion().compareTo(release.getVersion()) < 0)) {
                best = release;
            }
        }
        return best;
    }

    private static void mergeSelectedRelease(List<ReleaseSummary> history, UpdateSnapshot snapshot, String latest) {
        if (latest == null) {
            return;
        }
        SemanticVersion parsedLatest = SemanticVersion.tryParse(latest);
        if (parsedLatest == null) {
            return;
        }
        ReleaseSummary current = newestBySemver(history, snapshot.getChannel());
        SemanticVersion parsedCurrent = current == null ? null : SemanticVersion.tryParse(current.getVersion());
        if (parsedCurrent == null || parsedLatest.compareTo(parsedCurrent) > 0) {
            for (int index = history.size() - 1; index >= 0; index--) {
                if (history.get(index).getChannel() == snapshot.getChannel()) {
                    history.remove(index);
                }
            }
            history.add(new ReleaseSummary(latest, snapshot.getChannel(), null));
        }
    }

    private static ReleaseSummary newestBySemver(List<ReleaseSummary> history, UpdateChannel channel) {
        ReleaseSummary current = null;
        SemanticVersion currentVersion = null;
        for (ReleaseSummary release : history) {
            if (release.getChannel() != channel) {
                continue;
            }
            SemanticVersion parsed = SemanticVersion.tryParse(release.getVersion());
            SemanticVersion key = parsed != null ? parsed : ZERO_VERSION;
            if (current == null || currentVersion.compareTo(key) < 0) {
                current = release;
                currentVersion = key;
            }
        }
        return current;
    }

    private static String status(List<UpdateRegistration> entries) {
        UpdateSnapshot available = firstAvailable(entries);
        if (available == null) {
            return "Все зарегистрированные компоненты актуальны";
        }
        return "Доступно обновление · " + BukkitStatusLabels.channel(available);
    }

    private static UpdateSnapshot firstAvailable(List<UpdateRegistration> entries) {
        for (UpdateRegistration entry : entries) {
            if (entry.getSnapshot().getState() == UpdateState.UPDATE_AVAILABLE) {
                return entry.getSnapshot();
            }
        }
        return null;
    }

    private static String publishedAtLabel(Instant instant) {
        return "опубликована " + RussianText.ago(Duration.between(instant, Instant.now())) + " · "
            + UTC_DATE_TIME.format(instant);
    }
}
