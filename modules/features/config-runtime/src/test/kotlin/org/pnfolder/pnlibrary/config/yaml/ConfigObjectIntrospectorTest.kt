package org.pnfolder.pnlibrary.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.BackupDatabase
import org.pnfolder.pnlibrary.api.config.BackupTable
import org.pnfolder.pnlibrary.api.config.ConfigIgnore
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigOrder
import org.pnfolder.pnlibrary.api.config.DatabaseField
import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole

class ConfigObjectIntrospectorTest {
    @Test
    fun `discovers inherited fields and applies filtering ordering and naming`() {
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE)

        val fields = introspector.fields(ChildConfig::class.java)

        assertEquals(listOf("base-value", "explicit", "child-value"), fields.map(introspector::key))
        assertEquals(listOf(1, 2, 3), fields.map { introspector.read(it, ChildConfig()) })
    }

    @Test
    fun `materializes standard collection interfaces predictably`() {
        val introspector = ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED)

        val set = introspector.collection(Set::class.java, listOf("a", "a", "b"), "values")
        val map = introspector.map(Map::class.java, linkedMapOf("a" to 1), "values")

        assertEquals(linkedSetOf("a", "b"), set)
        assertEquals(linkedMapOf("a" to 1), map)
    }

    private open class BaseConfig {
        @ConfigOrder(-10)
        var baseValue: Int = 1

        @ConfigIgnore
        var ignored: Int = 0

        @Transient
        var transientValue: Int = 0
    }

    private class ChildConfig : BaseConfig() {
        @ConfigOrder(-5)
        @ConfigKey("explicit")
        var renamed: Int = 2

        var childValue: Int = 3

        companion object {
            @JvmField
            val staticValue: Int = 0
        }
    }

    @BackupDatabase(DatabaseKind.MYSQL, "Игроки", tables = ["players"])
    @BackupDatabase(DatabaseKind.MYSQL, "Экономика")
    class ThreeInOne {
        @DatabaseField(DatabaseRole.USER)
        var user = ""

        @DatabaseField(DatabaseRole.DATABASE, database = "Игроки")
        var playersSchema = ""

        @DatabaseField(DatabaseRole.DATABASE, database = "Экономика")
        var economySchema = ""

        @BackupTable(database = "Экономика")
        var accounts = ""
    }

    @Test
    fun `one class can describe several databases and each field joins its own`() {
        val paths = ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED).databasePaths(ThreeInOne::class.java)

        assertEquals(listOf("Игроки", "Экономика"), paths.map { it.name })
        assertEquals(mapOf(DatabaseRole.USER to "user", DatabaseRole.DATABASE to "playersSchema"), paths[0].keys)
        assertEquals(mapOf(DatabaseRole.USER to "user", DatabaseRole.DATABASE to "economySchema"), paths[1].keys)
        assertEquals(emptyList<String>(), paths[0].tableKeys)
        assertEquals(listOf("accounts"), paths[1].tableKeys)
    }

    class OnTheFields {
        @BackupDatabase(DatabaseKind.MYSQL, "Игроки")
        var playersDb = ""

        @DatabaseField(DatabaseRole.HOST, database = "Игроки")
        var playersHost = ""

        @BackupDatabase(DatabaseKind.POSTGRESQL, "Кланы", tables = ["clans"])
        var clansDb = ""

        @DatabaseField(DatabaseRole.HOST, database = "Кланы")
        var clansHost = ""
    }

    @Test
    fun `a database can be described on its fields alone with no annotation on the class`() {
        val paths = ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED).databasePaths(OnTheFields::class.java)

        assertEquals(listOf("Игроки", "Кланы"), paths.map { it.name })
        assertEquals(listOf(DatabaseKind.MYSQL, DatabaseKind.POSTGRESQL), paths.map { it.kind })
        assertEquals(mapOf(DatabaseRole.HOST to "playersHost", DatabaseRole.DATABASE to "playersDb"), paths[0].keys)
        assertEquals(mapOf(DatabaseRole.HOST to "clansHost", DatabaseRole.DATABASE to "clansDb"), paths[1].keys)
        assertEquals("", paths[0].path)
    }
}
