package org.pnfolder.pnlibrary.api.commands.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

class CommandTreeBuilderTest {
    @Test
    void builderCreatesAnImmutableMixedDepthTree() {
        CommandDefinition definition = CommandDefinition.command("group", c -> c.argument("group", ArgumentType.string(), g -> g
                .suggests(context -> Collections.singletonList("builders"))
                .literal("member", m -> m.argument("page", ArgumentType.integer(), p -> p
                        .permission("groups.page")
                        .availableIf(context -> "builders".equals(context.<String>get("group")))
                        .executes(context -> {})))));

        CommandNode group = definition.getRoot().getChildren().get(0);
        CommandNode member = group.getChildren().get(0);
        CommandNode page = member.getChildren().get(0);
        assertEquals(1, definition.getRoot().getChildren().size());
        assertEquals(CommandNodeKind.ARGUMENT, group.getKind());
        assertEquals("member", member.getName());
        assertEquals(ArgumentType.integer(), page.getArgumentType());
        assertEquals("groups.page", page.getPermission());
        assertTrue(page.isExecutable());
    }

    @Test
    void typedContextExposesParsedValuesWithoutChangingRawArguments() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("group", "builders");
        values.put("amount", new BigDecimal("12.50"));
        CommandContext context =
                new CommandContext(new TestSender(), Arrays.asList("builders", "12.50"), "group", "12.50", values);

        assertEquals("builders", context.<String>get("group"));
        assertEquals(new BigDecimal("12.50"), context.<BigDecimal>get("amount"));
        assertEquals(Arrays.asList("builders", "12.50"), context.getArguments());
        assertFalse(context.contains("missing"));
        assertThrows(IllegalArgumentException.class, () -> context.<String>get("missing"));
    }

    @Test
    void ambiguousSiblingArgumentsAndDuplicateNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("bad", c -> c
                .argument("first", ArgumentType.string(), n -> {})
                .argument("second", ArgumentType.integer(), n -> {})));
        assertThrows(IllegalArgumentException.class, () -> CommandDefinition.command("bad", c -> c
                .literal("same", n -> {})
                .literal("SAME", n -> {})));
        assertThrows(
                IllegalArgumentException.class, () -> CommandDefinition.command("bad", c -> c.literal("bad literal", n -> {})));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandDefinition.command("bad", c -> c.argument("bad argument", ArgumentType.string(), n -> {})));
    }

    @Test
    void builtChildrenCannotBeMutatedThroughTheirPublicList() {
        CommandDefinition definition = CommandDefinition.command("safe", c -> c.literal("child", n -> {}));

        assertThrows(UnsupportedOperationException.class, () -> definition.getRoot().getChildren().clear());
    }

    @Test
    void javaConsumersCanConfigureLiteralAndArgumentChildren() {
        CommandDefinition definition = CommandDefinition.builder("java-tree")
                .literal("show", literal -> literal.argument("page", ArgumentType.integer(), n -> n.executes(context -> {})))
                .build();

        assertEquals("page", definition.getRoot().getChildren().get(0).getChildren().get(0).getName());
    }

    private static final class TestSender implements CommandSender {
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers("Test", UUID.randomUUID(), permission -> true);
        }

        @Override
        public void sendMessage(Component message) {}
    }
}
