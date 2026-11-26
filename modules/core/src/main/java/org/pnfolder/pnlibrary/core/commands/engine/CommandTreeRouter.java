package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.IncompleteCommand;
import org.pnfolder.pnlibrary.api.commands.error.UnknownArgument;

/** Walks the words a sender typed through a command tree, to a node to run or to the reason it cannot. */
public final class CommandTreeRouter {
    /** Finds what the typed {@code context} reaches in {@code command}: a node that can run, or a problem to answer. */
    @NotNull
    public CommandRoute route(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        CommandNode node = command.getRoot();
        List<CommandNode> nodes = new ArrayList<CommandNode>();
        nodes.add(node);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        CommandContext routed = context.withParsedValues(new LinkedHashMap<String, Object>(values));
        CommandError denied = RouteRules.blocked(node, routed);
        if (denied != null) {
            return problem(denied, nodes);
        }
        List<String> path = new ArrayList<String>();
        path.add(command.getName());
        List<String> arguments = context.getArguments();
        for (int index = 0; index < arguments.size(); index++) {
            CommandNode child = RouteRules.childFor(node, arguments.get(index));
            if (child == null) {
                return unknown(node, routed, nodes, path, arguments.get(index));
            }
            nodes.add(child);
            boolean greedy = RouteRules.isGreedy(child);
            String text = greedy ? joinFrom(arguments, index) : arguments.get(index);
            denied = enter(child, text, node, routed, values, path);
            if (denied != null) {
                return problem(denied, nodes);
            }
            index = greedy ? arguments.size() - 1 : index;
            node = child;
            routed = context.withParsedValues(new LinkedHashMap<String, Object>(values));
        }
        return finish(node, routed, nodes, path);
    }

    // The child must be allowed for this sender; then a literal extends the path and an argument must parse.
    private CommandError enter(CommandNode child, String text, CommandNode parent, CommandContext routed,
            Map<String, Object> values, List<String> path) {
        CommandError denied = RouteRules.blocked(child, routed);
        return denied != null ? denied : take(child, text, parent, routed, values, path);
    }

    private CommandRoute finish(CommandNode node, CommandContext routed, List<CommandNode> nodes, List<String> path) {
        if (node.isExecutable()) {
            return new ExecutableRoute(node, routed, nodes);
        }
        return problem(new IncompleteCommand(RouteRules.usage(path, node, routed)), nodes);
    }

    private CommandRoute unknown(CommandNode node, CommandContext routed, List<CommandNode> nodes, List<String> path, String token) {
        if (node.getAcceptsExtraArguments() && node.isExecutable()) {
            return new ExecutableRoute(node, routed, nodes);
        }
        return problem(new UnknownArgument(RouteRules.usage(path, node, routed), token), nodes);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private CommandError take(CommandNode child, String text, CommandNode parent, CommandContext routed,
            Map<String, Object> values, List<String> path) {
        if (child.getKind() != CommandNodeKind.ARGUMENT) {
            path.add(child.getName());
            return null;
        }
        Object parsed = RouteRules.parse(child, text);
        if (parsed == null) {
            ArgumentType<?> type = child.getArgumentType() == null ? ArgumentType.string() : child.getArgumentType();
            return type.error(RouteRules.usage(path, parent, routed), child.getName(), text);
        }
        values.put(child.getName(), parsed);
        path.add("<" + child.getName() + ">");
        return null;
    }

    private static String joinFrom(List<String> words, int index) {
        StringBuilder text = new StringBuilder();
        for (int at = index; at < words.size(); at++) {
            text.append(at == index ? "" : " ").append(words.get(at));
        }
        return text.toString();
    }

    /** Tab suggestions for the words typed so far; the last word is the one being completed. */
    @NotNull
    public CompletionStage<List<String>> suggest(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        return TreeSuggestions.suggest(command, context);
    }

    private static CommandRoute problem(CommandError error, List<CommandNode> nodes) {
        List<CommandNode> reversed = new ArrayList<CommandNode>(nodes);
        Collections.reverse(reversed);
        return new RouteProblem(error, reversed);
    }

    // allowed() checks only permission; the sender rule is checked in route() via RouteRules.blocked().
    // Suggestions must respect the rule too, otherwise the console sees player-only tab completions.

}
