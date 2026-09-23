package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProductModelTest {
    @Test
    fun `product id is normalized`() {
        assertEquals("pnauth", ProductId.of(" PnAuth ").value)
    }

    @Test
    fun `product id rejects unsafe input`() {
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("../auth") }
    }

    @Test
    fun `update request derives no product identity from repository`() {
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnAuth")
            .apiVersions(1, 2)
            .artifactPattern("(?i)^pnauth-.*\\.jar$")
            .build()

        assertEquals("pnAuth", request.repositoryName)
        assertEquals(false, PluginUpdateRequest::class.java.methods.any { it.name == "getComponent" })
    }
}
