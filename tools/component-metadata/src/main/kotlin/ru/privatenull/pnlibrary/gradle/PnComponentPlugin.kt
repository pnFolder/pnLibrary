package ru.privatenull.pnlibrary.gradle

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class PnComponentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("pnComponent", PnComponentExtension::class.java)
        val generate = project.tasks.register("generatePnComponentMetadata") { task ->
            task.group = "build"
            task.description = "Generates reproducible pnLibrary component manifests"
            task.inputs.property("id", extension.id)
            task.inputs.property("componentVersion", extension.componentVersion)
            task.inputs.property("apiMinimum", extension.apiMinimum)
            task.inputs.property("apiMaximum", extension.apiMaximum)
            task.inputs.property("channel", extension.channel)
            task.inputs.property("managedDependencies", extension.managedDependencies)
            task.inputs.property("artifacts", extension.artifacts)
            val resourceDirectory = project.layout.buildDirectory.dir("generated/pnComponent/resources")
            val releaseDirectory = project.layout.buildDirectory.dir("generated/pnComponent/release")
            task.outputs.dir(resourceDirectory)
            task.outputs.dir(releaseDirectory)
            task.doLast {
                val id = extension.id.orNull?.trim().orEmpty()
                val version = extension.componentVersion.orNull?.trim().orEmpty()
                require(id.matches(Regex("[a-z0-9][a-z0-9_.-]*"))) { "pnComponent.id is required and must be normalized" }
                require(version.isNotEmpty()) { "pnComponent.componentVersion is required" }
                val dependencies = extension.managedDependencies.get().map { it.split('|') }
                require(dependencies.map { it[0] }.distinct().size == dependencies.size) { "duplicate managed dependency" }
                val installed = JsonObject().apply {
                    addProperty("schema", 1); addProperty("component", id); addProperty("version", version)
                    add("pnLibraryApi", api(extension))
                    add("managedDependencies", JsonArray().apply { dependencies.sortedBy { it[0] }.forEach { value -> add(JsonObject().apply {
                        addProperty("component", value[0]); addProperty("minimumVersion", value[1])
                        addProperty("repositoryOwner", value[2]); addProperty("repositoryName", value[3])
                    }) } })
                }
                val artifacts = extension.artifacts.get().map { it.split('|') }
                val release = JsonObject().apply {
                    addProperty("schema", 1); addProperty("component", id); addProperty("version", version)
                    addProperty("channel", extension.channel.get().lowercase()); add("pnLibraryApi", api(extension))
                    add("dependencies", JsonArray().apply { dependencies.sortedBy { it[0] }.forEach { value -> add(JsonObject().apply {
                        addProperty("component", value[0]); addProperty("minimumVersion", value[1])
                    }) } })
                    add("artifacts", JsonArray().apply { artifacts.sortedBy { it[0] }.forEach { value -> add(JsonObject().apply {
                        addProperty("file", value[0]); addProperty("platform", value[1].lowercase())
                        add("java", JsonObject().apply { addProperty("minimum", value[2].toInt()); if (value[3].isEmpty()) add("maximum", null) else addProperty("maximum", value[3].toInt()) })
                        addProperty("size", value[4].toLong()); addProperty("sha256", value[5].lowercase())
                    }) } })
                }
                val gson = GsonBuilder().disableHtmlEscaping().create()
                val embedded = resourceDirectory.get().file("META-INF/pnlibrary/component.json").asFile.toPath()
                val published = releaseDirectory.get().file("pn-update.json").asFile.toPath()
                Files.createDirectories(embedded.parent); Files.createDirectories(published.parent)
                Files.write(embedded, gson.toJson(installed).toByteArray(StandardCharsets.UTF_8))
                Files.write(published, gson.toJson(release).toByteArray(StandardCharsets.UTF_8))
                Files.write(releaseDirectory.get().file("checksums.sha256").asFile.toPath(),
                    artifacts.sortedBy { it[0] }.joinToString("\n", postfix = if (artifacts.isEmpty()) "" else "\n") { "${it[5].lowercase()}  ${it[0]}" }.toByteArray(StandardCharsets.UTF_8))
            }
        }
        project.pluginManager.withPlugin("java") {
            project.extensions.getByType(SourceSetContainer::class.java).named("main") { sourceSet ->
                sourceSet.resources.srcDir(project.layout.buildDirectory.dir("generated/pnComponent/resources"))
            }
            project.tasks.named("processResources").configure { it.dependsOn(generate) }
        }
        project.tasks.matching { it.name == "assemble" }.configureEach { it.dependsOn(generate) }
    }

    private fun api(extension: PnComponentExtension) = JsonObject().apply {
        addProperty("minimum", extension.apiMinimum.get()); addProperty("maximum", extension.apiMaximum.get())
    }
}
