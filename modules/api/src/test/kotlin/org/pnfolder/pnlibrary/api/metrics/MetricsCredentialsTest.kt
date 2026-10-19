package org.pnfolder.pnlibrary.api.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MetricsCredentialsTest {

    @Test
    fun `generic access preserves provider value types`() {
        val numeric: MetricsCredentials<Int> = MetricsCredentials.BStats(42)
        val text: MetricsCredentials<CharArray> = MetricsCredentials.FastStats("00123".toCharArray())

        assertEquals(43, numeric.useValue { it + 1 })
        assertEquals("00123", text.useValue { String(it) })
        text.close()
    }

    @Test
    fun `token copies input and preserves leading zeros`() {
        val input = "00123".toCharArray()
        val credentials = MetricsCredentials.FastStats(input)
        input.fill('x')
        assertEquals("00123", credentials.useToken { it })
        assertFalse(credentials.toString().contains("00123"))
    }

    @Test
    fun `closed token cannot be reused`() {
        val credentials = MetricsCredentials.FastStats("secret".toCharArray())
        credentials.close()
        credentials.close()
        assertThrows(IllegalStateException::class.java) {
            credentials.useToken { it }
        }
    }

    @Test
    fun `legacy configuration text redacts its token`() {
        val configuration = MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = "00123")
        assertFalse(configuration.toString().contains("00123"))
        val credentials = configuration.credentials() as MetricsCredentials.FastStats
        assertEquals("00123", credentials.useToken { it })
        credentials.close()
    }

    @Test
    fun `credentials reject invalid identifiers`() {
        assertThrows(IllegalArgumentException::class.java) { MetricsCredentials.BStats(0) }
        assertThrows(IllegalArgumentException::class.java) { MetricsCredentials.FastStats(charArrayOf(' ')) }
    }
}
