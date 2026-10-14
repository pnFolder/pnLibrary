package ru.privatenull.pnlibrary.api.metrics

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.security.SecretValue

class SecretValueTest {

    @Test
    fun `temporary secret is erased after use without changing owned value`() {
        val secret = SecretValue("password".toCharArray())
        lateinit var borrowed: CharArray
        secret.useSecret { value ->
            borrowed = value
            assertEquals("password", String(value))
            value[0] = 'x'
        }
        assertArrayEquals(CharArray(8), borrowed)
        assertEquals("password", secret.useSecret { String(it) })
        secret.close()
    }

    @Test
    fun `temporary secret is erased when callback throws`() {
        val secret = SecretValue("token".toCharArray())
        lateinit var borrowed: CharArray
        assertThrows(IllegalStateException::class.java) {
            secret.useSecret<Unit> {
                borrowed = it
                error("consumer failed")
            }
        }
        assertArrayEquals(CharArray(5), borrowed)
        secret.close()
        assertThrows(IllegalStateException::class.java) { secret.useSecret { it.size } }
    }
}
