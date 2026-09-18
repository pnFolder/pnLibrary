package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion

class UpdateModelTest {
    @Test
    fun `channels express the maximum accepted risk`() {
        assertTrue(UpdateChannel.STABLE.accepts(UpdateChannel.STABLE))
        assertFalse(UpdateChannel.STABLE.accepts(UpdateChannel.BETA))
        assertTrue(UpdateChannel.BETA.accepts(UpdateChannel.STABLE))
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.BETA))
        assertTrue(UpdateChannel.DEV.accepts(UpdateChannel.DEV))
    }

    @Test
    fun `component identifiers are normalized and validated`() {
        assertEquals("pnmarket", ComponentId.of("PnMarket").value)
        assertThrows(IllegalArgumentException::class.java) { ComponentId.of("bad id") }
    }

    @Test
    fun `release validates API and dependency metadata`() {
        val dependency = ComponentDependency(
            ComponentId.of("pneconomy"),
            SemanticVersion.parse("3.0.0"),
        )
        val release = ComponentRelease(
            component = ComponentId.of("pnmarket"),
            version = SemanticVersion.parse("4.0.0"),
            channel = UpdateChannel.STABLE,
            supportedApi = ApiVersionRange(5, 5),
            dependencies = listOf(dependency),
        )

        assertEquals(5, release.supportedApi.minimum)
        assertEquals("pneconomy", release.dependencies.single().component.value)
    }
}
