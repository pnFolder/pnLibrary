package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigNewLine
import org.pnfolder.pnlibrary.api.config.ConfigNotBlank
import org.pnfolder.pnlibrary.api.config.ConfigPattern
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigRequired

class ConfigSchemaInspectorTest {
    private val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE)
    private val inspector = ConfigSchemaInspector(introspector, ConfigSerializerResolver(emptyMap()))

    @Test
    fun `finds required paths inside nested configuration objects`() {
        assertEquals(setOf("name", "connection.host"), inspector.requiredPaths(RootConfig::class.java))
    }

    @Test
    fun `reports validation failures with complete yaml paths`() {
        val invalid = RootConfig().apply {
            name = " "
            connection.port = 70_000
            connection.host = "INVALID HOST"
        }

        val problems = inspector.validate(invalid, RootConfig::class.java)

        assertEquals(
            listOf("name", "connection.host", "connection.port"),
            problems.map { it.path },
        )
    }

    @Test
    fun `builds comments and layout metadata for nested fields`() {
        val metadata = inspector.metadata(RootConfig::class.java, RootConfig())

        assertEquals(listOf("Connection settings."), metadata.getValue("connection").comments)
        assertTrue(metadata.getValue("connection").separateWithBlankLine)
        assertTrue("host" in metadata.getValue("connection").children)
    }

    private class RootConfig {
        @ConfigRequired
        @ConfigNotBlank
        var name: String = "server"

        @ConfigComment("Connection settings.")
        @ConfigNewLine
        var connection: ConnectionConfig = ConnectionConfig()
    }

    private class ConnectionConfig {
        @ConfigRequired
        @ConfigPattern("[a-z.]+")
        var host: String = "localhost"

        @ConfigRange(min = 1.0, max = 65_535.0)
        var port: Int = 25565
    }
}
