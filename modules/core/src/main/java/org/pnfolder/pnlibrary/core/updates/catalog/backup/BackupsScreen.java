package org.pnfolder.pnlibrary.core.updates.catalog.backup;

import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import org.pnfolder.pnlibrary.core.updates.catalog.screens.CatalogStyle;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.updates.history.backup.BackupItemText;

/** Retained rollback sets with their age; paths and file contents are never shown. */
public final class BackupsScreen extends CatalogStyle {
    public BackupsScreen(UpdateCatalogRoutes routes, Instant now, ZoneId zone) {
        super(routes, now, zone);
    }

    /** One page of backups matching {@code query}. */
    public List<Component> backups(List<UpdateBackup> all, int page, BackupQuery query) {
        if (page <= 0) {
            throw new IllegalArgumentException("Page must be positive");
        }
        UpdateCatalogRoutes routes = getRoutes();
        List<UpdateBackup> backups = query.apply(all);
        List<UpdateBackup> selected = onPage(backups, page);
        String counted = RussianText.counted(backups.size(), "набор", "набора", "наборов");
        List<Component> result = new ArrayList<>();
        result.add(header("Резервы", text(query.isDefault() ? counted : counted + " из " + all.size(), ChatPalette.SOFT)));
        result.add(note("Сохранённые версии плагинов для отката."));
        result.add(note("Откат вернёт версии из строки и применится после перезапуска. Снова обновиться: " + routes.getCheck() + "."));
        result.add(blank());
        result.addAll(filterLines(query));
        for (UpdateBackup backup : selected) {
            result.addAll(rows(backup));
        }
        if (selected.isEmpty()) {
            result.add(note(all.isEmpty() ? "Резервов пока нет: они появятся после обновления."
                : backups.isEmpty() ? "По этим условиям наборов нет." : "На этой странице пусто."));
        }
        result.add(blank());
        Component line = actions(
            button("[Плагины]", routes.getList(), "Все зарегистрированные плагины", ChatPalette.ACCENT),
            button("[История]", routes.getHistory(), "Кто, когда и что обновлял и откатывал", ChatPalette.ACCENT));
        if (line != null) {
            result.add(line);
        }
        Component pager = pager(page, pageCount(backups.size()), query.command(routes.getBackups()), "Предыдущая", "Следующая");
        if (pager != null) {
            result.add(pager);
        }
        return framed(result);
    }

    /** The active filters and the buttons that change the order or clear them; for a reader that cannot click, the commands. */
    private List<Component> filterLines(BackupQuery query) {
        UpdateCatalogRoutes routes = getRoutes();
        List<String> active = new ArrayList<>();
        if (query.getPlugin() != null) {
            active.add("плагин " + query.getPlugin());
        }
        if (query.getSinceText() != null) {
            active.add("с " + query.getSinceText());
        }
        active.add("порядок: " + query.getOrder().getTitle());
        List<Component> lines = new ArrayList<>();
        lines.add(text("  Условия: ", ChatPalette.LABEL).append(text(String.join(" · ", active), ChatPalette.TEXT)));
        List<Component> buttons = new ArrayList<>();
        for (BackupOrder order : BackupOrder.values()) {
            buttons.add(order == query.getOrder() ? selected(order.getTitle(), ChatPalette.ACCENT)
                : button("[" + order.getTitle() + "]", query.withOrder(order).command(routes.getBackups()),
                    "Порядок: " + order.getTitle(), ChatPalette.ACCENT));
        }
        buttons.add(query.isDefault() ? null : button("[Сбросить]", routes.getBackups(), "Все наборы, сначала новые", ChatPalette.WARN));
        Component line = actions(buttons.toArray(new Component[0]));
        if (line != null) {
            lines.add(line);
        }
        if (!routes.getInteractive()) {
            String filters = "[plugin <плагин>] [since " + BackupQuery.SINCE_HELP + "] [sort new|old|plugin] [страница]";
            lines.add(text("  Фильтры: " + routes.getBackups() + " " + filters, ChatPalette.LABEL));
        }
        return lines;
    }

    private String versionOf(@Nullable String value) {
        return value == null ? "?" : version(value);
    }

    /** A set's versions and age with buttons that open the rollback confirmations, never the rollback itself. */
    private List<Component> rows(UpdateBackup backup) {
        UpdateCatalogRoutes routes = getRoutes();
        List<String> restores = new ArrayList<>();
        for (UpdateBackupComponent component : backup.getComponents()) {
            restores.add(component.getProduct() + " " + versionOf(component.getBackupVersion()));
        }
        Instant created = backup.getCreatedAt();
        List<Component> card = new ArrayList<>();
        card.add(text("Резервный набор", ChatPalette.TEXT));
        card.add(hoverField("ID", text(backup.getId().toString(), ChatPalette.LABEL)));
        card.add(hoverField("Создан", text(created == null ? "дата не записана" : fullTime(created) + " · " + age(created), ChatPalette.TEXT)));
        for (UpdateBackupComponent component : backup.getComponents()) {
            card.add(hoverField("Вернёт", text(component.getProduct() + " " + versionOf(component.getBackupVersion()), ChatPalette.TEXT)
                .append(text(" (сейчас " + versionOf(component.getUpdatedVersion()) + ")", ChatPalette.SOFT))));
        }
        for (UpdateBackupItem item : backup.getItems()) {
            card.add(hoverField("Сохранено", text(BackupItemText.describe(item), ChatPalette.TEXT)));
        }
        Component row = text("  ").append(text(String.join(", ", restores), ChatPalette.TEXT))
            .append(text("  ")).append(text(created == null ? "дата не записана" : age(created), ChatPalette.SOFT))
            .hoverEvent(tooltip(card.toArray(new Component[0])))
            .append(spaced(button("[Откат]", routes.rollback(backup.getId()), "Открыть подтверждение отката.\nКонфигурации не меняются.",
                ChatPalette.WARN)));
        if (backup.getConfigurationFiles() > 0) {
            row = row.append(spaced(button("[+конфиги]", routes.rollback(backup.getId(), true),
                "Открыть подтверждение отката JAR\nи выбранных конфигураций.", ChatPalette.ORANGE)));
        }
        List<Component> rows = new ArrayList<>();
        rows.add(row);
        if (!routes.getInteractive()) {
            rows.add(text("      откат: " + routes.rollback(backup.getId()), ChatPalette.LABEL));
            if (backup.getConfigurationFiles() > 0) {
                rows.add(text("      с конфигами: " + routes.rollback(backup.getId(), true), ChatPalette.LABEL));
            }
        }
        return rows;
    }
}
