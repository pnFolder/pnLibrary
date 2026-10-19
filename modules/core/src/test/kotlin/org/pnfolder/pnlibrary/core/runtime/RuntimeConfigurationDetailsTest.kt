package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig

class RuntimeConfigurationDetailsTest {
    @Test
    fun `configuration details expose counts without sensitive values`() {
        val config = PnLibraryConfig(
            excludedPaths = listOf("secret.yml"),
            secretKeyPatterns = listOf("token"),
            redactValuePatterns = listOf("password"),
        )

        val details = RuntimeConfigurationDetails.of(config)

        assertEquals(1, details["excludedPathCount"])
        assertEquals(2, details["redactionRuleCount"])
        assertEquals(false, details.containsKey("token"))
        assertEquals(false, details.containsKey("password"))
    }
}
