package ru.privatenull.pnlibrary.update

import ru.privatenull.pnlibrary.api.updates.ComponentDescriptor
import java.nio.file.Path
import java.util.jar.JarFile

class EmbeddedDescriptorReader(private val codec: ComponentDescriptorCodec = ComponentDescriptorCodec()) {
    fun read(jar: Path): ComponentDescriptor = try {
        JarFile(jar.toFile()).use { archive ->
            val matches = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name.removePrefix("./") == ENTRY }
                .toList()
            if (matches.size != 1) throw ManifestException(ENTRY, "expected exactly one embedded descriptor")
            val entry = matches.single()
            if (entry.size > ComponentDescriptorCodec.MAX_MANIFEST_BYTES) {
                throw ManifestException(ENTRY, "embedded descriptor exceeds size limit")
            }
            val bytes = archive.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > ComponentDescriptorCodec.MAX_MANIFEST_BYTES) {
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
