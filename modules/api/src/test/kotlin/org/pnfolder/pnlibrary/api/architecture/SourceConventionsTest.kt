package org.pnfolder.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Conventions every production source in the repository follows, checked on the source text so they hold for
 * all modules at once: explicit imports, one message style, and layers that never reach sideways or upwards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceConventionsTest {
    private val root: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private val productionSources: List<Path> by lazy {
        listOf("modules", "platforms", "examples").flatMap { group -> Files.walk(root.resolve(group)).use { paths ->
            paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".kt") || it.toString().endsWith(".java")) }
                .filter { path -> path.has("src/main") && BUILD_DIRECTORIES.none { path.has(it) } }
                .toList()
        } }
    }

    private val publicApiModules = Files.readAllLines(root.resolve("gradle/public-modules.properties"))
        .map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.substringBefore('=') + "/" }

    private val sources: List<Path> get() = productionSources.filter { it.toString().endsWith(".kt") }

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
        val offenders = productionSources.filter { it.has("modules/core/") }
            .mapNotNull { platformReferences(it, PlatformSourceReferences.packages.values.flatten()) }
        assertTrue(offenders.isEmpty(), "core references platform code in: $offenders")
    }

    @Test
    fun `platform runtimes never depend on each other`() {
        val offenders = PlatformSourceReferences.packages.flatMap { (platform, _) ->
            val forbidden = PlatformSourceReferences.packages.filterKeys { it != platform }.values.flatten()
            productionSources.filter { it.has("platforms/$platform/") }
                .mapNotNull { platformReferences(it, forbidden) }
        }
        assertTrue(offenders.isEmpty(), "platform runtimes reference each other in: $offenders")
    }

    @Test
    fun `public fluent builder methods declare their return type`() {
        val offenders = sources.filter { file -> publicApiModules.any { file.has(it) } }.flatMap { file ->
            val text = String(Files.readAllBytes(file), Charsets.UTF_8)
            INFERRED_APPLY.findAll(text)
                .filterNot { PRIVATE_MODIFIER.containsMatchIn(text.substring(text.lastIndexOf('\n', it.range.first) + 1, it.range.first)) }
                .map { "${relative(file)}: ${it.groupValues[1]}" }
                .toList()
        }
        assertTrue(offenders.isEmpty(), "Fluent methods without an explicit return type: $offenders")
    }

    @Test
    fun `public API modules hold contracts, not reflective platform code`() {
        // A self-contained feature module keeps its implementation in an `internal` package; that is not API.
        val offenders = sources.filter { file -> publicApiModules.any { file.has(it) } && !file.has("/internal/") }
            .filter { file -> lines(file).any { line -> REFLECTION.any { it in line } } }
        assertTrue(offenders.isEmpty(), "Reflection belongs in a runtime module, found in: ${relative(offenders)}")
    }

    private fun platformReferences(file: Path, forbidden: Collection<String>): String? {
        val references = PlatformSourceReferences.find(
            String(Files.readAllBytes(file), Charsets.UTF_8),
            javaSource = file.toString().endsWith(".java"),
            forbiddenPackages = forbidden,
        )
        return references.takeIf { it.isNotEmpty() }?.let { "${relative(file)}: $it" }
    }

    private fun Path.has(fragment: String) = toString().replace('\\', '/').contains(fragment)

    private fun lines(file: Path): List<String> = Files.readAllLines(file)

    private fun relative(file: Path): String = root.relativize(file).toString().replace('\\', '/')

    private fun relative(files: List<Path>): List<String> = files.map(::relative)

    private companion object {
        val BUILD_DIRECTORIES = listOf("/build/", "/.gradle/", "/out/")
        val INFERRED_APPLY = Regex("""fun\s+(?:<[^>]*>\s+)?(\w+)\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)\s*=\s*apply\b""")
        val PRIVATE_MODIFIER = Regex("""\b(?:private|internal)\b""")
        val REFLECTION = listOf("Class.forName(", ".getMethod(", ".getDeclaredField(", ".getDeclaredMethod(", ".invoke(")

        /**
         * Files whose job is the legacy text format itself: parsers and serializers, the redactor that strips codes
         * from reports, and KDoc describing accepted input. Example plugins are left out: they show plain Bukkit use.
         */
        val LEGACY_TEXT_ALLOWED = setOf(
            "modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api/text/ComponentService.kt",
            "modules/core/src/main/kotlin/org/pnfolder/pnlibrary/core/text/ComponentServiceImpl.kt",
            "modules/core/src/main/kotlin/org/pnfolder/pnlibrary/core/diagnostics/DiagnosticRedactor.kt",
            "modules/features/currency-api/src/main/kotlin/org/pnfolder/pnlibrary/api/currency/CurrencyRegistry.kt",
            "platforms/bukkit/runtime/src/main/kotlin/org/pnfolder/pnlibrary/bukkit/item/ItemText.kt",
        )
    }
}
