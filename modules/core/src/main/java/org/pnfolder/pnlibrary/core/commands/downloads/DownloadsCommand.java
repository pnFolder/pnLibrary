package org.pnfolder.pnlibrary.core.commands.downloads;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;

/**
 * {@code /pn downloads} shows the files plugins asked pnLibrary to download, with their state; {@code /pn downloads now}
 * starts all of them. Requires {@code pnlibrary.downloads}; the console always may. Missing plugins are in {@code /pn deps}.
 */
public final class DownloadsCommand {
    private static final String PERMISSION = "pnlibrary.downloads";

    private DownloadsCommand() {
    }

    /** The command; {@code plugins} is asked again on every run, so it may change. */
    @NotNull
    public static CommandDefinition create(@NotNull final Supplier<PluginRegistry> plugins) {
        return CommandDefinition.command("downloads", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Загрузки доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> list(plugins.get(), context));
            root.literal("now", now -> now.executes(context -> startAll(plugins.get(), context)));
        });
    }

    private static void list(PluginRegistry plugins, CommandContext context) {
        List<DeclaredDownload> all = declaredDownloads(plugins);
        context.getSender().sendMessage(Component.text("Загрузки: " + all.size(), ChatPalette.ACCENT));
        for (DeclaredDownload download : all) {
            context.getSender().sendMessage(row(download));
        }
        context.getSender().sendMessage(Component.text("Запустить все: /pn downloads now", ChatPalette.FAINT));
    }

    private static void startAll(PluginRegistry plugins, CommandContext context) {
        int started = 0;
        for (PluginRegistration plugin : plugins.all()) {
            for (ModuleContext module : plugin.all()) {
                DownloadRegistration downloads = module.getDownloads();
                if (downloads != null) {
                    downloads.downloadNow();
                    started++;
                }
            }
        }
        context.getSender().sendMessage(Component.text("Запущено наборов загрузок: " + started, ChatPalette.TEXT));
    }

    /** The downloads that every module of every plugin declared, with who asked. */
    @NotNull
    public static List<DeclaredDownload> declaredDownloads(@NotNull PluginRegistry plugins) {
        List<DeclaredDownload> all = new ArrayList<>();
        for (PluginRegistration plugin : plugins.all()) {
            for (ModuleContext module : plugin.all()) {
                DownloadRegistration downloads = module.getDownloads();
                if (downloads == null) {
                    continue;
                }
                for (DownloadSnapshot snapshot : downloads.snapshots()) {
                    all.add(new DeclaredDownload(plugin.getId().getValue() + "/" + module.getId().getValue(), snapshot));
                }
            }
        }
        return all;
    }

    private static Component row(DeclaredDownload download) {
        DownloadSnapshot snapshot = download.getSnapshot();
        String note = snapshot.getMessage() == null ? "" : "  " + snapshot.getMessage();
        return Component.text(snapshot.getKey(), ChatPalette.TEXT).append(Component.text(
                "  " + download.getOwner() + "  " + snapshot.getState().name().toLowerCase(Locale.ROOT) + note, ChatPalette.SOFT));
    }
}
