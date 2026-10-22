package org.pnfolder.pnlibrary.core.updates.engine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The `.pnlibrary` folder of this repository follows the rules in `docs/repository.md`. */
class RepositoryLayoutTest {
    private val folder: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .map { it.resolve(".pnlibrary") }
        .firstOrNull(Files::isDirectory)
        ?: error("The .pnlibrary folder was not found above ${Paths.get("").toAbsolutePath()}")

    private fun catalog(name: String): ReleaseCatalog {
        val file = folder.resolve(name)
        assertTrue(Files.isRegularFile(file), "$name is missing from .pnlibrary")
        return ReleaseCatalogCodec().decode(Files.readAllBytes(file))
    }

    @Test
    fun `releases json and its example are valid catalogs of this product`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            assertEquals("pnlibrary", catalog(name).product, name)
        }
    }

    @Test
    fun `every listed version has a changelog file named after it`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            catalog(name).releases.forEach { release ->
                val changelog = folder.resolve("changelogs").resolve("${release.version}.md")
                assertTrue(Files.isRegularFile(changelog), "$name lists ${release.version} but ${changelog.fileName} is missing")
            }
        }
    }

    @Test
    fun `an installer block describes a universal jar with a checksum`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            val root = JsonParser.parseString(Files.readString(folder.resolve(name))).asJsonObject
            root.getAsJsonArray("releases").map { it.asJsonObject }.forEach { release ->
                val installer = release.getAsJsonObject("installer") ?: return@forEach
                val version = release.get("version").asString
                assertTrue(
                    installer.get("file").asString.matches(Regex("(?i)^pnLibrary-[0-9][^/\\\\]*-universal\\.jar\$")),
                    "$name: the installer file of $version must be a universal jar",
                )
                assertTrue(installer.get("url").asString.startsWith("https://"), "$name: the installer link of $version must be HTTPS")
                assertTrue(
                    installer.get("sha256").asString.matches(Regex("(?i)[0-9a-f]{64}")),
                    "$name: the installer sha256 of $version must have 64 hexadecimal characters",
                )
                installer.get("size")?.let { assertTrue(it.asLong > 0, "$name: the installer size of $version must be positive") }
            }
        }
    }

    @Test
    fun `a changelog link points at the file of its own version`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            catalog(name).releases.forEach { release ->
                val link = release.changelogUrl ?: return@forEach
                assertNotNull(link.path)
                assertTrue(
                    link.path.endsWith("/.pnlibrary/changelogs/${release.version}.md"),
                    "$name: the changelog link of ${release.version} is $link",
                )
            }
        }
    }
}
