package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryState;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/** The {@code libs} list and the {@code lib enable|disable|retry} switches of {@code /pn modules}. */
final class LibraryPages {
    private final PnLibrary library;

    LibraryPages(PnLibrary library) {
        this.library = library;
    }

    /** The libraries of a plugin ({@code plugin}) or of one of its modules ({@code plugin:module}). */
    private ModuleLibraries holderOf(String owner) {
        for (PluginRegistration plugin : library.getPlugins().all()) {
            if (plugin.getId().getValue().equalsIgnoreCase(owner)) {
                return plugin.getLibraries();
            }
            for (ModuleContext module : plugin.all()) {
                if ((plugin.getId().getValue() + ":" + module.getId().getValue()).equalsIgnoreCase(owner)) {
                    return module.getLibraries();
                }
            }
        }
        return null;
    }

    void changeLibrary(CommandContext context, String action, String owner, String coordinates) {
        ChatKit kit = CommandOutput.kit(context);
        ModuleLibraries holder = holderOf(owner);
        if (holder == null) {
            context.getSender().sendMessage(kit.note("Нет плагина или модуля «" + owner + "». Список: /pn modules libs",
                    ChatPalette.DANGER));
            return;
        }
        ModuleLibrary found = null;
        for (LibraryStatus status : holder.getStatuses()) {
            if (status.getLibrary().getCoordinates().equalsIgnoreCase(coordinates)) {
                found = status.getLibrary();
                break;
            }
        }
        if (found == null) {
            context.getSender().sendMessage(kit.note("У " + owner + " нет библиотеки «" + coordinates
                    + "». Список: /pn modules libs " + owner, ChatPalette.DANGER));
            return;
        }
        LibraryStatus status = "enable".equals(action) ? holder.setAutoLoad(found, true)
                : "disable".equals(action) ? holder.setAutoLoad(found, false) : holder.retry(found);
        context.getSender().sendMessage(kit.text(found.getTitle() + " " + found.getVersion() + ": ", ChatPalette.TEXT)
                .append(result(kit, status)));
    }

    private Component result(ChatKit kit, LibraryStatus status) {
        String text;
        TextColor color;
        if (status.isRestartRequired()) {
            text = "выключена: она уже используется, пропадёт после перезапуска сервера";
            color = ChatPalette.WARN;
        } else if (status.getState() == LibraryState.LOADED) {
            text = "загружена";
            color = ChatPalette.OK;
        } else if (status.getState() == LibraryState.DISABLED) {
            text = "выключена, при запуске сервера не загружается";
            color = ChatPalette.WARN;
        } else if (status.getState() == LibraryState.FAILED) {
            text = "ошибка: " + status.getError();
            color = ChatPalette.DANGER;
        } else {
            text = "не нужна на этой платформе";
            color = ChatPalette.FAINT;
        }
        return kit.text(text, color);
    }

    void libs(CommandContext context, String owner) {
        ChatKit kit = CommandOutput.kit(context);
        Map<String, List<LibraryRow>> byOwner = groupedByOwner(owner);
        int count = 0;
        for (List<LibraryRow> rows : byOwner.values()) {
            count += rows.size();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header("Библиотеки", kit.text(String.valueOf(count), ChatPalette.LABEL)));
        if (count == 0) {
            lines.add(kit.note(owner == null ? "Плагины не подключили библиотек." : "У " + owner + " нет библиотек.", ChatPalette.FAINT));
        }
        for (Map.Entry<String, List<LibraryRow>> entry : byOwner.entrySet()) {
            lines.add(kit.text(" " + entry.getKey(), ChatPalette.ACCENT));
            for (LibraryRow row : entry.getValue()) {
                lines.add(libraryLine(kit, row));
                if (row.getDescription() != null) {
                    lines.add(kit.text("      " + row.getDescription(), ChatPalette.LABEL));
                }
                if (row.getPlace() != null) {
                    lines.add(kit.text("      лежит: " + row.getPlace(), ChatPalette.FAINT));
                }
            }
        }
        CommandOutput.send(context, lines);
    }

    private Map<String, List<LibraryRow>> groupedByOwner(String owner) {
        Map<String, List<LibraryRow>> byOwner = new LinkedHashMap<>();
        for (LibraryRow row : LibraryRow.all(library)) {
            if (owner != null && !row.belongsTo(owner)) {
                continue;
            }
            List<LibraryRow> own = byOwner.get(row.getOwner());
            if (own == null) {
                own = new ArrayList<>();
                byOwner.put(row.getOwner(), own);
            }
            own.add(row);
        }
        return byOwner;
    }

    private Component libraryLine(ChatKit kit, LibraryRow row) {
        String scope = "ISOLATED".equals(row.getScope()) ? "отдельно" : "LIBRARY".equals(row.getScope()) ? "в pnLibrary" : "в плагине";
        Component line = kit.text("    " + row.getTitle(), ChatPalette.TEXT).append(kit.text(" " + row.getVersion(), ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(scope, ChatPalette.FAINT));
        if (row.getSize() >= 0) {
            line = line.append(kit.dot()).append(kit.text(DiskUsage.format(row.getSize()), ChatPalette.SOFT));
        }
        line = line.append(kit.dot()).append(LibraryRow.GIVEN.equals(row.getTrust())
                ? kit.text("сумма задана", ChatPalette.OK) : kit.text("jar не проверяется", ChatPalette.WARN));
        if (!row.getRequired()) {
            line = line.append(kit.dot()).append(kit.text("необязательная", ChatPalette.FAINT));
        }
        if ("DISABLED".equals(row.getState())) {
            line = line.append(kit.dot()).append(kit.text("выключена", ChatPalette.WARN));
        } else if ("FAILED".equals(row.getState())) {
            line = line.append(kit.dot()).append(kit.text("ошибка: " + row.getError(), ChatPalette.DANGER));
        } else if (row.getRestartRequired()) {
            line = line.append(kit.dot()).append(kit.text("выключится после перезапуска", ChatPalette.WARN));
        }
        if (row.getConflict()) {
            line = line.append(kit.dot()).append(kit.text("другая версия у другого плагина", ChatPalette.DANGER));
        }
        return line;
    }
}
