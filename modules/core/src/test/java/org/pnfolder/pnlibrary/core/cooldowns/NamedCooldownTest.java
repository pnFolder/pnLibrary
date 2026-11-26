package org.pnfolder.pnlibrary.core.cooldowns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.error.CommandException;
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown;
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown;
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick;

class NamedCooldownTest {
    private final InMemoryCooldowns service = new InMemoryCooldowns();
    private final Cooldown home = service.cooldown("home", Duration.ofSeconds(30), "shop.home.nocooldown");

    private static Audience player(UUID id, String... permissions) {
        final Set<String> held = new HashSet<>(Arrays.asList(permissions));
        final Pointers pointers = AudienceExtensions.senderPointers("Alice", id, held::contains);
        return new Audience() {
            @Override
            public Pointers pointers() {
                return pointers;
            }
        };
    }

    private static Audience player(String... permissions) {
        return player(UUID.randomUUID(), permissions);
    }

    private Cooldown named(Consumer<Cooldown.Builder> configure) {
        return service.cooldown("kit", configure);
    }

    @Test
    void aPlayerIsHeldBackAfterTheFirstUseAndReadsTheTimeLeft() {
        Audience alice = player();

        assertTrue(home.acquire(alice).getAllowed());
        org.pnfolder.pnlibrary.api.cooldowns.CooldownResult second = home.acquire(alice);

        assertTrue(second.isDenied());
        assertTrue(second.getRemainingSeconds() >= 1 && second.getRemainingSeconds() <= 30);
        assertTrue(home.remaining(alice).compareTo(Duration.ZERO) > 0);
    }

    @Test
    void theConsoleAndAPlayerWithTheBypassPermissionAreNeverHeldBack() {
        final Pointers pointers = AudienceExtensions.senderPointers("Console", name -> true);
        Audience console = new Audience() {
            @Override
            public Pointers pointers() {
                return pointers;
            }
        };
        Audience vip = player("shop.home.nocooldown");

        for (int i = 0; i < 2; i++) {
            assertTrue(home.acquire(console).getAllowed());
            assertTrue(home.acquire(vip).getAllowed());
        }
        assertEquals(Duration.ZERO, home.remaining(vip));
    }

    @Test
    void requireStopsTheCommandWithAnOnCooldownAnswer() {
        Audience alice = player();
        home.require(alice);

        CommandException failure = assertThrows(CommandException.class, () -> home.require(alice));

        assertTrue(failure.getError() instanceof OnCooldown);
        assertTrue(((OnCooldown) failure.getError()).getRemaining().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void resetEndsTheCooldownAndTheSameActionByUuidIsShared() {
        UUID id = UUID.randomUUID();
        assertTrue(home.acquire(id).getAllowed());
        assertTrue(home.acquire(player(id)).isDenied());

        assertTrue(home.reset(id));
        assertFalse(home.acquire(id).isDenied());
    }

    @Test
    void aBlankActionAndANegativeLengthAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.cooldown("  ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> service.cooldown("x", Duration.ofSeconds(-1)));
    }

    @Test
    void runDoesTheActionOnceAndCallsTheOwnReactionInsteadWhenThePlayerHasToWait() {
        final List<Long> denied = new ArrayList<>();
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(5)).onDenied(d -> denied.add(d.getRemainingSeconds())));
        Audience alice = player();
        final int[] given = {0};

        assertTrue(kit.run(alice, () -> given[0]++).getAllowed());
        assertTrue(kit.run(alice, () -> given[0]++).isDenied());

        assertEquals(1, given[0]);
        assertEquals(1, denied.size());
        assertTrue(denied.get(0) >= 1 && denied.get(0) <= 300);
    }

    @Test
    void withoutAnOwnReactionThePlayerGetsTheLibrarysMessage() {
        final List<String> messages = new ArrayList<>();
        final Pointers pointers = AudienceExtensions.senderPointers("Alice", UUID.randomUUID(), name -> false);
        Audience alice = new Audience() {
            @Override
            public Pointers pointers() {
                return pointers;
            }

            @Override
            public void sendMessage(Component message) {
                messages.add(PlainTextComponentSerializer.plainText().serialize(message));
            }
        };
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(5)));

        kit.run(alice, () -> { });
        kit.run(alice, () -> { });

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).startsWith("Подождите"), messages.get(0));
    }

    @Test
    void aTierGivesADonorAShorterWaitAndTheFirstTierHeldWins() {
        Cooldown kit = named(b -> b.duration(Duration.ofHours(1))
            .tier("shop.donor", Duration.ofMinutes(20)).tier("shop.vip", Duration.ofMinutes(5)));
        Audience plain = player();
        Audience donor = player("shop.donor", "shop.vip");

        assertEquals(Duration.ofHours(1), kit.durationFor(plain));
        assertEquals(Duration.ofMinutes(20), kit.durationFor(donor));
    }

    @Test
    void onStartedHearsTheLengthThatAppliesToTheOneWhoStartedIt() {
        final List<Duration> started = new ArrayList<>();
        Cooldown kit = named(b -> b.duration(Duration.ofHours(1)).tier("shop.donor", Duration.ofMinutes(20))
            .onStarted(s -> started.add(s.getDuration())));

        kit.acquire(player("shop.donor"));
        kit.acquire(player());

        assertEquals(Arrays.asList(Duration.ofMinutes(20), Duration.ofHours(1)), started);
    }

    @Test
    void aSharedCooldownHoldsEverybodyBackOnceAnyoneUsedIt() {
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(5)).shared());

        assertTrue(kit.acquire(player()).getAllowed());
        assertTrue(kit.acquire(player()).isDenied());
        assertFalse(kit.isReady(player()));
    }

    @Test
    void aCooldownWithoutADurationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.cooldown("x", b -> { }));
    }

    @Test
    void aTierMayNeedAnyOfSeveralPermissionsAndMayBeLongerOrShorterThanTheBase() {
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(10))
            .tier(Arrays.asList("shop.vip", "shop.donor"), Duration.ofMinutes(2))
            .tier("shop.newbie", 2.0)
            .tier("shop.trial", 0.5));

        assertEquals(Duration.ofMinutes(10), kit.durationFor(player()));
        assertEquals(Duration.ofMinutes(2), kit.durationFor(player("shop.donor")));
        assertEquals(Duration.ofMinutes(2), kit.durationFor(player("shop.vip")));
        assertEquals(Duration.ofMinutes(20), kit.durationFor(player("shop.newbie")));
        assertEquals(Duration.ofMinutes(5), kit.durationFor(player("shop.trial")));
    }

    @Test
    void whenSeveralTiersFitThePickSaysWhichOneCounts() {
        assertEquals(Duration.ofMinutes(5), lengthWith(TierPick.FIRST));
        assertEquals(Duration.ofMinutes(1), lengthWith(TierPick.SHORTEST));
        assertEquals(Duration.ofMinutes(30), lengthWith(TierPick.LONGEST));
    }

    private Duration lengthWith(TierPick pick) {
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(10)).tier("a", Duration.ofMinutes(5))
            .tier("b", Duration.ofMinutes(1)).tier("c", Duration.ofMinutes(30)).pick(pick));
        return kit.durationFor(player("a", "b", "c"));
    }

    @Test
    void anyOneOfSeveralBypassPermissionsOrTheOwnConditionLetsAPlayerThrough() {
        Cooldown kit = named(b -> b.duration(Duration.ofMinutes(10)).bypass("shop.admin", "shop.staff")
            .bypassIf(who -> AudienceExtensions.hasPermission(who, "shop.event")));
        Audience staff = player("shop.staff");
        Audience event = player("shop.event");
        Audience plain = player();

        for (int i = 0; i < 2; i++) {
            assertTrue(kit.acquire(staff).getAllowed());
            assertTrue(kit.acquire(event).getAllowed());
        }
        assertTrue(kit.acquire(plain).getAllowed());
        assertTrue(kit.acquire(plain).isDenied());
    }

    @Test
    void aTierWithoutAPermissionAndANegativeFactorAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> named(b -> b.duration(Duration.ofSeconds(1)).tier(Collections.emptyList(), Duration.ZERO)));
        assertThrows(IllegalArgumentException.class,
            () -> named(b -> b.duration(Duration.ofSeconds(1)).tier("x", -1.0)));
    }
}
