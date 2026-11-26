package org.pnfolder.pnlibrary.core.commands.deps;

import java.util.Objects;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandOutput;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependency;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/**
 * {@code /pn deps}: plugins that other plugins need and the server does not have.
 *
 * <pre>
 * /pn deps                     what is missing, with the links, and the code of each group
 * /pn deps install &lt;code&gt;     downloads that group now, even when automatic download is off
 * /pn deps auto on|off         whether missing plugins are downloaded without a command
 * </pre>
 *
 * The automatic switch is kept in the data folder and is the same one the game menu changes. Requires
 * {@code pnlibrary.deps}; the console always may use it.
 */
public final class DepsCommand {
    public static final String PERMISSION = "pnlibrary.deps";
    private static final String UNAVAILABLE = "Загрузки зависимостей здесь недоступны.";

    private final PnLibrary library;

    public DepsCommand(@NotNull PnLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("deps", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Зависимости плагинов доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(this::status);
            root.literal("install", install -> install.argument("code", ArgumentType.string(), code -> {
                code.suggests(context -> codes());
                code.executes(context -> install(context, context.<String>get("code")));
            }));
            root.literal("auto", auto -> {
                auto.literal("on", on -> on.executes(context -> switchAutomatic(context, true)));
                auto.literal("off", off -> off.executes(context -> switchAutomatic(context, false)));
            });
        });
    }

    private List<String> codes() {
        List<String> codes = new ArrayList<String>();
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads != null) {
            for (MissingGroup group : downloads.missingGroups()) {
                codes.add(group.getCode());
            }
        }
        return codes;
    }

    private void say(CommandContext context, String text, TextColor color) {
        ChatKit kit = CommandOutput.kit(context);
        context.getSender().sendMessage(kit.text(text, color));
    }

    private void status(CommandContext context) {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads == null) {
            say(context, UNAVAILABLE, ChatPalette.DANGER);
            return;
        }
        say(context, "Автозагрузка недостающих плагинов: " + (downloads.isAutomatic() ? "включена" : "выключена") + ".", ChatPalette.ACCENT);
        List<MissingGroup> groups = downloads.missingGroups();
        if (groups.isEmpty()) {
            say(context, "Всё нужное установлено.", ChatPalette.LABEL);
            return;
        }
        for (MissingGroup group : groups) {
            say(context, group.getOwner() + " просит:", ChatPalette.ACCENT);
            for (MissingDependency item : group.getItems()) {
                item(context, item);
            }
            say(context, group.getInstallable() ? "  Установить: /pn deps install " + group.getCode()
                    : "  Скачайте по ссылкам и положите в plugins.", ChatPalette.SOFT);
        }
    }

    private void item(CommandContext context, MissingDependency item) {
        String name = item.getTitle().equalsIgnoreCase(item.getPlugin()) ? item.getPlugin() : item.getTitle() + " (" + item.getPlugin() + ")";
        say(context, "  " + name + " " + item.getMinimum() + "+ · " + (item.getRequired() ? "обязательный" : "необязательный"),
                ChatPalette.TEXT);
        if (item.getDescription() != null) {
            say(context, "    " + item.getDescription(), ChatPalette.LABEL);
        }
        if (item.getNoFileForServer()) {
            say(context, "    для этого сервера готового файла нет, скачайте вручную", ChatPalette.LABEL);
        }
        if (item.getVariant() != null) {
            say(context, "    выбран файл: " + item.getVariant(), ChatPalette.LABEL);
        }
        List<URI> links = new ArrayList<URI>();
        if (item.getPage() != null) {
            links.add(item.getPage());
        }
        links.addAll(item.getSources());
        for (URI link : links) {
            say(context, "    " + link, ChatPalette.LABEL);
        }
    }

    private void install(final CommandContext context, String code) {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads == null) {
            say(context, UNAVAILABLE, ChatPalette.DANGER);
            return;
        }
        CompletionStage<List<DownloadSnapshot>> stage;
        try {
            stage = downloads.installGroup(code);
        } catch (IllegalArgumentException refused) {
            say(context, refused.getMessage() == null ? "Установить нельзя." : refused.getMessage(), ChatPalette.DANGER);
            return;
        }
        if (stage == null) {
            say(context, "Такого кода нет или всё уже скачано. Список: /pn deps", ChatPalette.DANGER);
            return;
        }
        say(context, "Скачиваю…", ChatPalette.LABEL);
        stage.whenComplete((shots, error) -> finished(context, shots, error));
    }

    private void finished(CommandContext context, List<DownloadSnapshot> shots, Throwable error) {
        DownloadSnapshot failed = null;
        for (DownloadSnapshot shot : shots == null ? new ArrayList<DownloadSnapshot>() : shots) {
            if (shot.getState() == DownloadState.FAILED) {
                failed = shot;
                break;
            }
        }
        if (error != null) {
            say(context, "Не вышло: " + error.getMessage(), ChatPalette.DANGER);
        } else if (failed != null) {
            say(context, "Не вышло: " + (failed.getMessage() == null ? "причина неизвестна" : failed.getMessage()), ChatPalette.DANGER);
        } else {
            say(context, "Готово. Перезапустите сервер, чтобы плагины загрузились.", ChatPalette.ACCENT);
        }
    }

    private void switchAutomatic(CommandContext context, boolean value) {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads == null) {
            say(context, UNAVAILABLE, ChatPalette.DANGER);
            return;
        }
        downloads.setAutomatic(value);
        say(context, value ? "Недостающие плагины теперь скачиваются сами." : "Автозагрузка выключена: ссылки и код будут в сообщении.",
                ChatPalette.ACCENT);
    }
}
