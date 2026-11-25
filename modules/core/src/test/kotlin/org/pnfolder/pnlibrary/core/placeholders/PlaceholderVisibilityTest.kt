package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.registry.Visibility

class PlaceholderVisibilityTest {
    private val problems = mutableListOf<String>()
    private val engine = PlaceholderEngine { problems += it }

    private fun read(text: String) = engine.render(text, ViewerInfo.none()).toCompletableFuture().get()

    @Test
    fun `module visibility hides a placeholder from other modules and outsiders`() {
        val mine = engine.scope("shop/main")
        mine.value("shop.secret", "", null, Visibility.MODULE) { 1 }

        assertTrue(mine.contains("shop.secret"))
        assertFalse(engine.scope("shop/other").contains("shop.secret"))
        assertFalse(engine.contains("shop.secret"))
    }

    @Placeholders("shop")
    class Holder {
        @Placeholder("open")
        val price: Reply<Int> get() = Reply.of(1)

        @Placeholder("closed", visibility = Visibility.MODULE)
        val counter: Reply<Int> get() = Reply.of(2)
    }

    @Test
    fun `visibility on a property hides only that placeholder and the operator test still sees it`() {
        engine.scope("shop/main").register(Holder(), papi = null)

        assertTrue(engine.contains("shop.price"))
        assertFalse(engine.contains("shop.counter"))
        assertTrue(engine.scope("shop/main").contains("shop.counter"))
        assertEquals("2", engine.resolveAll("shop.counter").toCompletableFuture().get().toString())
    }

    @Test
    fun `plugin visibility reaches the modules of the same plugin only`() {
        engine.scope("shop/main").value("shop.price", "", null, Visibility.PLUGIN) { 1 }

        assertTrue(engine.scope("shop/other").contains("shop.price"))
        assertFalse(engine.scope("bank/main").contains("shop.price"))
        assertFalse(engine.contains("shop.price"))
    }

    @Test
    fun `the nearest placeholder wins and the clash is reported`() {
        engine.scope("bank/main").value("shop.price") { "far" }
        engine.scope("shop/main").value("shop.price") { "near" }

        assertEquals("near", engine.scope("shop/main").render("{shop.price}", ViewerInfo.none()).toCompletableFuture().get())
        assertTrue(problems.any { it.contains("shop.price") })
    }

    @Test
    fun `a closed module takes its converters and placeholders away`() {
        val module = engine.scope("shop/main")
        module.value("shop.coins") { 1 }
        module.converter(Thread::class.java, object : ArgumentConverter<Thread> {
                override val label = "thread"

                override fun read(input: ArgumentInput): Thread? = null
            })
        module.close()

        assertFalse(engine.contains("shop.coins"))
        assertEquals("{shop.coins}", read("{shop.coins}"))
    }

    private fun ask(module: PlaceholderService, text: String) = module.render(text, ViewerInfo.none()).toCompletableFuture().get()

    @Test
    fun `an owner in front of the address picks who answers`() {
        engine.scope("bank/main").value("shop.price") { "bank" }
        val shop = engine.scope("shop/main")
        shop.value("shop.price") { "shop" }
        val other = engine.scope("shop/other")

        assertEquals("bank", ask(shop, "{bank/main@shop.price}"))
        assertEquals("shop", ask(other, "{shop@shop.price}"))
        assertEquals("shop", ask(shop, "{~~@shop.price}"))
        assertEquals("{~~@shop.price}", ask(other, "{~~@shop.price}"))
        assertEquals("shop", ask(other, "{~@shop.price}"))
        assertEquals("{~~@shop.price}", read("{~~@shop.price}"))
    }

    @Test
    fun `a format can be picked by its owner`() {
        engine.scope("shop/main").value("shop.name") { "abc" }
        val module = engine.scope("a/main")

        assertEquals("ABC", ask(module, "{shop.name|~~~@upper}"))
        assertEquals("abc", ask(module, "{shop.name|bank@upper}"))
    }

    @Test
    fun `an owner of the placeholder and of the format work together`() {
        engine.scope("shop/main").value("shop.name") { "abc" }
        val module = engine.scope("a/main")

        assertEquals("ABC", ask(module, "{shop/main@shop.name|~~~@upper}"))
    }

    @Test
    fun `a placeholder can be inside another one`() {
        val shop = engine.scope("shop/main")
        shop.value("shop.player") { "alex" }
        shop.value("shop.alex.level") { 7 }

        assertEquals("7", ask(shop, "{shop.{shop.player}.level}"))
        assertEquals("[{shop.{shop.nobody}.level}]", ask(shop, "[{shop.{shop.nobody}.level}]"))
    }

    @Test
    fun `placeholders inside placeholders go deep, in every place`() {
        val shop = engine.scope("shop/main")
        shop.value("shop.a") { "b" }
        shop.value("shop.b") { "c" }
        shop.value("shop.c") { "d" }
        shop.value("shop.d") { 4 }
        shop.value("shop.owner") { "shop/main" }
        shop.value("shop.width") { 5 }
        shop.value("shop.name") { "ab" }
        shop.value("shop.one.x.two.y") { "both" }
        shop.value("shop.x") { "x" }
        shop.value("shop.y") { "y" }
        shop.value("shop.text") { "ab" }

        assertEquals("4", ask(shop, "{shop.{shop.{shop.{shop.a}}}}"))
        assertEquals("both", ask(shop, "{shop.one.{shop.x}.two.{shop.y}}"))
        assertEquals("ab", ask(shop, "{shop.name|~~~@upper}").lowercase())
        assertEquals("ab", ask(shop, "{{shop.owner}@shop.text}"))
    }

    @Test
    fun `too deep placeholders are refused and reported`() {
        val shop = engine.scope("shop/main")
        shop.value("shop.a") { 1 }
        val text = "{shop.".repeat(40) + "a" + "}".repeat(40)

        assertEquals(text, ask(shop, text))
        assertTrue(problems.any { it.contains("inside each other") })
    }

    @Test
    fun `a value with braces is not read again`() {
        val shop = engine.scope("shop/main")
        shop.value("shop.curly") { "{shop.curly}" }

        assertEquals("{shop.curly}", ask(shop, "{shop.curly}"))
        assertEquals("{shop.x.{shop.curly}}", ask(shop, "{shop.x.{shop.curly}}"))
    }

    @Test
    fun `values of a run are skipped when an owner is written`() {
        val shop = engine.scope("shop/main")
        val values = mapOf("item" to "local")
        fun ask(text: String) = shop.render(text, ViewerInfo.none(), values).toCompletableFuture().get()
        shop.value("shop.item") { "registered" }

        assertEquals("local", ask("{item}"))
        assertEquals("{~~@item}", ask("{~~@item}"))
        assertEquals("registered", ask("{~~@shop.item}"))
    }
}
