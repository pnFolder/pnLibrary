package org.pnfolder.pnlibrary.core.scenario;

import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.placeholders.UsageVariant;

/**
 * {@code /pn actions [page]} lists the named actions, requirements and functions the plugins defined, with owner and
 * visibility; {@code /pn actions info <name>} shows how each one is written. Requires {@code pnlibrary.actions}; the console always may.
 */
public final class ActionsCommand {
    private static final String ACTIONS_PERMISSION = "pnlibrary.actions";
    private static final int PAGE_SIZE = 12;

    private ActionsCommand() {
    }

    public static @NotNull CommandDefinition create(@NotNull Supplier<List<ScenarioDefinitionInfo>> source) {
        return CommandDefinition.command("actions", command -> {
            command.permission(ACTIONS_PERMISSION);
            command.noPermissionMessage("Действия доступны администраторам. Нужно право " + ACTIONS_PERMISSION + ".");
            command.consoleBypassesPermission();
            command.executes(context -> list(source.get(), context, 1));
            command.argument("page", ArgumentType.integer(), page ->
                page.executes(context -> list(source.get(), context, context.<Integer>get("page"))));
            command.literal("info", info -> info.argument("name", ArgumentType.string(), name ->
                name.executes(context -> info(source.get(), context, context.<String>get("name")))));
        });
    }

    private static void list(List<ScenarioDefinitionInfo> all, CommandContext context, int requested) {
        int pages = Math.max((all.size() + PAGE_SIZE - 1) / PAGE_SIZE, 1);
        int page = Math.max(1, Math.min(requested, pages));
        context.getSender().sendMessage(Component.text(
            "Действия, требования и функции: " + all.size() + ", страница " + page + "/" + pages, ChatPalette.ACCENT));
        int from = Math.min((page - 1) * PAGE_SIZE, all.size());
        for (ScenarioDefinitionInfo info : all.subList(from, Math.min(from + PAGE_SIZE, all.size()))) {
            context.getSender().sendMessage(definitionRow(info));
        }
        context.getSender().sendMessage(Component.text("Как писать: /pn actions info <имя>", ChatPalette.FAINT));
    }

    /** One line of the list: the name, what it is, who owns it and who may use it. */
    public static @NotNull Component definitionRow(@NotNull ScenarioDefinitionInfo info) {
        String scope = info.getVisibility().equals("all") ? "" : "  [" + info.getVisibility() + "]";
        return Component.text(info.getName(), ChatPalette.TEXT)
            .append(Component.text("  " + kindTitle(info.getKind()) + "  " + info.getOwner() + scope, ChatPalette.SOFT));
    }

    private static String kindTitle(String kind) {
        switch (kind) {
            case "action":
                return "действие";
            case "requirement":
                return "требование";
            default:
                return "функция";
        }
    }

    private static void info(List<ScenarioDefinitionInfo> all, CommandContext context, String written) {
        String wanted = written.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.endsWith("()")) {
            wanted = wanted.substring(0, wanted.length() - 2);
        }
        boolean found = false;
        for (ScenarioDefinitionInfo entry : all) {
            if (!entry.getName().equals(wanted)) {
                continue;
            }
            found = true;
            context.getSender().sendMessage(definitionRow(entry));
            for (UsageVariant variant : DefinitionUsage.of(entry)) {
                context.getSender().sendMessage(
                    Component.text("  " + variant.getLabel() + ": ", ChatPalette.SOFT)
                        .append(Component.text(variant.getText(), ChatPalette.TEXT))
                        .append(Component.text("  " + variant.getMeaning(), ChatPalette.FAINT)));
            }
        }
        if (!found) {
            context.getSender().sendMessage(Component.text(
                wanted + ": такого действия, требования или функции нет. Список: /pn actions", ChatPalette.WARN));
        }
    }
}
