package ru.privatenull.pnlibrary.metadata.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

class PnComponentMetadataPluginTest {
    @TempDir lateinit var projectDirectory: Path

    @Test
    fun `Kotlin DSL embeds descriptor in jar`() {
        write("settings.gradle.kts", "rootProject.name = \"demo-kts\"\n")
        write(
            "build.gradle.kts",
            """
            plugins {
                java
                id("ru.privatenull.pnlibrary.component-metadata")
            }
            version = "2.4.0"
            pnComponentMetadata {
                id.set("pncases")
                apiVersions(1, 2)
            }
            """.trimIndent(),
        )
        assertJarMetadata("pncases", 2)
    }

    @Test
    fun `Groovy DSL embeds descriptor in jar`() {
        write("settings.gradle", "rootProject.name = 'demo-groovy'\n")
        write(
            "build.gradle",
            """
            plugins {
                id 'java'
                id 'ru.privatenull.pnlibrary.component-metadata'
            }
            version = '3.0.0'
            pnComponentMetadata {
                id = 'pneconomy'
                apiVersion(1)
            }
            """.trimIndent(),
        )
        assertJarMetadata("pneconomy", 1)
    }

    private fun assertJarMetadata(id: String, maximumApi: Int) {
        GradleRunner.create().withProjectDir(projectDirectory.toFile())
            .withArguments("jar", "--stacktrace").withPluginClasspath().build()
        val jar = Files.list(projectDirectory.resolve("build/libs")).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".jar") }.findFirst().orElseThrow()
        }
        JarFile(jar.toFile()).use { archive ->
            val entry = archive.getJarEntry("META-INF/pnlibrary/component.json")
            assertTrue(entry != null)
            val json = archive.getInputStream(entry).bufferedReader().readText()
            assertTrue(json.contains("\"component\": \"$id\""))
            assertTrue(json.contains("\"maximum\": $maximumApi"))
        }
    }

    private fun write(relative: String, content: String) {
        val path = projectDirectory.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
