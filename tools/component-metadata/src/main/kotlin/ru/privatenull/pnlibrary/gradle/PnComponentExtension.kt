package ru.privatenull.pnlibrary.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class PnComponentExtension @Inject constructor(objects: ObjectFactory) {
    val id: Property<String> = objects.property(String::class.java).convention("")
    val componentVersion: Property<String> = objects.property(String::class.java).convention("")
    val apiMinimum: Property<Int> = objects.property(Int::class.java).convention(1)
    val apiMaximum: Property<Int> = objects.property(Int::class.java).convention(1)
    val channel: Property<String> = objects.property(String::class.java).convention("stable")
    val managedDependencies: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val artifacts: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    fun managedDependency(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String) {
        require('|' !in component + minimumVersion + repositoryOwner + repositoryName)
        managedDependencies.add(listOf(component, minimumVersion, repositoryOwner, repositoryName).joinToString("|"))
    }

    @JvmOverloads
    fun artifact(file: String, platform: String, minimumJava: Int, maximumJava: Int? = null, size: Long, sha256: String) {
        require('|' !in file + platform + sha256)
        artifacts.add(listOf(file, platform, minimumJava, maximumJava ?: "", size, sha256).joinToString("|"))
    }
}
