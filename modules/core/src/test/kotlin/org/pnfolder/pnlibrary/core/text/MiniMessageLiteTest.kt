package org.pnfolder.pnlibrary.core.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType

class MiniMessageLiteTest {
    private data class Run(val text: String, val color: Int?, val decorations: Set<TextDecoration>)

    private fun runs(component: Component): List<Run> {
        val result = ArrayList<Run>()
        fun walk(node: Component, parent: Style) {
            val style = node.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if (node is TextComponent && node.content().isNotEmpty()) {
                val decorations = TextDecoration.values().filter { style.decoration(it) == TextDecoration.State.TRUE }.toSet()
                val previous = result.lastOrNull()
                val newline = node.content() == "\n" && previous != null
                result += if (newline) Run("\n", previous!!.color, previous.decorations)
                else Run(node.content(), style.color()?.value(), decorations)
            }
            node.children().forEach { walk(it, style) }
        }
        walk(component, Style.empty())
        return result.fold(ArrayList<Run>()) { merged, run ->
            val last = merged.lastOrNull()
            if (last != null && last.color == run.color && last.decorations == run.decorations) {
                merged[merged.size - 1] = last.copy(text = last.text + run.text)
            } else merged += run
            merged
        }
    }

    private val samples = listOf(
        "plain", "<red>red", "<red>a</red>b", "<red>a<blue>b</blue>c</red>d", "<red>a<blue>b</red>c",
        "<#ff8800>hex</#ff8800> tail", "<color:#00ff00>x</color>y", "<color:gold>x</color>", "<c:aqua>c</c>",
        "<bold>b</bold>n", "<b>b</b>", "<i>i</i>", "<em>e</em>", "<italic>x</italic>", "<u>u</u>", "<underlined>u</underlined>",
        "<st>s</st>", "<strikethrough>s</strikethrough>", "<obf>o</obf>", "<obfuscated>o</obfuscated>",
        "<red><bold>both</bold> red only", "<bold><red>x</red></bold>", "<red>a<reset>b", "a</red>b", "<bold>a<reset>b", "<bold>a<reset>b</bold>c",
        "line<br>two", "line<newline>two", "a \\<red> literal", "<unknown>text</unknown>", "1 < 2 and 3 > 2",
        "<RED>Upper</RED>", "<dark_red>d<dark_gray>g<gray>g2<light_purple>l<white>w", "<green>x</green><yellow>y</yellow>",
        "<red>unclosed <bold>nest", "<>empty", "<red", "text<", "<red><red>double</red>still", "<!bold>neg",
        "<bold>a<!bold>b</!bold>c", "<black>k<dark_blue>b<dark_green>g<dark_aqua>a<gold>g<blue>b<aqua>a",
    )

    @Test
    fun `lite matches MiniMessage on the supported subset`() {
        val real = MiniMessage.miniMessage()
        for (sample in samples) {
            assertEquals(runs(real.deserialize(sample)), runs(MiniMessageLite.deserialize(sample)), "input: $sample")
        }
    }

    @Test
    fun `gradient keeps the text and the first color`() {
        assertEquals(listOf(Run("go", 0xFF0000, emptySet())), runs(MiniMessageLite.deserialize("<gradient:#ff0000:#00ff00>go</gradient>")))
    }

    private fun components(mini: MiniText, warnings: MutableList<String>) = CachedComponents(
        Mockito.mock(PlaceholderService::class.java), ComponentCache(), mini, warnings::add,
    )

    private fun brokenFactory(): () -> MiniMessage = { throw NoSuchMethodError("Component.compact()") }

    @Test
    fun `an old Adventure switches to lite parsing and warns once`() {
        val warnings = ArrayList<String>()
        val components = components(MiniText(brokenFactory()), warnings)
        val first = components.deserialize("<red>hi", ComponentSerializerType.MINI_MESSAGE)
        components.deserialize("&aok", ComponentSerializerType.ADAPTIVE)
        components.serialize(first)
        assertEquals(listOf(Run("hi", 0xFF5555, emptySet())), runs(first))
        assertEquals(1, warnings.size)
    }

    @Test
    fun `an unexpected failure is not swallowed`() {
        val boom = IllegalStateException("boom")
        val components = components(MiniText({ throw boom }), ArrayList())
        val thrown = assertThrows(IllegalStateException::class.java) {
            components.deserialize("<red>x", ComponentSerializerType.MINI_MESSAGE)
        }
        assertSame(boom, thrown)
    }
}
