package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Words and colours for module states, shared by {@code /pn modules} and the menu. */
public final class ModuleLabels {
    private ModuleLabels() {
    }

    @NotNull
    public static String label(@NotNull ModuleState state) {
        switch (state) {
            case ENABLED:
                return "работает";
            case DISABLED:
                return "выключен";
            default:
                return "ошибка";
        }
    }

    @NotNull
    public static TextColor color(@NotNull ModuleState state) {
        switch (state) {
            case ENABLED:
                return ChatPalette.OK;
            case DISABLED:
                return ChatPalette.FAINT;
            default:
                return ChatPalette.DANGER;
        }
    }

    /** What a module is called to a person: its own name from the metadata, then its id. */
    @NotNull
    public static String title(@NotNull ModuleContext module) {
        String name = module.getMetadata().getName();
        return name.trim().isEmpty() ? module.getId().getValue() : name;
    }

    /** Every module of every plugin with the text that names it in commands: {@code плагин:модуль}. */
    @NotNull
    public static List<NamedModule> named(@NotNull PnLibrary library) {
        List<NamedModule> modules = new ArrayList<NamedModule>();
        for (PluginRegistration plugin : library.getPlugins().all()) {
            for (ModuleContext module : plugin.all()) {
                modules.add(new NamedModule(plugin.getId().getValue() + ":" + module.getId().getValue(), module));
            }
        }
        Collections.sort(modules, (left, right) -> left.getName().compareTo(right.getName()));
        return modules;
    }
}
