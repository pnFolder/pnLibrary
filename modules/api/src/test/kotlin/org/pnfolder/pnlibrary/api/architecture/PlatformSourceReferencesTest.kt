package org.pnfolder.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformSourceReferencesTest {
    @Test
    fun `imports from native and library platform namespaces are detected in both languages`() {
        for (namespace in PlatformSourceReferences.packages.values.flatten()) {
            for (javaSource in listOf(false, true)) {
                assertEquals(
                    setOf("$namespace.Example"),
                    PlatformSourceReferences.find("import $namespace.Example;", javaSource),
                )
            }
        }
    }

    @Test
    fun `fully qualified calls cannot bypass import checks`() {
        for (javaSource in listOf(false, true)) {
            assertEquals(
                setOf("org.bukkit.Bukkit.getServer"),
                PlatformSourceReferences.find("org.bukkit.Bukkit.getServer()", javaSource),
            )
        }
    }

    @Test
    fun `java static imports and kotlin aliases and wildcard imports are detected`() {
        assertEquals(setOf("org.bukkit.Bukkit.getServer"), PlatformSourceReferences.find(
            "import static org.bukkit.Bukkit.getServer;", javaSource = true,
        ))
        assertEquals(setOf("org.bukkit.Bukkit"), PlatformSourceReferences.find(
            "import org.bukkit.Bukkit as NativeServer", javaSource = false,
        ))
        assertEquals(setOf("org.bukkit"), PlatformSourceReferences.find(
            "import org.bukkit.*", javaSource = false,
        ))
    }

    @Test
    fun `comments and diagnostic string literals are not dependencies`() {
        val source = """
            // org.bukkit.Bukkit.getServer()
            /* com.velocitypowered.api.proxy.ProxyServer */
            /** @see net.md_5.bungee.api.ProxyServer */
            "org.bukkit.Bukkit"
            "com.velocitypowered.api.proxy.ProxyServer"
            "${'$'}{net.md_5.bungee.api.ProxyServer}"
        """.trimIndent()
        assertTrue(PlatformSourceReferences.find(source, javaSource = true).isEmpty())
        assertTrue(PlatformSourceReferences.find(source.substringBefore("\"${'$'}{"), javaSource = false).isEmpty())
    }

    @Test
    fun `kotlin raw strings and nested comments are ignored but template expressions are checked`() {
        val rawString = "\"\"\"org.bukkit.Bukkit\"\"\""
        val comment = "/* outer /* nested */ org.bukkit.Bukkit */"
        assertTrue(PlatformSourceReferences.find("$rawString\n$comment", javaSource = false).isEmpty())
        assertEquals(setOf("org.bukkit.Bukkit.getServer"), PlatformSourceReferences.find(
            "\"server: ${'$'}{org.bukkit.Bukkit.getServer()}\"", javaSource = false,
        ))
    }

    @Test
    fun `whitespace comments and escaped identifiers do not hide qualified references`() {
        assertEquals(setOf("org.bukkit.Bukkit"), PlatformSourceReferences.find(
            "org /* comment */ . bukkit\n. Bukkit", javaSource = true,
        ))
        assertEquals(setOf("org.bukkit.Bukkit"), PlatformSourceReferences.find(
            "`org` . `bukkit` . Bukkit", javaSource = false,
        ))
    }

    @Test
    fun `similar package names are allowed and a platform can keep its own references`() {
        assertTrue(PlatformSourceReferences.find(
            "org.bukkitish.Example; example.org.bukkit.Example", javaSource = true,
        ).isEmpty())
        val otherPlatforms = PlatformSourceReferences.packages.filterKeys { it != "bukkit" }.values.flatten()
        assertEquals(setOf("com.velocitypowered.api.proxy.ProxyServer"), PlatformSourceReferences.find(
            "org.bukkit.Bukkit; com.velocitypowered.api.proxy.ProxyServer", javaSource = true,
            forbiddenPackages = otherPlatforms,
        ))
    }
}
