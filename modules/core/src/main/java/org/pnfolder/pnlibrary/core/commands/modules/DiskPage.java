package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/** {@code /pn modules disk}: what every plugin takes on the disk, by kind. */
final class DiskPage {
    private final PnLibrary library;

    DiskPage(PnLibrary library) {
        this.library = library;
    }

    void show(CommandContext context) {
        ChatKit kit = CommandOutput.kit(context);
        DiskReport report = DiskUsage.scan(DiskUsage.getPluginsFolder(), DiskUsage.registeredFiles(library));
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Диск плагинов", kit.text(DiskUsage.format(report.getTotal()), ChatPalette.LABEL)));
        for (DiskRow row : report.getRows()) {
            long share = report.getTotal() > 0 ? row.getTotal() * 100 / report.getTotal() : 0;
            lines.add(kit.text(" " + row.getName(), ChatPalette.ACCENT).append(kit.dot())
                    .append(kit.text(DiskUsage.format(row.getTotal()) + " (" + share + "%)",
                            share >= 50 ? ChatPalette.WARN : ChatPalette.SOFT)));
            lines.add(kit.text("    плагин " + DiskUsage.format(row.getJar()) + " · настройки " + DiskUsage.format(row.getConfigs())
                    + " · библиотеки " + DiskUsage.format(row.getLibraries()) + " · журналы " + DiskUsage.format(row.getLogs())
                    + " · данные " + DiskUsage.format(row.getData()), ChatPalette.FAINT));
        }
        lines.add(kit.blank());
        lines.add(kit.hoverField("Библиотеки pnLibrary", kit.text(DiskUsage.format(report.getRegisteredLibraries()), ChatPalette.TEXT)));
        lines.add(kit.hoverField("Свободно на диске", kit.text(DiskUsage.format(report.getFree()) + " из "
                + DiskUsage.format(report.getCapacity()), ChatPalette.TEXT)));
        CommandOutput.send(context, lines);
    }
}
