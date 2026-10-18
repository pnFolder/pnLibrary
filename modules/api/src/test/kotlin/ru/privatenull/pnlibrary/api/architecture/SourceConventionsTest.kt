package ru.privatenull.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Conventions every production source in the repository follows, checked on the source text so they hold for
 * all modules at once: explicit imports, one message style, and layers that never reach sideways or upwards.
 */
class SourceConventionsTest {
    private val root: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private val sources: List<Path> by lazy {
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { path -> path.has("src/main") && BUILD_DIRECTORIES.none { path.has(it) } }
                .toList()
        }
    }

    @Test
    fun `production code imports every name explicitly`() {
        val offenders = sources.filter { file -> lines(file).any { it.startsWith("import ") && it.trimEnd().endsWith(".*") } }
        assertTrue(offenders.isEmpty(), "Wildcard imports in: ${relative(offenders)}")
    }

    @Test
    fun `chat colors come from the palette, never from legacy section codes`() {
        val offenders = sources.filter { !it.has("examples/") }
            .filter { file -> relative(file) !in LEGACY_TEXT_ALLOWED && lines(file).any { '§' in it } }
        assertTrue(offenders.isEmpty(), "Legacy § color codes outside the allowed adapters in: ${relative(offenders)}")
    }

    @Test
    fun `the runtime core never depends on a platform`() {
        val offenders = sources.filter { it.has("modules/core/") }
            .filter { file -> lines(file).any { line -> PLATFORM_PACKAGES.any { line.startsWith("import $it.") } } }
        assertTrue(offenders.isEmpty(), "core imports platform code in: ${relative(offenders)}")
    }

    @Test
    fun `platform runtimes never depend on each other`() {
        val offenders = PLATFORM_PACKAGES.flatMap { own ->
            val platform = own.substringAfterLast('.')
            sources.filter { it.has("platforms/$platform/") }.filter { file ->
                lines(file).any { line -> PLATFORM_PACKAGES.any { other -> other != own && line.startsWith("import $other.") } }
            }
        }
        assertTrue(offenders.isEmpty(), "platform runtimes import each other in: ${relative(offenders)}")
    }

    private fun Path.has(fragment: String) = toString().replace('\\', '/').contains(fragment)

    private fun lines(file: Path): List<String> = Files.readAllLines(file)

    private fun relative(file: Path): String = root.relativize(file).toString().replace('\\', '/')

    private fun relative(files: List<Path>): List<String> = files.map(::relative)

    private companion object {
        val BUILD_DIRECTORIES = listOf("/build/", "/.gradle/", "/out/")
        val PLATFORM_PACKAGES = listOf("ru.privatenull.pnlibrary.bukkit", "ru.privatenull.pnlibrary.bungee", "ru.privatenull.pnlibrary.velocity")

        /**
         * Files whose job is the legacy text format itself: parsers and serializers, the redactor that strips codes
         * from reports, and KDoc describing accepted input. Example plugins are left out: they show plain Bukkit use.
         */
        val LEGACY_TEXT_ALLOWED = setOf(
            "modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/text/ComponentService.kt",
            "modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/text/ComponentServiceImpl.kt",
            "modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/DiagnosticRedactor.kt",
            "modules/features/currency-api/src/main/kotlin/ru/privatenull/pnlibrary/api/currency/CurrencyRegistry.kt",
            "platforms/bukkit/api/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/item/ItemText.kt",
        )
    }
}
