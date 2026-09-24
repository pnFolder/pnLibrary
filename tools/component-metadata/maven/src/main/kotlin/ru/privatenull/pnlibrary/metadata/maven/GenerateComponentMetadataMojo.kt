package ru.privatenull.pnlibrary.metadata.maven

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import ru.privatenull.pnlibrary.metadata.ComponentMetadata
import ru.privatenull.pnlibrary.metadata.ComponentMetadataWriter
import java.io.File

/** Maven goal that generates the canonical descriptor before JAR packaging. */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
class GenerateComponentMetadataMojo : AbstractMojo() {
    /** Maven project supplying default artifact identity and version. */
    @Parameter(defaultValue = "${'$'}{project}", readonly = true, required = true)
    lateinit var project: MavenProject

    /** Optional component ID override. */
    @Parameter(property = "pnComponent.id")
    var componentId: String? = null

    /** Optional component version override. */
    @Parameter(property = "pnComponent.version")
    var componentVersion: String? = null

    /** Oldest supported pnLibrary API generation. */
    @Parameter(property = "pnComponent.apiMinimum", defaultValue = "1", required = true)
    var apiMinimum: Int = 1

    /** Newest supported pnLibrary API generation, or the minimum when omitted. */
    @Parameter(property = "pnComponent.apiMaximum")
    var apiMaximum: Int? = null

    /** Compiled resource directory that receives the descriptor. */
    @Parameter(defaultValue = "${'$'}{project.build.outputDirectory}", required = true)
    lateinit var outputDirectory: File

    /** Validates configuration and writes the descriptor. */
    override fun execute() {
        try {
            val id = componentId?.trim().takeUnless { it.isNullOrEmpty() }
                ?: normalizeId(project.artifactId)
            val version = componentVersion?.trim().takeUnless { it.isNullOrEmpty() }
                ?: project.version
            val metadata = ComponentMetadata(id, version, apiMinimum, apiMaximum ?: apiMinimum)
            val output = ComponentMetadataWriter.write(outputDirectory.toPath(), metadata)
            log.info("Generated ${output.toAbsolutePath()}")
        } catch (error: Exception) {
            throw MojoExecutionException("Cannot generate pnLibrary component metadata: ${error.message}", error)
        }
    }

    private fun normalizeId(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
        .ifEmpty { throw IllegalArgumentException("cannot infer component id from Maven artifactId '$value'") }
}
