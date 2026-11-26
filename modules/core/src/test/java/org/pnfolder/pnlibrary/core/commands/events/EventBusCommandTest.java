package org.pnfolder.pnlibrary.core.commands.events;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter;
import org.pnfolder.pnlibrary.core.commands.engine.ExecutableRoute;
import org.pnfolder.pnlibrary.core.events.EventDispatcher;
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory;

class EventBusCommandTest {
    private final EventHistory history = new EventHistory(null, 30);
    private final EventDispatcher events = new EventDispatcher(history, (owner, event, error) -> { });
    private final PnLibrary library = mock(PnLibrary.class);
    private final CommandDefinition definition;

    EventBusCommandTest() {
        when(library.getEvents()).thenReturn(events);
        definition = new EventBusCommand(library).definition();
    }

    @AfterEach
    void close() {
        events.close();
    }

    private List<String> run(String... args) {
        final List<Component> sent = new ArrayList<>();
        CommandSender sender = new CommandSender() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers("Console", permission -> true);
            }

            @Override
            public void sendMessage(@NotNull Component message) {
                sent.add(message);
            }
        };
        ExecutableRoute route = (ExecutableRoute) new CommandTreeRouter().route(definition,
                new CommandContext(sender, Arrays.asList(args), "bus", String.join(" ", args)));
        route.getNode().getExecution().execute(route.getContext()).toCompletableFuture().join();
        List<String> texts = new ArrayList<>();
        for (Component message : sent) {
            texts.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return texts;
    }

    private void listen(String plugin, Runnable action) {
        events.scope(PluginId.of(plugin)).subscribe(PingEvent.class, EventPriority.NORMAL, event -> action.run());
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    @DisplayName("slow names a listener that ran and says so when none did")
    void slowNamesAListenerThatRanAndSaysSoWhenNoneDid() {
        boolean none = false;
        for (String line : run("slow")) {
            none |= line.contains("ещё не запускались");
        }
        assertTrue(none);
        listen("shop", () -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        events.callEvent(new PingEvent());
        String text = joined(run("slow"));
        assertTrue(text.contains("shop") && text.contains("Ping"), text);
    }

    @Test
    @DisplayName("errors lists only listeners that threw")
    void errorsListsOnlyListenersThatThrew() {
        listen("good", () -> { });
        listen("bad", () -> {
            throw new IllegalStateException("boom");
        });
        events.callEvent(new PingEvent());
        String text = joined(run("errors"));
        assertTrue(text.contains("bad") && text.contains("ошибок 1"), text);
        assertTrue(!text.contains("good"), text);
    }

    @Test
    @DisplayName("plugin view filters by plugin and top ranks events by calls")
    void pluginViewFiltersByPluginAndTopRanksEventsByCalls() {
        listen("shop", () -> { });
        listen("other", () -> { });
        for (int index = 0; index < 3; index++) {
            events.callEvent(new PingEvent());
        }
        String plugin = joined(run("plugin", "shop"));
        assertTrue(plugin.contains("shop") && !plugin.contains("other"), plugin);
        assertTrue(joined(run("top")).contains("3"), "top");
    }

    @Test
    @DisplayName("range counts calls from the hourly history")
    void rangeCountsCallsFromTheHourlyHistory() {
        events.callEvent(new PingEvent());
        events.callEvent(new PingEvent());
        String text = joined(run("range", "1h"));
        assertTrue(text.contains("Ping") && text.contains("2"), text);
    }
}
