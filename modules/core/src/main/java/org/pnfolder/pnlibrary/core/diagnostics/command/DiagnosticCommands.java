package org.pnfolder.pnlibrary.core.diagnostics.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Builds pnLibrary's diagnostics command without depending on a native command API. */
public final class DiagnosticCommands {
    private static final String DEFAULT_ROOT = "/pn debug";

    private DiagnosticCommands() {
    }

    /** The {@code debug} command, with usage lines that start with {@code /pn debug}. */
    @NotNull
    public static CommandDefinition create(@NotNull PnLibrary library) {
        return create(library, DEFAULT_ROOT);
    }

    /** The {@code debug} command, with usage lines that start with {@code root}. */
    @NotNull
    public static CommandDefinition create(@NotNull PnLibrary library, @NotNull String root) {
        DiagnosticCommandExecutor executor = new DiagnosticCommandExecutor(library);
        return CommandDefinition.command("debug", builder -> {
            builder.permission("pnlibrary.debug");
            builder.consoleBypassesPermission();
            builder.suggests(context -> suggestions(library, context));
            builder.executes(context -> run(executor, root, context));
        });
    }

    private static void run(DiagnosticCommandExecutor executor, String root, CommandContext context) {
        ChatKit kit = new ChatKit(ChatReader.of(context.getSender()));
        executor.execute(
            context.getArguments().toArray(new String[0]),
            false,
            AudienceExtensions.id(context.getSender()),
            context.getSender(),
            event -> {
                for (Component line : DiagnosticCommandRenderer.render(event, kit, root)) {
                    context.getSender().sendMessage(line);
                }
            });
    }

    private static List<String> suggestions(PnLibrary library, CommandContext context) {
        String prefix = context.getCurrentInput().toLowerCase(Locale.ROOT);
        Set<String> all = new LinkedHashSet<String>();
        all.add("all");
        for (PluginRegistration plugin : library.getPlugins().all()) {
            for (ModuleContext module : plugin.all()) {
                all.add(module.getKey().getValue());
            }
        }
        all.add("--local");
        List<String> matching = new ArrayList<String>();
        for (String candidate : all) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matching.add(candidate);
            }
        }
        return matching;
    }
}
