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

    class Account {
        var name: String = ""

        @field:ConfigSecret
        var pin: String = ""
    }

    class Holders {
        var users: List<Account> = emptyList()
        var byName: Map<String, Account> = emptyMap()
    }

    @Test
    fun `secrets inside list items and map values are found`() {
        val paths = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE).secretPaths(Holders::class.java)

        assertEquals(listOf("users.pin", "by-name.*.pin"), paths)
    }

    @Test
    fun `secret fields are found at any depth under their file keys`() {
        val paths = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE).secretPaths(Settings::class.java)

        assertEquals(listOf("database.password", "webhook-url"), paths)
    }
}
