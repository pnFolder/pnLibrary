package org.pnfolder.pnlibrary.core.updates.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType
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
    fun `every universal artifact names a universal jar and carries a checksum`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            catalog(name).releases.forEach { release ->
                release.artifacts.filter { it.platform == PlatformType.UNIVERSAL }.forEach { artifact ->
                    assertTrue(
                        artifact.file.matches(Regex("(?i)^pnLibrary-[0-9][^/\\\\]*-universal\\.jar\$")),
                        "$name: the universal artifact of ${release.version} must be named a universal jar",
                    )
                    assertNotNull(artifact.sha256, "$name: the universal artifact of ${release.version} has no sha256")
                }
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

    @Test
    fun `the channel of a release matches the word in its version`() {
        listOf("releases.json", "releases.example.json").forEach { name ->
            catalog(name).releases.forEach { release ->
                val word = release.version.prerelease.firstOrNull()
                assertEquals(release.channel.name.lowercase(), word, "$name: ${release.version} is listed in channel ${release.channel}")
            }
        }
    }
}
