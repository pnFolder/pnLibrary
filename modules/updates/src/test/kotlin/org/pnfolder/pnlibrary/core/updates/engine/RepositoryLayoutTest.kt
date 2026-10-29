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

    /** The real catalog of pnLibrary and the filled example for plugin authors in `integration/template/.pnlibrary`. */
    private class Place(val file: Path, val product: String, val linkPrefix: String)

    private val places: Map<String, Place> = mapOf(
        "releases.json" to Place(folder.resolve("releases.json"), "pnlibrary", "/.pnlibrary/changelogs/"),
        "template releases.json" to Place(
            folder.resolveSibling("integration").resolve("template").resolve(".pnlibrary").resolve("releases.json"),
            "myplugin",
            "/.pnlibrary/changelogs/",
        ),
    )

    private fun changelogs(name: String): Path = places.getValue(name).file.resolveSibling("changelogs")

    private fun catalog(name: String): ReleaseCatalog {
        val file = places.getValue(name).file
        assertTrue(Files.isRegularFile(file), "$name is missing")
        return ReleaseCatalogCodec().decode(Files.readAllBytes(file))
    }

    @Test
    fun `releases json and the template example are valid catalogs of their product`() {
        places.forEach { (name, place) -> assertEquals(place.product, catalog(name).product, name) }
    }

    @Test
    fun `every listed version has a changelog file named after it`() {
        places.keys.forEach { name ->
            catalog(name).releases.forEach { release ->
                val changelog = changelogs(name).resolve("${release.version}.md")
                assertTrue(Files.isRegularFile(changelog), "$name lists ${release.version} but ${changelog.fileName} is missing")
            }
        }
    }

    @Test
    fun `every universal artifact names a universal jar and carries a checksum`() {
        catalog("releases.json").releases.forEach { release ->
            release.artifacts.filter { it.platform == PlatformType.UNIVERSAL }.forEach { artifact ->
                assertTrue(
                    artifact.file.matches(Regex("(?i)^pnLibrary-[0-9][^/\\\\]*-universal\\.jar\$")),
                    "the universal artifact of ${release.version} must be named a universal jar",
                )
                assertNotNull(artifact.sha256, "the universal artifact of ${release.version} has no sha256")
            }
        }
    }

    @Test
    fun `a changelog link points at the file of its own version`() {
        places.forEach { (name, place) ->
            catalog(name).releases.forEach { release ->
                val link = release.changelogUrl ?: return@forEach
                assertNotNull(link.path)
                assertTrue(link.path.endsWith("${place.linkPrefix}${release.version}.md"), "$name: the changelog link of ${release.version} is $link")
            }
        }
    }

    @Test
    fun `the channel of a release matches the word in its version`() {
        places.keys.forEach { name ->
            catalog(name).releases.forEach { release ->
                val word = release.version.prerelease.firstOrNull()
                assertEquals(release.channel.name.lowercase(), word, "$name: ${release.version} is listed in channel ${release.channel}")
            }
        }
    }
}
