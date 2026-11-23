package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentAdapters
import org.pnfolder.pnlibrary.api.placeholders.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Format
import org.pnfolder.pnlibrary.api.placeholders.Group
import org.pnfolder.pnlibrary.api.placeholders.Hide
import org.pnfolder.pnlibrary.api.placeholders.Invalid
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.PlayerRef
import org.pnfolder.pnlibrary.api.placeholders.PublishedAnswers
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.Route
import org.pnfolder.pnlibrary.api.placeholders.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.RouteKind
import org.pnfolder.pnlibrary.api.placeholders.UseFormat
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

enum class TestPeriod { DAY, WEEK }

class TestWar {
    @Placeholder val score: Reply<Int> = Reply.of(340)

    @Placeholder val wins: Reply<Int> = Reply.of(2)

    @Hide @Placeholder val secretKey: Reply<String> = Reply.of("hidden")
}

class TestKillsQuery(@Argument val who: ArgumentValue<PlayerRef>, @Argument val period: ArgumentValue<String>)

class TestClan(val name: String)

@Placeholders("clan")
class TestClanValues {
    @Placeholder("Clan level")
    fun level(@Viewer viewer: ViewerInfo): Reply<Int> {
        viewer.requirePlayerId()
        return Reply.value(12)
    }

    @Placeholder fun war(): Reply<TestWar> = Reply.next(TestWar())

    @Placeholder val tag: Reply<String> = Reply.of("WLF")

    @Placeholder val owls: Reply<String> get() = Reply.of("owls")

    @Hide @Placeholder fun secret(): Reply<Int> = Reply.of(1)

    @Placeholder fun top(@Argument(range = "1..100") place: ArgumentValue<Int>): Reply<Int> = Reply.of(place.required() * 10)

    @Placeholder
    fun kills(@Argument who: ArgumentValue<PlayerRef>, @Argument(choices = ["day", "week"]) period: ArgumentValue<String>): Reply<Int> =
        Reply.of(who.required().text.length * 10 + if (period.required() == "week") 7 else 0)

    @Placeholder(onInvalid = Invalid.DEFAULT, default = "none")
    fun friend(@Argument who: ArgumentValue<PlayerRef>): Reply<String> = Reply.of(who.value?.text)

    @Placeholder fun mine(@Argument who: ArgumentValue<PlayerRef>, @Viewer viewer: ViewerInfo): Reply<String> = Reply.of(who.orMe(viewer)?.text)

    @Placeholder fun age(@Argument span: ArgumentValue<Duration>): Reply<Duration> = Reply.of(span.required())

    @Placeholder fun period(@Argument by: ArgumentValue<TestPeriod>): Reply<String> = Reply.of(by.required().name)

    @Placeholder fun query(@Group query: TestKillsQuery): Reply<String> = Reply.of(query.who.required().text + "-" + query.period.required())

    @Placeholder @UseFormat("upper")
    fun title(): Reply<String> = Reply.of("wolf")

    @Placeholder fun money(): Reply<Long> = Reply.of(1_250_000)

}

@Placeholders("own")
class TestOwnType {
    @Placeholder fun clanOf(@Argument clan: ArgumentValue<TestClan>): Reply<Int> = Reply.of(clan.required().name.length)
}

class TestClanFormats {
    @Format("pad0")
    fun pad0(n: Int, @Argument width: ArgumentValue<Int>): String = n.toString().padStart(width.required(), '0')

    @Format("lang")
    fun lang(n: Int, @Viewer viewer: ViewerInfo): String = "$n/${viewer.language}"
}

@Placeholders("bad")
class TestNeedsUnknown {
    @Placeholder fun x(@Argument thread: ArgumentValue<Thread>): Reply<Int> = Reply.of(0)
}

class PlaceholderServiceTest {
    private val problems = mutableListOf<String>()
    private val engine = PlaceholderEngine { problems += it }

    private fun module(): PlaceholderService =
        engine.scope("pnclans/war").also {
            it.register(TestClanValues())
            it.formats(TestClanFormats())
        }

    private fun read(address: String, viewer: ViewerInfo = ViewerInfo.none()): Any? =
        engine.resolve(address, viewer).toCompletableFuture().get()

    @Test
    fun `plain values, branches and properties are placeholders`() {
        module()
        val player = ViewerInfo.of(UUID.randomUUID())

        assertEquals(12, read("clan.level", player))
        assertNull(read("clan.level"), "without a player the method ends quietly")
        assertEquals(340, read("clan.war.score"))
        assertEquals(2, read("{clan.war.wins}"))
        assertEquals("WLF", read("clan.tag"))
        assertEquals("owls", read("clan.owls"), "a property without a field works too")
    }

    @Test
    fun `hidden members and unknown addresses give nothing`() {
        module()

        assertNull(read("clan.secret"))
        assertNull(read("clan.war.secretkey"))
        assertNull(read("nobody.here"))
        assertNull(read("clan"))
    }

    @Test
    fun `arguments arrive converted and checked`() {
        module()

        assertEquals(30, read("clan.top.3"))
        assertNull(read("clan.top.500"), "outside the range")
        assertNull(read("clan.top.x"), "not a number")
        assertEquals(47, read("clan.kills.Alex.week"))
        assertNull(read("clan.kills.Alex.year"), "a word outside the list")
        assertEquals("WEEK", read("clan.period.week"))
        assertNull(read("clan.period.year"))
        assertEquals("Alex-week", read("clan.query.Alex.week"))
    }

    @Test
    fun `the method sees the text and me stands for the viewer`() {
        module()
        val id = UUID.randomUUID()

        assertEquals("Bob", read("clan.friend.Bob"))
        assertEquals(id.toString(), read("clan.mine.me", ViewerInfo.of(id)))
        assertEquals("Alex", read("clan.mine.Alex", ViewerInfo.of(id)))
    }

    @Test
    fun `formats of the text, of the method and of a module`() {
        module()

        assertEquals("WOLF", read("clan.title"))
        assertEquals("wolf", read("clan.title|lower"), "a format in the text replaces the default")
        assertEquals("wolf", read("clan.title|raw"), "raw switches the default format off")
        assertEquals("00010", read("clan.top.1|pad0:5"))
        assertEquals("30/ru", read("clan.top.3|lang", ViewerInfo(null, "ru")))
    }

    @Test
    fun `built in formats follow the language of the viewer`() {
        module()
        val ru = ViewerInfo(null, "ru")
        val en = ViewerInfo(null, "en")

        assertEquals("1 250 000", read("clan.money|group", ru))
        assertEquals("1,250,000", read("clan.money|group", en))
        assertEquals("1,3M", read("clan.money|short", ru))
        assertEquals("1.3M", read("clan.money|short", en))
        assertEquals("1h 30m", read("clan.age.1h30m|left", en))
        assertEquals("1ч 30м", read("clan.age.1h30m|left", ru))
        assertEquals("01:30:00", read("clan.age.1h30m|hms"))
        assertEquals("есть", read("clan.top.1|ifpos:есть,нет"))
        assertEquals("[WLF]", read("clan.tag|wrap:[,]"))
        assertEquals("WL…", read("clan.tag|cut:2"))
    }

    @Test
    fun `a format that does not fit leaves the value and reports it`() {
        module()

        assertEquals("WLF", read("clan.tag|short"))
        assertTrue(problems.any { "short" in it }, problems.toString())
    }

    @Test
    fun `render fills the texts and leaves unknown addresses`() {
        module()
        val text = engine.render("Level {clan.war.score|group}, tag {clan.tag}, {nobody.here}!").toCompletableFuture().get()

        assertEquals("Level 340, tag WLF, {nobody.here}!", text)
    }

    @Test
    fun `a part with a dot or a bar is written in quotes or with a tilde code`() {
        module()

        assertEquals("a.b", read("clan.friend.\"a.b\""))
        assertEquals("a|b", Addresses.parse("clan.friend.a~7Cb")!!.words.last())
    }

    @Test
    fun `closing the module releases its placeholders and formats`() {
        val module = module()
        assertEquals(340, read("clan.war.score"))

        module.close()

        assertNull(read("clan.war.score"))
        assertTrue(engine.list().isEmpty())
    }

    @Test
    fun `the list shows every placeholder with its owner and description`() {
        module()
        val list = engine.list()

        val level = list.first { it.address == "clan.level" }
        assertEquals("Clan level", level.description)
        assertEquals("pnclans/war", level.owner)
        assertTrue(list.none { "secret" in it.address }, "hidden ones are not listed")
    }

    @Test
    fun `an argument type without an adapter is reported where it is written`() {
        val failure = assertThrows(RuntimeException::class.java) { engine.scope("x/y").register(TestNeedsUnknown()) }
        assertTrue(failure.message.orEmpty().contains("Thread"), failure.message)
    }

    @Test
    fun `a plugin can teach the library its own argument type`() {
        ArgumentAdapters.register<TestClan>("clan") { text -> if (text == "Wolves") TestClan(text) else null }
        try {
            engine.scope("x/own").register(TestOwnType())

            assertEquals(6, read("own.clanof.Wolves"))
            assertNull(read("own.clanof.Owls"))
        } finally {
            ArgumentAdapters.unregister(TestClan::class.java)
        }
    }

    @Test
    fun `values handed over by the caller win and take formats`() {
        module()

        val values = mapOf<String, Any?>("amount" to 1500, "clan.tag" to "mine")

        assertEquals(1500, engine.resolve("amount", ViewerInfo.none(), values).toCompletableFuture().get())
        assertEquals("1,500", engine.resolve("{amount|group}", ViewerInfo.none(), values).toCompletableFuture().get())
        assertEquals("mine", engine.resolve("clan.tag", ViewerInfo.none(), values).toCompletableFuture().get())
        assertEquals("a 1500 b", engine.render("a {amount} b", ViewerInfo.none(), values).toCompletableFuture().get())
    }

    @Test
    fun `a placeholder without a class can be added and removed`() {
        val module = engine.scope("shop/main")
        val handle = module.value("shop.coins", "Coins of the player") { viewer -> if (viewer.hasPlayer) 5 else null }

        assertEquals(5, read("shop.coins", ViewerInfo.of(UUID.randomUUID())))
        assertNull(read("shop.coins"))
        assertTrue(module.contains("shop.coins"))
        assertTrue(engine.list().any { it.address == "shop.coins" && it.description == "Coins of the player" })

        handle.close()

        assertTrue(!module.contains("shop.coins"))
        assertNull(read("shop.coins", ViewerInfo.of(UUID.randomUUID())))
    }

    private class FakePapi : PlaceholderAdapter {
        var open = true
        val shown = mutableMapOf<String, PublishedAnswers>()
        override val id = "placeholderapi"
        override val available get() = open

        override fun render(playerId: UUID?, text: String) = text.replace("%server_name%", "Lobby")

        override fun publish(prefix: String, answers: PublishedAnswers): AutoCloseable {
            shown[prefix] = answers
            return AutoCloseable { shown.remove(prefix) }
        }
    }

    @Test
    fun `placeholders with a prefix are shown to PlaceholderAPI and answered from there`() {
        val papi = FakePapi()
        engine.register(papi)
        val module = engine.scope("pnclans/war")
        module.register(TestClanValues(), papi = "pnclans")

        val answers = papi.shown.getValue("pnclans")
        assertEquals("340", answers.answer(null, "clan_war_score", 0))
        assertEquals("30", answers.answer(null, "clan_top_3", 0))
        assertNull(answers.answer(null, "nobody_here", 0))
        assertTrue(engine.list().first { it.address == "clan.war.score" }.published.contains("pnclans_clan_war_score"))

        module.close()

        assertTrue(papi.shown.isEmpty(), "closing the module withdraws the prefix")
    }

    @Test
    fun `an adapter that comes later still gets the prefixes already registered`() {
        engine.scope("pnclans/war").register(TestClanValues(), papi = "pnclans")
        val papi = FakePapi()

        engine.register(papi)

        assertTrue("pnclans" in papi.shown)
    }

    @Test
    fun `texts get the tokens of the outside system filled`() {
        engine.register(FakePapi())
        module()

        val text = engine.render("{clan.tag} on %server_name%").toCompletableFuture().get()

        assertEquals("WLF on Lobby", text)
    }

    @Test
    fun `a module without PlaceholderAPI keeps its prefix to itself`() {
        val papi = FakePapi()
        engine.register(papi)

        engine.scope("pnclans/war", papiEnabled = false).register(TestClanValues(), papi = "pnclans")

        assertTrue(papi.shown.isEmpty())
    }

    @Test
    fun `the library placeholders read its numbers`() {
        val identity = org.mockito.Mockito.mock(org.pnfolder.pnlibrary.spi.platform.PlatformIdentity::class.java)
        engine.scope("pnlibrary/main").register(LibraryPlaceholders(identity, 0L, { 3 }, { mapOf("errors" to 7L) }))

        assertEquals(3, read("pnlibrary.plugins"))
        assertEquals(7L, read("pnlibrary.stats.errors"))
        assertEquals(0L, read("pnlibrary.stats.warnings"))
    }
}

class TestRouteBranch(val seen: MutableList<RouteInfo>) {
    @Placeholder val score: Reply<Int> = Reply.of(5)

    @Placeholder val label: Reply<String> = Reply.of("ok")
}

@Placeholders("route")
class TestRouteValues(private val seen: MutableList<RouteInfo>) {
    @Placeholder
    fun war(@Viewer viewer: ViewerInfo, @Route route: RouteInfo): Reply<TestRouteBranch> {
        seen += route
        if (viewer.playerId == null) return Reply.stop(if (route.field?.text == "score") 0 else "no clan")
        return Reply.next(TestRouteBranch(seen))
    }

    @Placeholder(onInvalid = Invalid.EMPTY)
    fun lost(): Reply<TestRouteBranch> = Reply.none()
}

@Placeholders("tail")
class TestTailValues {
    @Placeholder(onInvalid = Invalid.DEFAULT, default = "-")
    fun lost(): Reply<TestRouteBranch> = Reply.none()
}

class PlaceholderRouteTest {
    private val engine = PlaceholderEngine { }
    private val seen = mutableListOf<RouteInfo>()

    private fun read(address: String, viewer: ViewerInfo = ViewerInfo.none()): Any? =
        engine.resolve(address, viewer).toCompletableFuture().get()

    @Test
    fun `a branch method sees what every part of the address is`() {
        engine.scope("test/route").register(TestRouteValues(seen))
        read("route.war.score|pad0:4", ViewerInfo.of(UUID.randomUUID()))

        val route = seen.single()
        assertEquals(
            listOf(RouteKind.GROUP, RouteKind.BRANCH, RouteKind.FIELD, RouteKind.FORMAT),
            route.parts.map { it.kind },
        )
        assertEquals("route.war.score", route.path)
        assertEquals(listOf("4"), route.of(RouteKind.FORMAT).single().arguments)
        assertTrue(route.hasFormat("pad0"))
        assertEquals(listOf("score"), route.after("war").filter { it.kind == RouteKind.FIELD }.map { it.text })
    }

    @Test
    fun `answer ends the address with its own value`() {
        engine.scope("test/route").register(TestRouteValues(seen))

        assertEquals(0, read("route.war.score"))
        assertEquals("no clan", read("route.war.label"))
    }

    @Test
    fun `a branch without value covers its text placeholders`() {
        engine.scope("test/route").register(TestRouteValues(seen), TestTailValues())

        assertEquals("", read("route.lost.score"))
        assertEquals("", read("route.lost.label"))
        assertEquals("-", read("tail.lost.label"))
    }
}

class TestBox(val name: String)

class BoxByName : ArgumentConverter<TestBox> {
    override val label = "box"

    override fun read(input: ArgumentInput): TestBox? =
        if (input.text == "me") TestBox("mine") else if (input.text == "none") input.fail("no box called none") else TestBox(input.text)

    override fun suggest(input: ArgumentInput): List<String> = listOf("alpha", "beta").filter { it.startsWith(input.text) }
}

class BoxLater : ArgumentConverter<TestBox> {
    override val label = "late box"

    override fun read(input: ArgumentInput): TestBox? = null

    override fun readLater(input: ArgumentInput): CompletionStage<TestBox?> =
        CompletableFuture.supplyAsync { TestBox(input.text.uppercase()) }
}

class SecondByFirst : ArgumentConverter<String> {
    override val label = "second"

    override fun read(input: ArgumentInput): String =
        "${input.previous.joinToString { it.text }}/${input.position}/${input.name}/${input.route.field?.text}"
}

@Placeholders("box")
class TestBoxValues {
    @Placeholder fun named(@Argument(converter = BoxByName::class) box: ArgumentValue<TestBox>): Reply<String> =
        Reply.of(box.value?.name ?: "failed: ${box.error}")

    @Placeholder fun later(@Argument(converter = BoxLater::class) box: ArgumentValue<TestBox>): Reply<String> = Reply.of(box.required().name)

    @Placeholder
    fun chain(
        @Argument first: ArgumentValue<String>,
        @Argument(name = "second", converter = SecondByFirst::class) second: ArgumentValue<String>,
    ): Reply<String> = Reply.of(second.required())
}

class PlaceholderConverterTest {
    private val engine = PlaceholderEngine { }

    private fun read(address: String): Any? = engine.resolve(address).toCompletableFuture().get()

    @Test
    fun `a converter named on the parameter reads the text`() {
        engine.scope("test/box").register(TestBoxValues())

        assertEquals("alpha", read("box.named.alpha"))
        assertEquals("mine", read("box.named.me"))
    }

    @Test
    fun `a converter says why the text does not fit`() {
        engine.scope("test/box").register(TestBoxValues())

        assertEquals("failed: no box called none", read("box.named.none"))
    }

    @Test
    fun `a converter may answer later`() {
        engine.scope("test/box").register(TestBoxValues())

        assertEquals("ABC", read("box.later.abc"))
    }

    @Test
    fun `a converter sees the arguments before it and the route`() {
        engine.scope("test/box").register(TestBoxValues())

        assertEquals("one/1/second/chain", read("box.chain.one.two"))
    }
}

class TestLateLeaf {
    @Placeholder val score: Reply<Int> = Reply.of(7)

    @Placeholder val label: Reply<String> = Reply.of("late")
}

class TestLateMiddle {
    @Placeholder
    fun deeper(): CompletableFuture<Reply<TestLateLeaf>> = CompletableFuture.supplyAsync { Reply.next(TestLateLeaf()) }
}

@Placeholders("late")
class TestLateValues {
    @Placeholder
    fun branch(): CompletableFuture<Reply<TestLateMiddle>> = CompletableFuture.supplyAsync { Reply.next(TestLateMiddle()) }

    @Placeholder
    fun missing(): CompletableFuture<Reply<TestLateLeaf>> = CompletableFuture.supplyAsync { Reply.none() }

    @Placeholder
    fun stopped(): CompletableFuture<Reply<TestLateLeaf>> = CompletableFuture.supplyAsync { Reply.stop("stopped here") }

    @Placeholder
    fun withArgument(@Argument id: ArgumentValue<Int>): CompletableFuture<Reply<TestLateLeaf>> =
        CompletableFuture.supplyAsync { if (id.required() > 5) Reply.stop("big") else Reply.next(TestLateLeaf()) }
}

class PlaceholderLateBranchTest {
    private val engine = PlaceholderEngine { }

    private fun read(address: String): Any? = engine.resolve(address).toCompletableFuture().get()

    @Test
    fun `a branch may be answered later, also several in a row`() {
        engine.scope("test/late").register(TestLateValues())

        assertEquals(7, read("late.branch.deeper.score"))
        assertEquals("late", read("late.branch.deeper.label"))
    }

    @Test
    fun `a branch that is empty later ends the address quietly`() {
        engine.scope("test/late").register(TestLateValues())

        assertNull(read("late.missing.score"))
    }

    @Test
    fun `an answer from a late branch ends the address`() {
        engine.scope("test/late").register(TestLateValues())

        assertEquals("stopped here", read("late.stopped.score"))
        assertEquals("big", read("late.withargument.9.score"))
        assertEquals(7, read("late.withargument.2.score"))
    }

    @Test
    fun `late branches are listed like others`() {
        engine.scope("test/late").register(TestLateValues())

        assertTrue(engine.list().any { it.address == "late.branch.deeper.score" })
    }
}

class TestReplyWar(private val points: Int, private val victories: Int) {
    @Placeholder val score: Reply<Int> get() = Reply.of(points)

    @Placeholder val wins: Reply<Int> get() = Reply.of(victories)
}

@Placeholders("reply")
class TestReplyValues {
    @Placeholder
    fun level(@Viewer viewer: ViewerInfo): Reply<Int> = if (viewer.playerId == null) Reply.none() else Reply.value(12)

    @Placeholder
    fun money(): Reply<Long> = Reply.value(1_250L, format = "coins")

    @Placeholder
    fun war(@Viewer viewer: ViewerInfo): Reply<TestReplyWar> = if (viewer.playerId == null) Reply.stop("no clan") else Reply.next(TestReplyWar(340, 2))

    @Placeholder
    fun warLater(@Viewer viewer: ViewerInfo): CompletableFuture<Reply<TestReplyWar>> =
        CompletableFuture.supplyAsync { if (viewer.playerId == null) Reply.stop("no clan later") else Reply.next(TestReplyWar(5, 1)) }

    @Placeholder
    fun plainWar(): Reply<TestReplyWar> = Reply.next(TestReplyWar(1, 1))

    @Placeholder
    fun left(): Reply<Duration> = Reply.of(Duration.ofMinutes(95))
}

class TestReplyFormats {
    @Format("coins")
    fun coins(amount: Long): String = "$amount coins"
}

class PlaceholderReplyTest {
    private val engine = PlaceholderEngine { }
    private val player = ViewerInfo.of(UUID.randomUUID())

    init {
        engine.scope("test/reply").also {
            it.formats(TestReplyFormats())
            it.register(TestReplyValues())
        }
    }

    private fun read(address: String, viewer: ViewerInfo = ViewerInfo.none()): Any? = engine.resolve(address, viewer).toCompletableFuture().get()

    @Test
    fun `value and none`() {
        assertEquals(12, read("reply.level", player))
        assertNull(read("reply.level"))
    }

    @Test
    fun `a value brings its own format`() {
        assertEquals("1250 coins", read("reply.money"))
        assertEquals(1250L, read("reply.money|raw"))
    }

    @Test
    fun `next goes on and stop answers from the branch`() {
        assertEquals(340, read("reply.war.score", player))
        assertEquals("no clan", read("reply.war.score"))
        assertEquals("no clan", read("reply.war.wins"))
    }

    @Test
    fun `a reply may come later`() {
        assertEquals(5, read("reply.warlater.score", player))
        assertEquals("no clan later", read("reply.warlater.score"))
    }

    @Test
    fun `a plain object still continues the address`() {
        assertEquals(1, read("reply.plainwar.score"))
    }

    @Test
    fun `a duration reads as time without asking for a format`() {
        assertEquals("1h 35m", read("reply.left"))
    }

    @Test
    fun `the list shows branches by what Reply holds`() {
        val addresses = engine.list().map { it.address }
        assertTrue("reply.war.score" in addresses)
        assertTrue("reply.level" in addresses)
    }
}
