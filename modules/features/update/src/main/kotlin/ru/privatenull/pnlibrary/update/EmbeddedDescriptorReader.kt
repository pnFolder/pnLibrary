package ru.privatenull.pnlibrary.update

import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.nio.file.Path
import java.util.jar.JarFile

class EmbeddedDescriptorReader(private val codec: ProductDescriptorCodec = ProductDescriptorCodec()) {
    fun read(jar: Path): ProductDescriptor = try {
        JarFile(jar.toFile()).use { archive ->
            val matches = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name.removePrefix("./") == ENTRY }
                .toList()
            if (matches.size != 1) throw ManifestException(ENTRY, "expected exactly one embedded descriptor")
            val entry = matches.single()
            if (entry.size > ProductDescriptorCodec.MAX_MANIFEST_BYTES) {
                throw ManifestException(ENTRY, "embedded descriptor exceeds size limit")
            }
            val bytes = archive.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > ProductDescriptorCodec.MAX_MANIFEST_BYTES) {
                        throw ManifestException(ENTRY, "embedded descriptor exceeds size limit")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            codec.decodeInstalled(bytes)
        }
    } catch (error: ManifestException) {
        throw error
    } catch (error: Exception) {
        throw ManifestException(ENTRY, "cannot read component JAR", error)
    }

    companion object {
        const val ENTRY = "META-INF/pnlibrary/component.json"
    }
}
