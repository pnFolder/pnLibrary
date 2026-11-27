package org.pnfolder.pnlibrary.api.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.StatusEffect;

class PlayerContractsTest {
    @Test
    @DisplayName("library player supplies sender identity defaults")
    void libraryPlayerSuppliesSenderIdentityDefaults() {
        UUID id = UUID.randomUUID();
        TestPlayer player = new TestPlayer(id);

        assertEquals(id.toString(), AudienceExtensions.id(player));
        assertTrue(AudienceExtensions.isPlayer(player));
        assertFalse(AudienceExtensions.isConsole(player));
        assertTrue(AudienceExtensions.hasPermission(player, "example.use"));
    }

    @Test
    @DisplayName("a player that cannot do something says so instead of pretending")
    void aPlayerThatCannotDoSomethingSaysSoInsteadOfPretending() {
        TestPlayer player = new TestPlayer(UUID.randomUUID());

        assertEquals(Delivery.unsupported(), player.addEffect(new StatusEffect(Key.key("speed"))));
        assertEquals(Delivery.unsupported(), player.runCommand("home"));
        assertNull(player.countItems(new ItemMatcher(Key.key("diamond"))));
        assertNull(player.getExperienceLevel());
        assertNull(player.getPosition());
    }

    @Test
    @DisplayName("player pointers give name, nickname, language and permission")
    void playerPointersGiveNameNicknameLanguageAndPermission() {
        UUID id = UUID.randomUUID();
        Locale locale = Locale.GERMAN;
        Audience who = new Audience() {
            @Override
            public @NotNull Pointers pointers() {
                return AudienceExtensions.playerPointers(() -> "Alice", id, () -> Component.text("Al"), () -> locale,
                    permission -> permission.equals("x"));
            }
        };
        Audience anonymous = new Audience() {
            @Override
            public @NotNull Pointers pointers() {
                return AudienceExtensions.playerPointers(() -> "Bob", id, () -> null, () -> null, permission -> false);
            }
        };

        assertEquals("Alice", AudienceExtensions.name(who));
        assertEquals(Component.text("Al"), AudienceExtensions.displayName(who));
        assertEquals(locale, AudienceExtensions.locale(who));
        assertTrue(AudienceExtensions.hasPermission(who, "x"));
        assertEquals(Component.text("Bob"), AudienceExtensions.displayName(anonymous));
        assertNull(AudienceExtensions.locale(anonymous));
    }

    private static class TestPlayer implements OnlinePlayer {
        private final UUID uniqueId;

        TestPlayer(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        @Override
        public @NotNull UUID getUniqueId() {
            return uniqueId;
        }

        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers("Alice", uniqueId, permission -> permission.equals("example.use"));
        }

        @Override
        public void sendMessage(@NotNull Component text) {
        }

        @Override
        public void sendActionBar(@NotNull Component text) {
        }

        @Override
        public void playSound(@NotNull Sound sound) {
        }
    }
}
