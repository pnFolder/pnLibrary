package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.lang.reflect.Proxy

class ModuleMetadataFactoryTest {
    @Test
    fun `uses explicit metadata before native platform values`() {
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, _ ->
            when (method.name) {
                "getType" -> PlatformType.BUKKIT
                "getImplementationName" -> "Paper"
                "ownerDetails" -> mapOf("name" to "Native", "version" to "1.0", "authors" to "Native author")
                "details" -> emptyMap<String, Any?>()
                else -> null
            }
        } as PlatformAdapter
        val definition = ModuleDefinitionBuilder().apply {
            metadataName = "Explicit"
            metadataVersion = "2.0"
            metadataAuthors = "pnFolder"
        }

        val metadata = ModuleMetadataFactory(platform).create(Any(), ModuleId.of("demo"), definition)

        assertEquals("Explicit", metadata.name)
        assertEquals("2.0", metadata.version)
        assertEquals("pnFolder", metadata.authors)
        assertEquals("Paper", metadata.platformImplementation)
    }
}
