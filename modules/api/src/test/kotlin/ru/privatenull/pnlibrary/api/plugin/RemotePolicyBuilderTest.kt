package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class RemotePolicyBuilderTest {
    @Test
    fun `builder validates source interval and immutable values`() {
        val policy = RemotePolicy.builder()
            .source("https://example.org/policy.java")
            .checkEvery(Duration.ofHours(2))
            .onDeny(DenyAction.DISABLE_MODULE)
            .value("  environment  ", "production")
            .build()

        assertEquals("production", policy.values["environment"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (policy.values as MutableMap<String, String>)["changed"] = "true"
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemotePolicy.builder().source("https:///missing-host.java")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemotePolicy.builder().source("https://example.org/policy.java").checkEvery(Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemotePolicy.builder().source("https://example.org/policy.java").value(" ", "invalid")
        }
    }
}
