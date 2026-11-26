package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentSpec;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;

// Brigadier validates client-side; the portable router still handles all logic after the tree matches.
final class BrigadierTrees {
    private static final String EXTRA_ARGUMENTS = "arguments";

    private BrigadierTrees() {
    }

    static <S> LiteralCommandNode<S> build(CommandDefinition definition, NativeInvocation<S> invocation) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(definition.getName())
            .requires(source -> invocation.allowed(source, definition.getRoot()));
        populate(root, definition.getRoot(), invocation);
        return root.build();
    }

    private static <S> void populate(ArgumentBuilder<S, ?> builder, CommandNode node, NativeInvocation<S> invocation) {
        if (node.isExecutable()) {
            builder.executes(context -> run(invocation, context.getSource(), context.getInput()));
        }
        for (CommandNode child : node.getChildren()) {
            builder.then(child(child, invocation));
        }
        // Allow free words when no argument child exists; the portable router parses them itself.
        if (node.getAcceptsExtraArguments() && node.isExecutable() && !hasArgumentChild(node)) {
            builder.then(
                RequiredArgumentBuilder.<S, Object>argument(EXTRA_ARGUMENTS, typeOf(ArgumentSpec.GREEDY))
                    .requires(source -> invocation.allowed(source, node))
                    .suggests(suggestionsFor(invocation))
                    .executes(context -> run(invocation, context.getSource(), context.getInput()))
            );
        }
    }

    private static boolean hasArgumentChild(CommandNode node) {
        for (CommandNode child : node.getChildren()) {
            if (child.getKind() == CommandNodeKind.ARGUMENT) {
                return true;
            }
        }
        return false;
    }

    private static <S> int run(NativeInvocation<S> invocation, S source, String input) {
        invocation.execute(source, input);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> ArgumentBuilder<S, ?> child(CommandNode node, NativeInvocation<S> invocation) {
        if (node.getKind() == CommandNodeKind.LITERAL) {
            LiteralArgumentBuilder<S> literal = LiteralArgumentBuilder.<S>literal(node.getName())
                .requires(source -> invocation.allowed(source, node));
            populate(literal, node, invocation);
            return literal;
        }
        org.pnfolder.pnlibrary.api.commands.definition.ArgumentType<?> portable = node.getArgumentType();
        ArgumentSpec spec = portable != null ? portable.getSpec() : ArgumentSpec.TEXT;
        RequiredArgumentBuilder<S, Object> argument = RequiredArgumentBuilder.<S, Object>argument(node.getName(), typeOf(spec))
            .requires(source -> invocation.allowed(source, node))
            .suggests(suggestionsFor(invocation));
        populate(argument, node, invocation);
        return argument;
    }

    private static double bound(Double value, ArgumentSpec spec) {
        if (value == null) {
            throw new IllegalArgumentException(spec.getKind() + " argument needs both bounds");
        }
        return value;
    }

    // Types the portable parser accepts in more spellings than Brigadier stay as SpacelessWord
    // so the client never rejects input the server would take (e.g. `yes` for boolean, `1e3` for decimal).
    @SuppressWarnings("unchecked")
    static ArgumentType<Object> typeOf(ArgumentSpec spec) {
        ArgumentType<?> type;
        switch (spec.getKind()) {
            case INTEGER:
                type = IntegerArgumentType.integer((int) bound(spec.getMinimum(), spec), (int) bound(spec.getMaximum(), spec));
                break;
            case LONG:
                type = LongArgumentType.longArg((long) bound(spec.getMinimum(), spec), (long) bound(spec.getMaximum(), spec));
                break;
            case DECIMAL:
                type = decimal(spec.getMinimum(), spec.getMaximum());
                break;
            case GREEDY:
                type = StringArgumentType.greedyString();
                break;
            default:
                type = SpacelessWord.INSTANCE;
        }
        return (ArgumentType<Object>) type;
    }

    private static ArgumentType<?> decimal(Double min, Double max) {
        if (min == null || max == null || (min == -Double.MAX_VALUE && max == Double.MAX_VALUE)) {
            return SpacelessWord.INSTANCE;
        }
        return DoubleArgumentType.doubleArg(min, max);
    }

    private static <S> SuggestionProvider<S> suggestionsFor(NativeInvocation<S> invocation) {
        return (context, builder) -> {
            CompletableFuture<Suggestions> result = new CompletableFuture<>();
            // For greedy arguments (EXTRA_ARGUMENTS), builder.start points to the beginning of the entire
            // greedy portion. Adjust to the last token so the suggestion replaces only the word being typed.
            int lastSpace = builder.getInput().lastIndexOf(' ');
            SuggestionsBuilder target = lastSpace >= builder.getStart() ? builder.createOffset(lastSpace + 1) : builder;
            try {
                invocation.suggest(context.getSource(), builder.getInput()).whenComplete((values, error) -> {
                    if (error == null && values != null) {
                        values.forEach(target::suggest);
                    }
                    result.complete(target.build());
                });
            } catch (Exception error) {
                result.complete(target.build());
            }
            return result;
        };
    }
}
