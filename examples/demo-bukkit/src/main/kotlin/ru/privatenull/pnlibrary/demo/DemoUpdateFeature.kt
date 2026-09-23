package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.update.ArtifactDownloader
import ru.privatenull.pnlibrary.update.ProductDescriptorCodec
import ru.privatenull.pnlibrary.update.FreezeDuration
import ru.privatenull.pnlibrary.update.FreezeStore
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.Duration

/** Direct showcase of the optional update feature module (kept separate from PluginRegistration). */
object DemoUpdateFeature {
    fun smoke(plugin: DemoPlugin) {
        val freeze = FreezeStore(plugin.dataFolder.toPath().resolve("demo-freezes.json"))
        val id = ru.privatenull.pnlibrary.api.updates.ProductId.of("pndemo")
        freeze.freeze(id, FreezeDuration.parse("1h"))
        freeze.remaining(id); freeze.isFrozen(id); freeze.active(); freeze.clear(id)

        val descriptor = ProductDescriptor.builder("pndemo", plugin.description.version)
            .pnLibraryApi(1, 1).build()
        val codec = ProductDescriptorCodec()
        val encoded = codec.encodeInstalled(descriptor)
        codec.decodeInstalled(encoded)

        val output = plugin.dataFolder.toPath().resolve("update-feature-smoke.bin")
        ArtifactDownloader(1024).download({ ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, output)
        Files.deleteIfExists(output)
        plugin.logger.info("update feature smoke: freeze, codec and bounded downloader are available")
    }
}
