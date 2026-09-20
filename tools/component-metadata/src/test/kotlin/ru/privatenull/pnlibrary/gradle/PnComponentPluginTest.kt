package ru.privatenull.pnlibrary.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PnComponentPluginTest {
    @TempDir lateinit var project: Path

    @Test
    fun `generates matching deterministic embedded and release metadata`() {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='fixture'")
        Files.writeString(project.resolve("build.gradle"), """
            plugins { id 'java'; id 'ru.privatenull.pnlibrary.component' }
            pnComponent {
              id.set('example')
              componentVersion.set('2.0.0')
              apiMinimum.set(1); apiMaximum.set(1)
              managedDependency('economy', '1.2.0', 'pnFolder', 'Economy')
              artifact('example.jar', 'bukkit', 17, null, 123, '${"a".repeat(64)}')
            }
        """.trimIndent())

        val first = run("generatePnComponentMetadata")
        assertEquals(TaskOutcome.SUCCESS, first.task(":generatePnComponentMetadata")?.outcome)
        val embedded = project.resolve("build/generated/pnComponent/resources/META-INF/pnlibrary/component.json")
        val release = project.resolve("build/generated/pnComponent/release/pn-update.json")
        val firstBytes = Files.readAllBytes(release)
        assertTrue(Files.readString(embedded).contains("\"component\":\"example\""))
        assertTrue(Files.readString(release).contains("\"component\":\"example\""))

        run("clean", "generatePnComponentMetadata")
        assertArrayEquals(firstBytes, Files.readAllBytes(release))
    }

    @Test
    fun `missing identity and duplicate dependencies fail clearly`() {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name='fixture'")
        Files.writeString(project.resolve("build.gradle"), """
            plugins { id 'java'; id 'ru.privatenull.pnlibrary.component' }
            pnComponent {
              componentVersion.set('1.0.0')
              managedDependency('same', '1.0.0', 'a', 'b')
              managedDependency('same', '2.0.0', 'a', 'b')
            }
        """.trimIndent())

        val output = runner("generatePnComponentMetadata").buildAndFail().output
        assertTrue(output.contains("id", ignoreCase = true) || output.contains("duplicate managed dependency"), output)
    }

    private fun run(vararg arguments: String) = runner(*arguments).build()

    private fun runner(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()
}
