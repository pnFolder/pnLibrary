package org.pnfolder.pnlibrary.core.updates.catalog;

import org.pnfolder.pnlibrary.core.updates.catalog.proxy.ProxyUpdateCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute;
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter;
import org.pnfolder.pnlibrary.core.commands.engine.ExecutableRoute;

class ProxyUpdateCommandTest {
    /** One command per update service, so that the tokens it issued are still known to the next call. */
    private final Map<FakeUpdates, CommandDefinition> definitions = new HashMap<>();

    @Test
    void aPlayerWithThePermissionMayChangeSettingsAndIsRecordedAsAPlayer() {
        FakeUpdates updates = new FakeUpdates();
        execute(updates, new Sender("player", false, null), "updates", "set", "pnlibrary", "channel", "DEV");
        assertEquals("pnlibrary", updates.channelProduct);
        assertEquals(UpdateChannel.DEV, updates.channelSet);
        assertEquals(UpdateActor.Kind.PLAYER, updates.actor.getKind());
        execute(updates, new Sender("player", false, null), "updates", "set", "pnlibrary", "hold", "3d");
        assertEquals("pnlibrary", updates.heldProduct);
        assertEquals(Duration.ofDays(3), updates.heldSet);
    }

    @Test
    void aSenderWithoutThePermissionCannotReachACommand() {
        FakeUpdates updates = new FakeUpdates();
        Set<String> nothing = Collections.emptySet();

        assertFalse(route(updates, new Sender("player", false, nothing), "updates", "set", "pnlibrary", "hold", "3d") instanceof ExecutableRoute);
        assertFalse(route(updates, new Sender("player", false, nothing), "update", "rollback") instanceof ExecutableRoute);
    }

    @Test
    void thereIsNoAutomaticDownloadSwitchAnyMore() {
        assertFalse(route(new FakeUpdates(), new Sender("player", true, null), "updates", "set", "pnlibrary", "auto", "on") instanceof ExecutableRoute);
        assertFalse(route(new FakeUpdates(), new Sender("player", true, null), "updates", "set", "all", "auto", "on") instanceof ExecutableRoute);
    }

    @Test
    void invalidValuesNeverChangeSettings() {
        FakeUpdates updates = new FakeUpdates();
        execute(updates, new Sender("player", false, null), "updates", "set", "pnlibrary", "channel", "nightly");
        execute(updates, new Sender("player", false, null), "updates", "set", "pnlibrary", "hold", "90d");
        assertNull(updates.channelProduct);
        assertNull(updates.heldProduct);
    }

    @Test
    void aSavedRollbackAsksFirstAndRunsOnlyOnTheTokenOfTheSameSender() {
        FakeUpdates updates = new FakeUpdates();
        UUID id = UUID.randomUUID();
        Sender asker = new Sender("player", false, null);
        execute(updates, asker, "updates", "rollback", id.toString());
        assertNull(updates.rolledBack);
        String token = asker.token();
        execute(updates, new Sender("other", false, null), "update", "confirm", token);
        assertNull(updates.rolledBack);
        execute(updates, asker, "update", "confirm", token);
        assertEquals(id, updates.rolledBack);
        assertEquals(Boolean.FALSE, updates.restoreConfigurations);
        updates.rolledBack = null;
        execute(updates, asker, "update", "confirm", token);
        assertNull(updates.rolledBack);
    }

    @Test
    void configurationRestoreIsASeparatelyConfirmedExplicitChoice() {
        FakeUpdates updates = new FakeUpdates();
        UUID id = UUID.randomUUID();
        Sender asker = new Sender("player", false, null);
        execute(updates, asker, "updates", "rollback", id.toString(), "configs");
        assertNull(updates.rolledBack);
        execute(updates, asker, "update", "confirm", asker.token());
        assertEquals(id, updates.rolledBack);
        assertEquals(Boolean.TRUE, updates.restoreConfigurations);
    }

    private CommandRoute route(FakeUpdates updates, Sender sender, String... arguments) {
        CommandDefinition definition = definitions.get(updates);
        if (definition == null) {
            PnLibrary library = (PnLibrary) Proxy.newProxyInstance(PnLibrary.class.getClassLoader(), new Class<?>[] {PnLibrary.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUpdates")) {
                        return updates;
                    }
                    if (method.getName().equals("isClosed")) {
                        return false;
                    }
                    throw new IllegalStateException("Unexpected call: " + method.getName());
                });
            final ProxyUpdateCommand controls = new ProxyUpdateCommand(library);
            definition = CommandDefinition.command("pn", controls::install);
            definitions.put(updates, definition);
        }
        return new CommandTreeRouter().route(definition,
            new CommandContext(sender, Arrays.asList(arguments), "pn", String.join(" ", arguments)));
    }

    private void execute(FakeUpdates updates, Sender sender, String... arguments) {
        ExecutableRoute route = (ExecutableRoute) route(updates, sender, arguments);
        route.getNode().getExecution().execute(route.getContext()).toCompletableFuture().join();
    }

    private static final class Sender implements CommandSender {
        private final String id;
        private final boolean console;
        private final Set<String> allowed;
        private final List<Component> sent = new ArrayList<>();

        Sender(String id, boolean console, Set<String> allowed) {
            this.id = id;
            this.console = console;
            this.allowed = allowed;
        }

        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers(id, console ? null : UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                permission -> allowed == null || allowed.contains(permission));
        }

        @Override
        public void sendMessage(@NotNull Component message) {
            sent.add(message);
        }

        /** The token in the confirmation button the sender was shown. */
        String token() {
            StringBuilder text = new StringBuilder();
            for (Component line : sent) {
                text.append(line).append(", ");
            }
            Matcher matcher = Pattern.compile("update confirm ([A-Za-z0-9_-]+)").matcher(text);
            assertTrue(matcher.find());
            return matcher.group(1);
        }
    }
}
