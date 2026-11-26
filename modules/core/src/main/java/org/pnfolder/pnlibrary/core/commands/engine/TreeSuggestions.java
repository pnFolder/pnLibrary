package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentSpec;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;

/** Tab suggestions for the words typed so far in a command tree. */
final class TreeSuggestions {
    private TreeSuggestions() {
    }

    /** Tab suggestions for the words typed so far; the last word is the one being completed. */
    @NotNull
    static CompletionStage<List<String>> suggest(@NotNull CommandDefinition command, @NotNull CommandContext context) {
        CommandNode node = command.getRoot();
        Map<String, Object> values = new LinkedHashMap<>();
        CommandContext routed = context.withParsedValues(new LinkedHashMap<>(values));
        if (!RouteRules.canSuggest(node, routed)) {
            return CommandDefinition.completedSuggestions(Collections.emptyList());
        }
        List<String> typed = context.getArguments();
        String partial = typed.isEmpty() ? context.getCurrentInput() : typed.get(typed.size() - 1);
        for (int index = 0; index < typed.size() - 1; index++) {
            // Everything typed after a greedy argument still belongs to that argument.
            if (node.getArgumentType() != null && RouteRules.isGreedy(node)) {
                continue;
            }
            CommandNode child = RouteRules.childFor(node, typed.get(index));
            if (child == null) {
                return node.getAcceptsExtraArguments()
                        ? node.getSuggestions().suggest(routed.withCurrentInput(partial))
                        : CommandDefinition.completedSuggestions(Collections.emptyList());
            }
            if (!RouteRules.canSuggest(child, routed) || !remember(child, typed.get(index), values)) {
                return CommandDefinition.completedSuggestions(Collections.emptyList());
            }
            node = child;
            routed = context.withParsedValues(new LinkedHashMap<>(values)).withCurrentInput(partial);
        }
        return lastSuggestions(node, routed.withCurrentInput(partial), partial);
    }


    private static boolean remember(CommandNode child, String token, Map<String, Object> values) {
        if (child.getKind() != CommandNodeKind.ARGUMENT) {
            return true;
        }
        Object parsed = RouteRules.parse(child, token);
        if (parsed == null) {
            return false;
        }
        values.put(child.getName(), parsed);
        return true;
    }


    private static CompletionStage<List<String>> lastSuggestions(CommandNode node, CommandContext suggestionContext, final String partial) {
        final List<String> literals = new ArrayList<>();
        for (CommandNode child : node.getChildren()) {
            if (child.getKind() == CommandNodeKind.LITERAL && RouteRules.canSuggest(child, suggestionContext)
                    && RouteRules.startsWith(child.getName(), partial)) {
                literals.add(child.getName());
            }
        }
        CommandNode argument = RouteRules.argumentChild(node);
        if (argument != null && !RouteRules.canSuggest(argument, suggestionContext)) {
            argument = null;
        }
        CompletionStage<List<String>> stage;
        if (argument != null) {
            final CommandNode typedArgument = argument;
            stage = argument.getSuggestions().suggest(suggestionContext).thenApply(found -> {
                List<String> all = new ArrayList<>(found);
                all.addAll(builtInSuggestions(typedArgument));
                return all;
            });
        } else if (node.getChildren().isEmpty() || node.getAcceptsExtraArguments()) {
            stage = node.getSuggestions().suggest(suggestionContext);
        } else {
            stage = CommandDefinition.completedSuggestions(Collections.emptyList());
        }
        return stage.thenApply(dynamic -> merge(literals, dynamic, partial));
    }


    private static List<String> merge(List<String> literals, List<String> dynamic, String partial) {
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<String>(literals);
        for (String value : dynamic) {
            if (RouteRules.startsWith(value, partial)) {
                distinct.add(value);
            }
        }
        return new ArrayList<>(distinct);
    }


    private static List<String> builtInSuggestions(CommandNode argument) {
        ArgumentType<?> type = argument.getArgumentType();
        if (type == null) {
            return Collections.emptyList();
        }
        ArgumentSpec spec = type.getSpec();
        switch (spec.getKind()) {
            case CHOICE:
                return spec.getChoices();
            case BOOLEAN:
                return java.util.Arrays.asList("true", "false");
            default:
                return Collections.emptyList();
        }
    }

}
