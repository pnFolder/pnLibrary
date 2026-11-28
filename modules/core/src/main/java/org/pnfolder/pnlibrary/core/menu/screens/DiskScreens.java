package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.core.commands.modules.DiskReport;
import org.pnfolder.pnlibrary.core.commands.modules.DiskRow;
import org.pnfolder.pnlibrary.core.commands.modules.DiskUsage;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** The screen of the space plugins take on the disk. */
public final class DiskScreens extends MenuScreens {
    private static final int HALF = 50;

    public DiskScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** Every plugin by the space it takes, biggest first, with the split into jar, settings, libraries, logs and data. */
    public void disk(MenuViewer player, int page, Consumer<MenuViewer> back) {
        away(player, () -> {
            DiskReport found = null;
            Exception failure = null;
            try {
                found = data().disk();
            } catch (Exception error) {
                failure = error;
            }
            final DiskReport report = found;
            final Exception error = failure;
            sync(() -> show(player, page, back, report, error));
        });
    }

    private void show(MenuViewer player, int page, Consumer<MenuViewer> back, DiskReport report, Exception error) {
        if (report == null) {
            tell(player, ChatTone.FAILURE, "Не получилось посчитать диск", error == null ? null : error.getMessage());
            back.accept(player);
            return;
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(summary(report), go(who -> { })));
        for (DiskRow row : report.getRows()) {
            rows.add(new Row(item(row, report.getTotal()), go(who -> { })));
        }
        list(player, "Диск плагинов", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> disk(who, to, back), back, "Плагинов нет");
    }

    private static ItemSpec summary(DiskReport report) {
        return MenuItems.icon(null, "HOPPER", "Всего", Style.lore(
            Collections.singletonList("Плагины, их папки, настройки и библиотеки."),
            Fact.list("Занято", DiskUsage.format(report.getTotal()),
                "Библиотек pnLibrary", DiskUsage.format(report.getRegisteredLibraries()),
                "Свободно на диске", DiskUsage.format(report.getFree()),
                "Размер диска", DiskUsage.format(report.getCapacity()))));
    }

    private static ItemSpec item(DiskRow row, long total) {
        long share = total > 0 ? row.getTotal() * 100 / total : 0;
        return MenuItems.icon(null, "CHEST", row.getName(), Style.lore(
            Collections.singletonList("Доля: " + share + "% от всех плагинов"),
            Fact.list("Всего", DiskUsage.format(row.getTotal()),
                "Сам плагин", DiskUsage.format(row.getJar()),
                "Настройки", DiskUsage.format(row.getConfigs()),
                "Библиотеки", DiskUsage.format(row.getLibraries()),
                "Журналы", DiskUsage.format(row.getLogs()),
                "Данные", DiskUsage.format(row.getData()))),
            share >= HALF ? Tone.DANGER : Tone.NORMAL);
    }
}
