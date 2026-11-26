package org.pnfolder.pnlibrary.bukkit.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;

class BrigadierTreesTest {
    private final List<String> executed = new ArrayList<>();
    private final List<String> suggested = new ArrayList<>();

    private final NativeInvocation<String> invocation = new NativeInvocation<String>() {
        @Override
        public void execute(String source, String input) {
            executed.add(input);
        }

        @Override
        public CompletionStage<List<String>> suggest(String source, String input) {
            suggested.add(input);
            return CompletableFuture.completedFuture(Arrays.asList("alpha", "beta"));
        }
    };

    private final CommandDefinition demo = CommandDefinition.command("demo", root -> {
        root.executes(context -> { });
        root.literal("set", set ->
            set.argument("amount", ArgumentType.integer(1, 10), amount -> amount.executes(context -> { })));
        root.literal("mode", mode ->
            mode.argument("kind", ArgumentType.choice("fast", "slow"), kind -> kind.executes(context -> { })));
        root.literal("flag", flag ->
            flag.argument("on", ArgumentType.bool(), on -> on.executes(context -> { })));
    });

    private CommandDispatcher<String> dispatcher() {
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(BrigadierTrees.build(demo, invocation));
        return dispatcher;
    }

    @Test
    void theTreeMirrorsThePortableCommandAndRunsExecutableRoutes() throws CommandSyntaxException {
        CommandDispatcher<String> dispatcher = dispatcher();

        dispatcher.execute("demo", "player");
        dispatcher.execute("demo set 5", "player");
        dispatcher.execute("demo mode fast", "player");

        assertEquals(Arrays.asList("demo", "demo set 5", "demo mode fast"), executed);
    }

    @Test
    void aNumberOutsideItsBoundsIsRejectedBeforeTheCommandRuns() {
        CommandDispatcher<String> dispatcher = dispatcher();

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("demo set 99", "player"));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("demo set abc", "player"));
        assertTrue(executed.isEmpty());
    }

    @Test
    void spellingsThePortableParserAcceptsAreNotRejectedByTheClientTree() throws CommandSyntaxException {
        CommandDispatcher<String> dispatcher = dispatcher();

        dispatcher.execute("demo flag yes", "player");
        dispatcher.execute("demo flag off", "player");

        assertEquals(Arrays.asList("demo flag yes", "demo flag off"), executed);
    }

    @Test
    void argumentSuggestionsComeFromTheSharedRouterWithTheFullLineTypedSoFar() throws Exception {
        CommandDispatcher<String> dispatcher = dispatcher();

        Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("demo mode ", "player")).get();

        assertEquals(Arrays.asList("alpha", "beta"),
            suggestions.getList().stream().map(Suggestion::getText).collect(Collectors.toList()));
        assertEquals(Collections.singletonList("demo mode "), suggested);
    }

    @Test
    void aSubcommandTheSenderMayNotEnterIsNotOfferedByTab() {
        CommandDefinition guarded = CommandDefinition.command("guarded", root -> {
            root.literal("open", open -> open.executes(context -> { }));
            root.literal("admin", admin -> {
                admin.permission("demo.admin");
                admin.executes(context -> { });
            });
        });
        NativeInvocation<Set<String>> byPermission = new NativeInvocation<Set<String>>() {
            @Override
            public void execute(Set<String> source, String input) {
            }

            @Override
            public CompletionStage<List<String>> suggest(Set<String> source, String input) {
                return CompletableFuture.completedFuture(Collections.<String>emptyList());
            }

            @Override
            public boolean allowed(Set<String> source, CommandNode node) {
                return node.getPermission() == null || source.contains(node.getPermission());
            }
        };
        CommandDispatcher<Set<String>> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(BrigadierTrees.build(guarded, byPermission));

        // The client tree is built from the nodes a source may use, so that is what decides what Tab offers.
        assertEquals(Collections.singletonList("open"), offered(dispatcher, Collections.<String>emptySet()));
        assertEquals(Arrays.asList("admin", "open"), offered(dispatcher, new HashSet<>(Arrays.asList("demo.admin"))));
    }

    private static List<String> offered(CommandDispatcher<Set<String>> dispatcher, Set<String> source) {
        return dispatcher.getRoot().getChild("guarded").getChildren().stream()
            .filter(node -> node.canUse(source))
            .map(com.mojang.brigadier.tree.CommandNode::getName)
            .sorted()
            .collect(Collectors.toList());
    }

    @Test
    void aCommandLineIsSplitIntoTheLabelAndItsArguments() {
        CommandLine typed = CommandLine.of("pnlibrary:pn events  abc", false);
        assertEquals("pn", typed.getLabel());
        assertEquals(Arrays.asList("events", "abc"), typed.getArguments());

        CommandLine completing = CommandLine.of("/pn events view ", true);
        assertEquals(Arrays.asList("events", "view", ""), completing.getArguments());
    }

    @Test
    void freeTextReachesTheRouterThroughAGreedyArgumentAndAnExtraWordsNode() throws CommandSyntaxException {
        CommandDefinition shop = CommandDefinition.command("shop", root -> {
            root.executes(context -> { });
            root.acceptsExtraArguments();
            root.literal("reload", reload -> reload.executes(context -> { }));
            root.literal("name", name ->
                name.argument("title", ArgumentType.greedyString(), title -> title.executes(context -> { })));
        });
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(BrigadierTrees.build(shop, invocation));

        dispatcher.execute("shop name Dark Knights of Pn", "player");
        dispatcher.execute("shop anything at all", "player");
        dispatcher.execute("shop reload", "player");

        assertEquals(Arrays.asList("shop name Dark Knights of Pn", "shop anything at all", "shop reload"), executed);
    }
}
