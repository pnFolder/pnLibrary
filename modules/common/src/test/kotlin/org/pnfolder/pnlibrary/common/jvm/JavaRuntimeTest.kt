package org.pnfolder.pnlibrary.common.jvm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaRuntimeTest {
    @Test
    fun `parses legacy and modern specification versions`() {
        for ((value, expected) in mapOf("1.8" to 8, "17" to 17, "21.0.1" to 21, "25-ea" to 25, "unknown" to 8, null to 8)) {
            assertEquals(expected, JavaRuntime.parseFeature(value))
        }
    }
}
