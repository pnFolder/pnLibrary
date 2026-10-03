package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion

class ReleaseChannelSelectorTest {
    private val product = ProductId.of("example")

    @Test
    fun `uses configured channel instead of registration default`() {
        val releases = listOf(
            release("2.0.0", UpdateChannel.STABLE),
            release("3.0.0-dev.1", UpdateChannel.DEV),
        )

        val stable = ReleaseChannelSelector.select(product, releases, UpdateChannel.STABLE)
        val development = ReleaseChannelSelector.select(product, releases, UpdateChannel.DEV)

        assertEquals("2.0.0", stable.latestAllowed?.version.toString())
        assertEquals("3.0.0-dev.1", development.latestAllowed?.version.toString())
        assertEquals(releases, development.all)
    }

    private fun release(version: String, channel: UpdateChannel): ProductRelease = ProductRelease(
        product = product,
        version = SemanticVersion.parse(version),
        channel = channel,
        supportedApi = ApiVersionRange(1, 1),
        providesApi = null,
        dependencies = emptyList(),
        repository = "owner/example",
        artifacts = listOf(
            ArtifactDescriptor(
                file = "example-$version.jar",
                platform = PlatformType.BUKKIT,
                minimumJava = 8,
                maximumJava = null,
                size = null,
                sha256 = null,
                downloadUri = null,
            ),
        ),
    )
}
