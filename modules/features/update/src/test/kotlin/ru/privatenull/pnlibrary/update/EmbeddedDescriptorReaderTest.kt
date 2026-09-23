package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.nio.file.Files

class EmbeddedDescriptorReaderTest {
    @TempDir lateinit var directory: Path

    @Test fun `reads the generated installed descriptor from jar`() {
        val bytes = ProductDescriptorCodec().encodeInstalled(
            ProductDescriptor.builder("economy", "3.4.0").pnLibraryApi(1, 2)
                .managedDependency("permissions", "2.1.0", "pnFolder", "Permissions").build(),
        )
        val jar = directory.resolve("economy.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry(EmbeddedDescriptorReader.ENTRY))
            output.write(bytes)
            output.closeEntry()
        }
        val descriptor = EmbeddedDescriptorReader().read(jar)
        assertEquals("economy", descriptor.id.value)
        assertEquals("permissions", descriptor.managedProductDependencies.single().product.value)
    }

    @Test fun `rejects duplicate embedded descriptors`() {
        val jar = directory.resolve("duplicate.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            repeat(2) { index ->
                output.putNextEntry(JarEntry(if (index == 0) EmbeddedDescriptorReader.ENTRY else "./${EmbeddedDescriptorReader.ENTRY}"))
                output.write("{}".toByteArray())
                output.closeEntry()
            }
        }
        assertThrows(ManifestException::class.java) { EmbeddedDescriptorReader().read(jar) }
    }
}
