package org.pnfolder.pnlibrary.core.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderInfo
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import org.pnfolder.pnlibrary.core.placeholders.usageVariants

class ActionsCommandTest {
    @Test
    fun `an action is written with every owner form its visibility allows`() {
        val shared = definitionUsage(ScenarioDefinitionInfo("action", "give-kit", "shop/main", "all")).map { it.text }

        assertEquals(
            listOf(
                "{ type: call, name: 'give-kit' }",
                "{ type: call, name: 'shop/main@give-kit' }",
                "{ type: call, name: 'shop@give-kit' }",
                "{ type: call, name: '~~@give-kit' }",
                "{ type: call, name: '~@give-kit' }",
            ),
            shared,
        )
        val own = definitionUsage(ScenarioDefinitionInfo("requirement", "vip", "shop/main", "module")).map { it.text }
        assertFalse(own.any { it.contains("shop@vip") || it.contains("'~@vip'") })
        assertTrue(definitionUsage(ScenarioDefinitionInfo("function", "kills", "shop/main", "all")).any { it.text == "~~@kills()" })
    }

    @Test
    fun `a placeholder shows its forms and hides the ones that cannot work`() {
        val open = usageVariants(PlaceholderInfo("shop.price", "", "shop/main", "Int", null, emptyList(), Visibility.ALL)).map { it.text }
        assertTrue("{shop/main@shop.price}" in open)
        assertTrue("{shop@shop.price}" in open)
        assertTrue("{~@shop.price}" in open)

        val hidden = usageVariants(PlaceholderInfo("shop.secret", "", "shop/main", "Int", null, emptyList(), Visibility.MODULE)).map { it.text }
        assertTrue("{~~@shop.secret}" in hidden)
        assertFalse("{shop@shop.secret}" in hidden)
        assertFalse("{~@shop.secret}" in hidden)
    }
}
