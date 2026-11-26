package org.pnfolder.pnlibrary.updates.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/** Renders automatic update notifications with the same card used by console status. */
public final class UpdateAnnouncementRenderer {
    private UpdateAnnouncementRenderer() {
    }

    /** Builds the announcement shown to administrators, without the latest versions of each registration. */
    @NotNull
    public static List<String> render(@NotNull UpdatePlanSnapshot snapshot, @NotNull ConsoleTheme theme) {
        return render(snapshot, theme, Collections.emptyList());
    }

    /** Builds the announcement shown to administrators. */
    @NotNull
    public static List<String> render(@NotNull UpdatePlanSnapshot snapshot, @NotNull ConsoleTheme theme,
                                      @NotNull List<UpdateSnapshot> registrations) {
        String[] texts = texts(snapshot.getState());
        if (texts == null) {
            return Collections.emptyList();
        }
        String subtitle = texts[0];
        String result = texts[1];
        ConsoleCard.Builder card = ConsoleCard.builder(theme, "ПРОВЕРКА ОБНОВЛЕНИЙ")
                .mascot("^.^", PnLibraryIdentity.NAME, subtitle)
                .blank();
        List<ProductChange> changes = snapshot.getPlan() == null
                ? Collections.emptyList() : snapshot.getPlan().getChanges();
        if (!changes.isEmpty()) {
            changesSection(card, snapshot, changes);
            latestSection(card, registrations);
        } else {
            reasonSection(card, snapshot);
        }
        card.blank().section("СОСТОЯНИЕ").lastItem(result).blank();
        if (snapshot.getState() == UpdateState.UPDATE_AVAILABLE) {
            card.section("ДЕЙСТВИЕ")
                    .lastItem("Автозагрузка выключена; используйте /pn update <плагин>")
                    .blank();
        }
        card.section("ПОДДЕРЖКА")
                .lastItem("Вопросы, ошибки и идеи — пишите нам в Discord: " + PnLibraryIdentity.SUPPORT_URL)
                .blank();
        return card.status(result).build().render();
    }

    private static String[] texts(UpdateState state) {
        switch (state) {
            case UPDATE_AVAILABLE:
                return new String[] {"найдены совместимые обновления", "Доступно обновление"};
            case UPDATE_STAGED:
                return new String[] {"обновление подготовлено", "Перезапустите сервер"};
            case BLOCKED:
                return new String[] {"обновление остановлено проверкой совместимости", "Обновление не установлено"};
            case FAILED:
                return new String[] {"проверка завершилась ошибкой", "Проверка не выполнена"};
            case ROLLED_BACK:
                return new String[] {"предыдущие версии подготовлены", "Нужен перезапуск сервера"};
            default:
                return null;
        }
    }

    private static void changesSection(ConsoleCard.Builder card, UpdatePlanSnapshot snapshot, List<ProductChange> changes) {
        card.section("ОБНОВЛЕНИЯ");
        for (int index = 0; index < changes.size(); index++) {
            ProductChange change = changes.get(index);
            ProductRelease release = releaseOf(snapshot, change);
            ArtifactDescriptor artifact = release == null || release.getArtifacts().isEmpty()
                    ? null : release.getArtifacts().get(0);
            String description = release != null && !release.getDescription().trim().isEmpty()
                    ? release.getDescription() : "не указано";
            boolean hasRepository = release != null && release.getRepository() != null
                    && !release.getRepository().trim().isEmpty();
            card.firstDetail("Плагин", change.getProduct().getValue())
                    .detail("Версия", (change.getFrom() == null ? "не установлена" : change.getFrom()) + " → " + change.getTo())
                    .detail("Канал", release == null ? "не указан" : UpdateChannelLabels.title(release.getChannel()))
                    .detail("Описание", description)
                    .detail("Источник", hasRepository ? "GitHub Releases" : "не указан")
                    .detail("Платформа", artifact == null ? "не указана" : artifact.getPlatform().getDisplayName())
                    .detail("Совместимость API", release == null
                            ? "не указан" : release.getSupportedApi().getMinimum() + "–" + release.getSupportedApi().getMaximum())
                    .detail("Java", artifact == null ? "не указана" : artifact.getMinimumJava() + "+")
                    .detail("Почему выбрана", "версия новее и совместима")
                    .lastDetail("Файл", artifact == null ? "файл не указан" : artifact.getFile());
            if (release != null && release.getChangelogUrl() != null) {
                card.detail("Изменения", release.getChangelogUrl());
            }
            if (index != changes.size() - 1) {
                card.blank();
            }
        }
    }

    private static ProductRelease releaseOf(UpdatePlanSnapshot snapshot, ProductChange change) {
        if (snapshot.getPlan() == null) {
            return null;
        }
        for (ProductRelease release : snapshot.getPlan().getSelected()) {
            if (release.getProduct().equals(change.getProduct())) {
                return release;
            }
        }
        return null;
    }

    private static void latestSection(ConsoleCard.Builder card, List<UpdateSnapshot> registrations) {
        List<UpdateSnapshot> histories = new ArrayList<>();
        for (UpdateSnapshot registration : registrations) {
            if (!registration.getAvailableReleases().isEmpty()) {
                histories.add(registration);
            }
        }
        if (histories.isEmpty()) {
            return;
        }
        card.blank().section("ПОСЛЕДНИЕ ВЕРСИИ");
        for (int index = 0; index < histories.size(); index++) {
            for (ReleaseSummary release : latestByChannel(histories.get(index))) {
                card.firstDetail(UpdateChannelLabels.title(release.getChannel()), release.getVersion())
                        .lastDetail("Опубликована", release.getPublishedAt() == null
                                ? "дата неизвестна" : release.getPublishedAt().toString());
            }
            if (index != histories.size() - 1) {
                card.blank();
            }
        }
    }

    private static List<ReleaseSummary> latestByChannel(UpdateSnapshot registration) {
        Map<Object, ReleaseSummary> latest = new LinkedHashMap<>();
        for (ReleaseSummary release : registration.getAvailableReleases()) {
            ReleaseSummary known = latest.get(release.getChannel());
            if (known == null || release.getVersion().compareTo(known.getVersion()) > 0) {
                latest.put(release.getChannel(), release);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private static void reasonSection(ConsoleCard.Builder card, UpdatePlanSnapshot snapshot) {
        if (!snapshot.getBlockers().isEmpty()) {
            card.section("ПРИЧИНА");
            for (int index = 0; index < snapshot.getBlockers().size(); index++) {
                String blocker = snapshot.getBlockers().get(index).toString();
                if (index == snapshot.getBlockers().size() - 1) {
                    card.lastItem(blocker);
                } else {
                    card.item(blocker);
                }
            }
        } else if (snapshot.getMessage() != null && !snapshot.getMessage().trim().isEmpty()) {
            card.section("ПРИЧИНА").lastItem(snapshot.getMessage());
        }
    }
}
