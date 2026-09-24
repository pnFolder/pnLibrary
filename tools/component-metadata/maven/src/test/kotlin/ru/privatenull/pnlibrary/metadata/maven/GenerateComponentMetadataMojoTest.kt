package ru.privatenull.pnlibrary.metadata.maven

import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GenerateComponentMetadataMojoTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `generates descriptor using Maven project defaults`() {
        val mojo = GenerateComponentMetadataMojo().apply {
            project = MavenProject().also {
                it.artifactId = "pnCases"
                it.version = "2.4.0"
            }
            outputDirectory = temporary.toFile()
            apiMinimum = 1
            apiMaximum = 2
        }
        mojo.execute()
        val json = temporary.resolve("META-INF/pnlibrary/component.json").toFile().readText()
        assertTrue(json.contains("\"component\": \"pncases\""))
        assertTrue(json.contains("\"version\": \"2.4.0\""))
        assertTrue(json.contains("\"maximum\": 2"))
    }
}
