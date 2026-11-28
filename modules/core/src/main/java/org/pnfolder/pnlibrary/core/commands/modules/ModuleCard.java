package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.compat.MinecraftCompatibility;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleControl;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;

/** The card of one module in {@code /pn modules view}: state, switches, libraries and the Minecraft check. */
final class ModuleCard {
    private final PnLibrary library;

    ModuleCard(PnLibrary library) {
        this.library = library;
    }

    private Component libraryLine(ChatKit kit, ModuleLibrary item, boolean fromPlugin) {
        String suffix = fromPlugin ? " (от плагина)" : "";
        Component line = kit.text("    " + item.getTitle() + " " + item.getVersion() + suffix,
                fromPlugin ? ChatPalette.FAINT : ChatPalette.SOFT);
        return item.getDescription() == null ? line : line.append(kit.text(": " + item.getDescription(), ChatPalette.LABEL));
    }


    void show(CommandContext context, NamedModule found) {
        ChatKit kit = CommandOutput.kit(context);
        ModuleContext module = found.getModule();
        ModuleControl control = module.getControl();
        List<Component> lines = new ArrayList<>();
        lines.add(kit.blank());
        lines.add(kit.header(ModuleLabels.title(module),
                kit.bracketed(ModuleLabels.label(control.getState()), ModuleLabels.color(control.getState()))));
        lines.add(kit.hoverField("Имя", kit.text(found.getName(), ChatPalette.TEXT)));
        String about = module.getMetadata().getDescription();
        lines.add(kit.hoverField("Описание", kit.text(about == null ? "плагин не описал" : about, ChatPalette.TEXT)));
        lines.add(kit.hoverField("Переключается", kit.text(control.isControllable() ? "да" : "нет, плагин не дал методы включения",
                ChatPalette.TEXT)));
        lines.add(kit.hoverField("Включается с сервером", kit.text(control.getAutoStart() ? "да" : "нет", ChatPalette.TEXT)));
        libraryLines(kit, module, lines);
        minecraftLines(kit, module, lines);
        if (control.getLastError() != null) {
            Throwable error = control.getLastError();
            lines.add(kit.hoverField("Ошибка", kit.text(error.getMessage() == null ? error.getClass().getSimpleName()
                    : error.getMessage(), ChatPalette.DANGER)));
        }
        if (control.isControllable() && kit.getReader().isInteractive()) {
            lines.add(kit.blank());
            lines.add(buttons(kit, found.getName(), control));
        }
        CommandOutput.send(context, lines);
    }


    private void libraryLines(ChatKit kit, ModuleContext module, List<Component> lines) {
        List<ModuleLibrary> own = module.getLibraries().getLoaded();
        List<ModuleLibrary> shared = new ArrayList<>();
        for (PluginRegistration plugin : library.getPlugins().all()) {
            if (plugin.all().contains(module)) {
                shared = plugin.getLibraries().getLoaded();
                break;
            }
        }
        if (own.isEmpty() && shared.isEmpty()) {
            return;
        }
        lines.add(kit.hoverField("Библиотеки", kit.text(String.valueOf(own.size() + shared.size()), ChatPalette.TEXT)));
        for (ModuleLibrary item : own) {
            lines.add(libraryLine(kit, item, false));
        }
        for (ModuleLibrary item : shared) {
            lines.add(libraryLine(kit, item, true));
        }
    }


    private void minecraftLines(ChatKit kit, ModuleContext module, List<Component> lines) {
        MinecraftCompatibility check = module.getMinecraft();
        if (check.getDeclared() == null) {
            return;
        }
        TextColor color = check.isBlocked() ? ChatPalette.DANGER
                : check.getNote() != null ? ChatPalette.WARN : ChatPalette.OK;
        String server = check.getServer() == null ? "неизвестно" : check.getServer();
        lines.add(kit.hoverField("Minecraft", kit.text(check.getDeclared() + ", сервер " + server, color)));
        if (check.getNote() != null) {
            lines.add(kit.text("    " + check.getNote(), ChatPalette.LABEL));
        }
        if (check.isForced()) {
            lines.add(kit.text("    администратор разрешил запуск на любой версии", ChatPalette.WARN));
        }
    }


    private Component buttons(ChatKit kit, String name, ModuleControl control) {
        Component buttons = kit.text(" ");
        for (ModuleAction action : ModuleAction.values()) {
            buttons = buttons.append(kit.option(action.getLabel(), ModulesCommand.ROOT + " " + action.getWord() + " " + name, action.getHint(),
                    action.getColor())).append(kit.text(" "));
        }
        String toggleWord = control.getAutoStart() ? "off" : "on";
        String toggleLabel = control.getAutoStart() ? "Не включать с сервером" : "Включать с сервером";
        return buttons.append(kit.option(toggleLabel, ModulesCommand.ROOT + " auto " + name + " " + toggleWord, "Сохраняется между перезапусками",
                ChatPalette.SOFT));
    }
}
