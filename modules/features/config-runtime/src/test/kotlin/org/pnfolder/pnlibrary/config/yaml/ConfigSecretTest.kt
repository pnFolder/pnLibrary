package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigSecret

class ConfigSecretTest {
    class Database {
        var host: String = "localhost"

        @field:ConfigSecret
        var password: String = ""
    }

    class Settings {
        var database: Database = Database()

        @field:ConfigSecret
        var webhookUrl: String = ""

        var welcome: String = "Hi"
    }

    @Test
    fun `secret fields are found at any depth under their file keys`() {
        val paths = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE).secretPaths(Settings::class.java)

        assertEquals(listOf("database.password", "webhook-url"), paths)
    }
}
