package org.pnfolder.pnlibrary.core.cooldowns

import net.kyori.adventure.audience.Audience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.audience.hasPermission
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.api.commands.error.CommandException
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick
import java.time.Duration
import java.util.UUID

class NamedCooldownTest {
    private val service = InMemoryCooldowns()
    private val home = service.cooldown("home", Duration.ofSeconds(30), bypassPermission = "shop.home.nocooldown")

    private fun player(id: UUID = UUID.randomUUID(), vararg permissions: String): Audience = object : Audience {
        override fun pointers() = senderPointers("Alice", id) { it in permissions }
    }

    @Test
    fun `a player is held back after the first use and reads the time left`() {
        val alice = player()

        assertTrue(home.acquire(alice).allowed)
        val second = home.acquire(alice)

        assertTrue(second.isDenied)
        assertTrue(second.remainingSeconds in 1..30)
        assertTrue(home.remaining(alice) > Duration.ZERO)
    }

    @Test
    fun `the console and a player with the bypass permission are never held back`() {
        val console = object : Audience {
            override fun pointers() = senderPointers("Console") { true }
        }
        val vip = player(permissions = arrayOf("shop.home.nocooldown"))

        repeat(2) {
            assertTrue(home.acquire(console).allowed)
            assertTrue(home.acquire(vip).allowed)
        }
        assertEquals(Duration.ZERO, home.remaining(vip))
    }

    @Test
    fun `require stops the command with an OnCooldown answer`() {
        val alice = player()
        home.require(alice)

        val failure = assertThrows(CommandException::class.java) { home.require(alice) }

        assertTrue(failure.error is OnCooldown)
        assertTrue((failure.error as OnCooldown).remaining > Duration.ZERO)
    }

    @Test
    fun `reset ends the cooldown and the same action by uuid is shared`() {
        val id = UUID.randomUUID()
        assertTrue(home.acquire(id).allowed)
        assertTrue(home.acquire(player(id)).isDenied)

        assertTrue(home.reset(id))
        assertFalse(home.acquire(id).isDenied)
    }

    @Test
    fun `a blank action and a negative length are refused`() {
        assertThrows(IllegalArgumentException::class.java) { service.cooldown("  ", Duration.ofSeconds(1)) }
        assertThrows(IllegalArgumentException::class.java) { service.cooldown("x", Duration.ofSeconds(-1)) }
    }

    private fun named(configure: (org.pnfolder.pnlibrary.api.cooldowns.Cooldown.Builder) -> Unit) =
        service.cooldown("kit") { configure(it) }

    @Test
    fun `run does the action once and calls the own reaction instead when the player has to wait`() {
        val denied = mutableListOf<Long>()
        val kit = named { it.duration(Duration.ofMinutes(5)).onDenied { d -> denied += d.remainingSeconds } }
        val alice = player()
        var given = 0

        assertTrue(kit.run(alice) { given++ }.allowed)
        assertTrue(kit.run(alice) { given++ }.isDenied)

        assertEquals(1, given)
        assertEquals(1, denied.size)
        assertTrue(denied.single() in 1..300)
    }

    @Test
    fun `without an own reaction the player gets the library's message`() {
        val messages = mutableListOf<String>()
        val id = UUID.randomUUID()
        val alice = object : Audience {
            override fun pointers() = senderPointers("Alice", id) { false }
            override fun sendMessage(message: net.kyori.adventure.text.Component) {
                messages += net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message)
            }
        }
        val kit = named { it.duration(Duration.ofMinutes(5)) }

        kit.run(alice) { }
        kit.run(alice) { }

        assertEquals(1, messages.size)
        assertTrue(messages.single().startsWith("Подождите"), messages.single())
    }

    @Test
    fun `a tier gives a donor a shorter wait and the first tier held wins`() {
        val kit = named {
            it.duration(Duration.ofHours(1)).tier("shop.donor", Duration.ofMinutes(20)).tier("shop.vip", Duration.ofMinutes(5))
        }
        val plain = player()
        val donor = player(permissions = arrayOf("shop.donor", "shop.vip"))

        assertEquals(Duration.ofHours(1), kit.durationFor(plain))
        assertEquals(Duration.ofMinutes(20), kit.durationFor(donor))
    }

    @Test
    fun `onStarted hears the length that applies to the one who started it`() {
        val started = mutableListOf<Duration>()
        val kit = named { it.duration(Duration.ofHours(1)).tier("shop.donor", Duration.ofMinutes(20)).onStarted { s -> started += s.duration } }

        kit.acquire(player(permissions = arrayOf("shop.donor")))
        kit.acquire(player())

        assertEquals(listOf(Duration.ofMinutes(20), Duration.ofHours(1)), started)
    }

    @Test
    fun `a shared cooldown holds everybody back once anyone used it`() {
        val kit = named { it.duration(Duration.ofMinutes(5)).shared() }

        assertTrue(kit.acquire(player()).allowed)
        assertTrue(kit.acquire(player()).isDenied)
        assertFalse(kit.isReady(player()))
    }

    @Test
    fun `a cooldown without a duration is refused`() {
        assertThrows(IllegalArgumentException::class.java) { service.cooldown("x") { } }
    }

    @Test
    fun `a tier may need any of several permissions and may be longer or shorter than the base`() {
        val kit = named {
            it.duration(Duration.ofMinutes(10))
                .tier(listOf("shop.vip", "shop.donor"), Duration.ofMinutes(2))
                .tier("shop.newbie", 2.0)
                .tier("shop.trial", 0.5)
        }

        assertEquals(Duration.ofMinutes(10), kit.durationFor(player()))
        assertEquals(Duration.ofMinutes(2), kit.durationFor(player(permissions = arrayOf("shop.donor"))))
        assertEquals(Duration.ofMinutes(2), kit.durationFor(player(permissions = arrayOf("shop.vip"))))
        assertEquals(Duration.ofMinutes(20), kit.durationFor(player(permissions = arrayOf("shop.newbie"))))
        assertEquals(Duration.ofMinutes(5), kit.durationFor(player(permissions = arrayOf("shop.trial"))))
    }

    @Test
    fun `when several tiers fit the pick says which one counts`() {
        fun lengthWith(pick: TierPick): Duration {
            val kit = named {
                it.duration(Duration.ofMinutes(10)).tier("a", Duration.ofMinutes(5)).tier("b", Duration.ofMinutes(1)).tier("c", Duration.ofMinutes(30)).pick(pick)
            }
            return kit.durationFor(player(permissions = arrayOf("a", "b", "c")))
        }

        assertEquals(Duration.ofMinutes(5), lengthWith(TierPick.FIRST))
        assertEquals(Duration.ofMinutes(1), lengthWith(TierPick.SHORTEST))
        assertEquals(Duration.ofMinutes(30), lengthWith(TierPick.LONGEST))
    }

    @Test
    fun `any one of several bypass permissions or the own condition lets a player through`() {
        val kit = named {
            it.duration(Duration.ofMinutes(10)).bypass("shop.admin", "shop.staff").bypassIf { who -> who.hasPermission("shop.event") }
        }
        val staff = player(permissions = arrayOf("shop.staff"))
        val event = player(permissions = arrayOf("shop.event"))
        val plain = player()

        repeat(2) {
            assertTrue(kit.acquire(staff).allowed)
            assertTrue(kit.acquire(event).allowed)
        }
        assertTrue(kit.acquire(plain).allowed)
        assertTrue(kit.acquire(plain).isDenied)
    }

    @Test
    fun `a tier without a permission and a negative factor are refused`() {
        assertThrows(IllegalArgumentException::class.java) { named { it.duration(Duration.ofSeconds(1)).tier(emptyList(), Duration.ZERO) } }
        assertThrows(IllegalArgumentException::class.java) { named { it.duration(Duration.ofSeconds(1)).tier("x", -1.0) } }
    }
}
