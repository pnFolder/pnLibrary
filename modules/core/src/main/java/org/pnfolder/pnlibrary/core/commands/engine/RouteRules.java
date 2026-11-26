package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentSpec;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.ConsoleOnly;
import org.pnfolder.pnlibrary.api.commands.error.NoPermission;
import org.pnfolder.pnlibrary.api.commands.error.PlayerOnly;

/** What a sender may reach in a command tree: permissions, sender rules, the child a word names, usage text. */
final class RouteRules {
    private RouteRules() {
    }

    @Nullable
    static CommandNode childFor(CommandNode node, String token) {
        for (CommandNode child : node.getChildren()) {
            if (child.getKind() == CommandNodeKind.LITERAL && child.getName().equalsIgnoreCase(token)) {
                return child;
            }
        }
        return argumentChild(node);
    }


    @Nullable
    static CommandNode argumentChild(CommandNode node) {
        for (CommandNode child : node.getChildren()) {
            if (child.getKind() == CommandNodeKind.ARGUMENT) {
                return child;
            }
        }
        return null;
    }


    static boolean allowed(CommandNode node, CommandContext context) {
        String permission = node.getPermission();
        boolean console = AudienceExtensions.isConsole(context.getSender());
        boolean permitted = permission == null
                || (console && node.getConsoleBypassesPermission())
                || AudienceExtensions.hasPermission(context.getSender(), permission)
                || holdsAny(node, context);
        return permitted && node.getAvailability().isAvailable(context);
    }


    static boolean holdsAny(CommandNode node, CommandContext context) {
        for (String other : node.getAlsoPermissions()) {
            if (AudienceExtensions.hasPermission(context.getSender(), other)) {
                return true;
            }
        }
        return false;
    }


    static boolean canSuggest(CommandNode node, CommandContext context) {
        if (!allowed(node, context)) {
            return false;
        }
        boolean console = AudienceExtensions.isConsole(context.getSender());
        switch (node.getSenderRule()) {
            case PLAYER:
                return !console;
            case CONSOLE:
                return console;
            default:
                return true;
        }
    }


    @Nullable
    @SuppressWarnings("unchecked")
    static Object parse(CommandNode node, String token) {
        ArgumentType<Object> type = (ArgumentType<Object>) node.getArgumentType();
        return type == null ? null : type.parse(token);
    }


    static String usage(List<String> path, CommandNode node, CommandContext context) {
        List<CommandNode> accessible = new ArrayList<>();
        for (CommandNode child : node.getChildren()) {
            if (canSuggest(child, context)) {
                accessible.add(child);
            }
        }
        String suffix;
        if (accessible.isEmpty()) {
            suffix = "";
        } else if (accessible.size() == 1) {
            suffix = " " + display(accessible.get(0));
        } else {
            List<String> shown = new ArrayList<>();
            for (CommandNode child : accessible) {
                shown.add(display(child));
            }
            suffix = " {" + String.join("|", shown) + "}";
        }
        return "/" + String.join(" ", path) + suffix;
    }


    static String display(CommandNode node) {
        return node.getKind() == CommandNodeKind.ARGUMENT ? "<" + node.getName() + ">" : node.getName().toLowerCase(Locale.ROOT);
    }


    static boolean isGreedy(CommandNode node) {
        ArgumentType<?> type = node.getArgumentType();
        return type != null && type.getSpec().getKind() == ArgumentSpec.Kind.GREEDY;
    }


    static boolean startsWith(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }


    @Nullable
    static CommandError blocked(CommandNode node, CommandContext context) {
        if (!allowed(node, context)) {
            return new NoPermission(node.getPermission() == null ? "" : node.getPermission());
        }
        boolean console = AudienceExtensions.isConsole(context.getSender());
        switch (node.getSenderRule()) {
            case PLAYER:
                return console ? new PlayerOnly() : null;
            case CONSOLE:
                return console ? null : new ConsoleOnly();
            default:
                return null;
        }
    }

}
