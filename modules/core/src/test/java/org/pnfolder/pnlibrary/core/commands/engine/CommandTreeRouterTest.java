package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.error.BadUsage;
import org.pnfolder.pnlibrary.api.commands.error.NoPermission;
import org.pnfolder.pnlibrary.api.commands.error.UnknownArgument;

class CommandTreeRouterTest {
    private static CommandContext context(List<String> arguments, Set<String> permissions) {
        final Set<String> held = permissions;
        CommandSender sender = new CommandSender() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers("Sender", UUID.randomUUID(), permission -> held.contains(permission));
            }

            @Override
            public void sendMessage(@NotNull Component message) {
            }
        };
        return new CommandContext(sender, arguments, "group", arguments.isEmpty() ? "" : arguments.get(arguments.size() - 1));
    }

    private static CommandContext context(String... arguments) {
        return context(Arrays.asList(arguments), Collections.emptySet());
    }

    private static Set<String> held(String... permissions) {
        return new HashSet<>(Arrays.asList(permissions));
    }

    @Test
    @DisplayName("mixed tree prefers literals and accumulates typed values")
    void mixedTreePrefersLiteralsAndAccumulatesTypedValues() {
        CommandDefinition definition = CommandDefinition.command("group", root -> root.argument("group", ArgumentType.string(), group -> {
            group.literal("page", page -> page.executes(context -> { }));
            group.literal("member", member -> member.argument("page", ArgumentType.integer(), page -> page.executes(context -> { })));
        }));
        CommandTreeRouter router = new CommandTreeRouter();

        CommandRoute member = router.route(definition, context("builders", "member", "12"));
        CommandRoute literal = router.route(definition, context("builders", "page"));

        assertTrue(member instanceof ExecutableRoute);
        assertEquals("builders", ((ExecutableRoute) member).getContext().<String>get("group"));
        assertEquals(12, (int) ((ExecutableRoute) member).getContext().<Integer>get("page"));
        assertTrue(literal instanceof ExecutableRoute);
    }

    @Test
    @DisplayName("permissions and availability hide descendants and deny manual input")
    void permissionsAndAvailabilityHideDescendantsAndDenyManualInput() {
        CommandDefinition definition = CommandDefinition.command("group", root -> root.argument("group", ArgumentType.string(), group -> {
            group.literal("public", node -> node.executes(context -> { }));
            group.literal("secret", secret -> {
                secret.permission("groups.secret");
                secret.availableIf(context -> "builders".equals(context.<String>get("group")));
                secret.literal("delete", delete -> delete.executes(context -> { }));
            });
        }));
        CommandTreeRouter router = new CommandTreeRouter();

        List<String> denied = router.suggest(definition, context("builders", "")).toCompletableFuture().join();
        List<String> allowed = router.suggest(definition,
                context(Arrays.asList("builders", ""), held("groups.secret"))).toCompletableFuture().join();

        assertEquals(Arrays.asList("public"), denied);
        assertEquals(Arrays.asList("public", "secret"), allowed);
        RouteProblem problem = (RouteProblem) router.route(definition, context("builders"));
        assertEquals("/group <group> public", ((BadUsage) problem.getError()).getUsage());
        assertTrue(((RouteProblem) router.route(definition, context("builders", "secret", "delete"))).getError() instanceof NoPermission);
        assertFalse(router.suggest(definition, context(Arrays.asList("other", "secret", ""), held("groups.secret")))
                .toCompletableFuture().join().contains("delete"));
    }

    @Test
    @DisplayName("argument suggestions see earlier values and partial input")
    void argumentSuggestionsSeeEarlierValuesAndPartialInput() {
        CommandDefinition definition = CommandDefinition.command("group", root -> root.argument("group", ArgumentType.string(),
                group -> group.literal("member", member -> member.argument("player", ArgumentType.string(), player -> {
                    player.suggests(context -> {
                        assertEquals("builders", context.<String>get("group"));
                        return Arrays.asList("Steve", "Alex");
                    });
                    player.executes(context -> { });
                }))));

        List<String> suggestions = new CommandTreeRouter().suggest(definition, context("builders", "member", "St"))
                .toCompletableFuture().join();

        assertEquals(Arrays.asList("Steve"), suggestions);
    }

    @Test
    @DisplayName("invalid or incomplete routes return usage")
    void invalidOrIncompleteRoutesReturnUsage() {
        CommandDefinition definition = CommandDefinition.command("amount",
                root -> root.argument("value", ArgumentType.integer(), value -> value.executes(context -> { })));
        CommandTreeRouter router = new CommandTreeRouter();

        assertEquals("/amount <value>", usageOf(router, definition, new String[0]));
        assertEquals("/amount <value>", usageOf(router, definition, new String[] {"wrong"}));
    }

    private static String usageOf(CommandTreeRouter router, CommandDefinition definition, String[] arguments) {
        return ((BadUsage) ((RouteProblem) router.route(definition, context(arguments))).getError()).getUsage();
    }

    @Test
    @DisplayName("a mounted command lives under another one and sees its own arguments")
    void aMountedCommandLivesUnderAnotherOneAndSeesItsOwnArguments() {
        final List<List<String>> seen = new ArrayList<>();
        CommandDefinition events = CommandDefinition.command("events", root -> {
            root.permission("events.view");
            root.executes(context -> seen.add(context.getArguments()));
            root.literal("view", view -> view.argument("id", ArgumentType.string(),
                    id -> id.executes(context -> seen.add(context.getArguments()))));
        });
        CommandDefinition control = CommandDefinition.command("control", root -> {
            root.literal("status", status -> {
                status.permission("control.admin");
                status.executes(context -> { });
            });
            root.mount("events", events);
        });
        CommandTreeRouter router = new CommandTreeRouter();

        ExecutableRoute rootRoute = (ExecutableRoute) router.route(control, context(Arrays.asList("events"), held("events.view")));
        ExecutableRoute card = (ExecutableRoute) router.route(control,
                context(Arrays.asList("events", "view", "abc"), held("events.view")));
        rootRoute.getNode().getExecution().execute(rootRoute.getContext());
        card.getNode().getExecution().execute(card.getContext());

        assertEquals(Arrays.asList(Collections.emptyList(), Arrays.asList("view", "abc")), seen);
        // The permission of the mounted command stays its own, and the control command's does not leak into it.
        CommandRoute denied = router.route(control, context(Arrays.asList("events"), held("control.admin")));
        assertTrue(((RouteProblem) denied).getError() instanceof NoPermission);
        List<String> suggestions = router.suggest(control, context(Arrays.asList(""), held("events.view"))).toCompletableFuture().join();
        assertEquals(Arrays.asList("events"), suggestions);
    }

    @Test
    @DisplayName("mounting under a name that is taken is refused")
    void mountingUnderANameThatIsTakenIsRefused() {
        final CommandDefinition other = CommandDefinition.command("other", root -> root.executes(context -> { }));

        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("control", root -> {
            root.literal("events", events -> events.executes(context -> { }));
            root.mount("events", other);
        }));
    }

    @Test
    @DisplayName("greedy argument takes the rest of the line as one value")
    void greedyArgumentTakesTheRestOfTheLineAsOneValue() {
        CommandDefinition definition = CommandDefinition.command("clan", root -> root.literal("create",
                create -> create.argument("name", ArgumentType.greedyString(), name -> name.executes(context -> { }))));
        CommandTreeRouter router = new CommandTreeRouter();

        CommandRoute route = router.route(definition, context("create", "Dark", "Knights", "of", "Pn"));
        CommandRoute missing = router.route(definition, context("create"));

        assertEquals("Dark Knights of Pn", ((ExecutableRoute) route).getContext().<String>get("name"));
        assertTrue(missing instanceof RouteProblem);
    }

    @Test
    @DisplayName("greedy argument must end its branch")
    void greedyArgumentMustEndItsBranch() {
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("clan", root -> root.argument("name",
                ArgumentType.greedyString(), name -> name.literal("more", more -> more.executes(context -> { })))));
    }

    @Test
    @DisplayName("node that accepts extra arguments runs on words no child matches")
    void nodeThatAcceptsExtraArgumentsRunsOnWordsNoChildMatches() {
        CommandDefinition definition = CommandDefinition.command("shop", root -> {
            root.executes(context -> { });
            root.acceptsExtraArguments();
            root.literal("reload", reload -> reload.executes(context -> { }));
        });
        CommandDefinition strict = CommandDefinition.command("strict", root -> {
            root.executes(context -> { });
            root.literal("reload", reload -> reload.executes(context -> { }));
        });
        CommandTreeRouter router = new CommandTreeRouter();

        CommandRoute unknown = router.route(definition, context("whatever", "else"));
        CommandRoute known = router.route(definition, context("reload"));
        CommandRoute refused = router.route(strict, context("whatever"));

        assertTrue(unknown instanceof ExecutableRoute);
        assertEquals(Arrays.asList("whatever", "else"), ((ExecutableRoute) unknown).getContext().getArguments());
        assertEquals("reload", ((ExecutableRoute) known).getNode().getName());
        assertTrue(((RouteProblem) refused).getError() instanceof UnknownArgument);
    }

    @Test
    @DisplayName("an alias may use any script")
    void anAliasMayUseAnyScript() {
        CommandDefinition definition = CommandDefinition.command("trade", root -> {
            root.aliases("обмен");
            root.executes(context -> { });
        });

        assertEquals(Collections.singleton("обмен"), definition.getAliases());
    }
}
