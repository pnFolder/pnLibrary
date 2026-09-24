package ru.privatenull.pnlibrary.metadata.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import ru.privatenull.pnlibrary.metadata.ComponentMetadata
import ru.privatenull.pnlibrary.metadata.ComponentMetadataWriter

/** Cacheable task that writes the canonical descriptor into a generated resources directory. */
@CacheableTask
abstract class GenerateComponentMetadata : DefaultTask() {
    /** Normalized component identifier. */
    @get:Input abstract val componentId: Property<String>
    /** Component version. */
    @get:Input abstract val componentVersion: Property<String>
    /** Oldest supported pnLibrary API generation. */
    @get:Input abstract val apiMinimum: Property<Int>
    /** Newest supported pnLibrary API generation. */
    @get:Input abstract val apiMaximum: Property<Int>
    /** Resource root that receives `META-INF/pnlibrary/component.json`. */
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    /** Validates the configured values and writes the descriptor. */
    @TaskAction
    fun generate() {
        ComponentMetadataWriter.write(
            outputDirectory.get().asFile.toPath(),
            ComponentMetadata(componentId.get(), componentVersion.get(), apiMinimum.get(), apiMaximum.get()),
        )
    }
}
