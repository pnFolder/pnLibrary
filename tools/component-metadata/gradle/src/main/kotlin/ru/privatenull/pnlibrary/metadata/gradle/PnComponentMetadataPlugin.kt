package ru.privatenull.pnlibrary.metadata.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/** Wires generated component metadata into the Java main resource set. */
class PnComponentMetadataPlugin : Plugin<Project> {
    /** Installs the DSL and generation task on [project]. */
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "pnComponentMetadata",
            PnComponentMetadataExtension::class.java,
        ).apply {
            id.convention(project.provider { normalizeId(project.name) })
            version.convention(project.provider { project.version.toString() })
        }
        val generated = project.layout.buildDirectory.dir("generated/pnComponentMetadata/resources")
        val task = project.tasks.register("generatePnComponentMetadata", GenerateComponentMetadata::class.java) { generate ->
            generate.group = "build"
            generate.description = "Generates ${ru.privatenull.pnlibrary.metadata.ComponentMetadataWriter.ENTRY}"
            generate.componentId.set(extension.id)
            generate.componentVersion.set(extension.version)
            generate.apiMinimum.set(extension.apiMinimum)
            generate.apiMaximum.set(extension.apiMaximum)
            generate.outputDirectory.set(generated)
        }

        project.pluginManager.withPlugin("java") {
            project.extensions.getByType(SourceSetContainer::class.java)
                .named("main") { it.resources.srcDir(generated) }
            project.tasks.named("processResources").configure { process -> process.dependsOn(task) }
        }
    }

    private fun normalizeId(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '.', '_')
        .ifEmpty { throw IllegalArgumentException("cannot infer component id from project name '$value'") }
}
