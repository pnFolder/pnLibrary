package org.pnfolder.pnlibrary.api.commands.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

class CommandBuilderTest {
    @Test
    void commandNormalizationIsIndependentOfSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("info", CommandDefinition.command("INFO", c -> {}).getName());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void builderNormalizesCommandMetadataAndInvokesSynchronousHandlers() {
        AtomicReference<List<String>> executedWith = new AtomicReference<>();
        CommandDefinition definition = CommandDefinition.command("  Hello ", c -> c
                .aliases("HI", "hello-there")
                .permission("example.hello")
                .consoleBypassesPermission()
                .executes(context -> executedWith.set(context.getArguments()))
                .suggests(context -> Collections.singletonList("world")));

        assertEquals("hello", definition.getName());
        assertEquals(new LinkedHashSet<>(Arrays.asList("hi", "hello-there")), definition.getAliases());
        assertEquals("example.hello", definition.getPermission());
        assertTrue(definition.getConsoleBypassesPermission());

        CommandContext context = new CommandContext(new TestSender(), Collections.singletonList("one"), "HI", "o");
        definition.getExecution().execute(context).toCompletableFuture().join();
        assertEquals(Collections.singletonList("one"), executedWith.get());
        assertEquals(
                Collections.singletonList("world"),
                definition.getSuggestions().suggest(context).toCompletableFuture().join());
    }

    @Test
    void blankAndMalformedNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command(" ", c -> {}));
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("bad name", c -> {}));
        assertThrows(
                IllegalArgumentException.class, () -> CommandDefinition.command("good", c -> c.aliases("bad alias")));
    }

    @Test
    void primaryNameCannotAlsoBeAnAlias() {
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("hello", c -> c.aliases("HELLO")));
    }

    @Test
    void builtAliasesAreDefensiveImmutableCopies() {
        String[] source = {"first"};
        CommandDefinition definition = CommandDefinition.command("hello", c -> c.aliases(source));

        source[0] = "changed";

        assertEquals(Collections.singleton("first"), definition.getAliases());
        assertThrows(UnsupportedOperationException.class, () -> definition.getAliases().add("second"));
    }

    private static final class TestSender implements CommandSender {
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers("sender", UUID.randomUUID(), permission -> true);
        }

        @Override
        public void sendMessage(Component message) {}
    }
}
