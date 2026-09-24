package ru.privatenull.pnlibrary.metadata.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Gradle DSL used to describe the component embedded in the project JAR. */
abstract class PnComponentMetadataExtension @Inject constructor(objects: ObjectFactory) {
    /** Component ID; defaults to the normalized Gradle project name. */
    val id: Property<String> = objects.property(String::class.java)
    /** Component version; defaults to the Gradle project version. */
    val version: Property<String> = objects.property(String::class.java)
    /** Oldest supported pnLibrary API generation. */
    val apiMinimum: Property<Int> = objects.property(Int::class.java).convention(1)
    /** Newest supported pnLibrary API generation. */
    val apiMaximum: Property<Int> = objects.property(Int::class.java).convention(apiMinimum)

    /** Declares one supported pnLibrary API generation. */
    fun apiVersion(value: Int) {
        apiMinimum.set(value)
        apiMaximum.set(value)
    }

    /** Declares an inclusive range of supported pnLibrary API generations. */
    fun apiVersions(minimum: Int, maximum: Int) {
        apiMinimum.set(minimum)
        apiMaximum.set(maximum)
    }
}
