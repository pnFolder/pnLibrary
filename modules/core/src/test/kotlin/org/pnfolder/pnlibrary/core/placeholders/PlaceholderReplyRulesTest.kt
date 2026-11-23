package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.placeholders.ArgumentAdapter
import org.pnfolder.pnlibrary.api.placeholders.ArgumentAdapters
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Placeholders

@Placeholders("plain")
class PlainValues {
    @Placeholder
    fun level(): Int = 12
}

@Placeholders("plainprop")
class PlainProperty {
    @Placeholder
    val tag: String = "WLF"
}

class PlaceholderReplyRulesTest {
    private val engine = PlaceholderEngine { }

    @Test
    fun `a method that does not return Reply is refused`() {
        val failure = assertThrows<IllegalArgumentException> { engine.scope("test/plain").register(PlainValues()) }
        assertTrue("must return Reply" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun `a property that is not a Reply is refused`() {
        assertThrows<IllegalArgumentException> { engine.scope("test/plain").register(PlainProperty()) }
    }

    @Test
    fun `every example class of the api is a valid placeholder class`() {
        val holders = listOf(
            "ClanBasics", "ClanArguments", "ClanBank", "AboutTheViewer", "ServerValues", "MeValues", "DuelValues", "RouteTour",
            "ClanWarTree", "ClanConverterValues", "ClanReplies",
        )
        // The examples register their own types in `setupPlaceholders`; here any reading will do.
        @Suppress("UNCHECKED_CAST")
        val clan = Class.forName("org.pnfolder.pnlibrary.api.placeholders.examples.Clan") as Class<Any>
        ArgumentAdapters.register(clan, ArgumentAdapter("clan") { null })
        holders.forEach { name ->
            val type = Class.forName("org.pnfolder.pnlibrary.api.placeholders.examples.$name")
            val constructor = type.declaredConstructors.first { it.parameterCount == 0 }.apply { isAccessible = true }
            Scanner().scan(listOf(constructor.newInstance()))
        }
    }
}
