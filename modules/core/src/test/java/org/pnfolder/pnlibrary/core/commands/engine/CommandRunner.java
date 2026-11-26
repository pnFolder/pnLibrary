package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Routes typed words through a definition and runs what they reach, for command tests. */
public final class CommandRunner {
    private CommandRunner() {
    }

    /** The route the words reach; fails the test when they do not reach something to run. */
    public static ExecutableRoute route(CommandDefinition definition, RecordingSender sender, String name, String... words) {
        CommandRoute route = new CommandTreeRouter().route(definition,
                new CommandContext(sender, Arrays.asList(words), name, String.join(" ", words)));
        assertTrue(route instanceof ExecutableRoute, String.valueOf(route));
        return (ExecutableRoute) route;
    }

    /** Runs the words and returns what the sender was sent while they ran, as plain text. */
    public static String run(CommandDefinition definition, RecordingSender sender, String name, String... words) {
        sender.clear();
        ExecutableRoute route = route(definition, sender, name, words);
        route.getNode().getExecution().execute(route.getContext()).toCompletableFuture().join();
        return sender.text();
    }
}
