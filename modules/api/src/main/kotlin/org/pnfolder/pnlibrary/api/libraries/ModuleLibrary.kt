package org.pnfolder.pnlibrary.api.libraries

import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.util.Collections

/**
 * A Maven library a module asks pnLibrary to download, verify and load, with every setting of that download.
 *
 * ```kotlin
 * ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
 *     .source { link("https://cdn.example.org/HikariCP-5.1.0.jar").sha256("3c0e…").name("CDN") }   // 1st place to try
 *     .source { repository("https://mirror.example.org/maven/").sha256("9f2a…") }                    // 2nd place
 *     .scope(LibraryScope.ISOLATED)                                                                  // own class loader
 *     .build()
 * ```
 *
 * The one list of [sources] says where the jar comes from; each place has its own address, sum and name. They are tried in
 * order, then Maven Central unless [Builder.mavenCentral] turns it off; the first that gives a jar passing its check wins.
 * Transitive dependencies are not resolved: list every jar the library needs.
 *
 * @property group Maven group id
 * @property artifact Maven artifact id
 * @property version Maven version
 * @property classifier optional classifier such as `shaded`
 * @property sources the places the jar can come from, as many as you like, tried in the given order
 * @property mavenCentral whether Maven Central is tried after the sources
 * @property scope which class loader the library joins; the plugin's own by default
 * @property platforms the platforms the library is loaded on; empty means every platform. The platform is detected by
 * pnLibrary: on another platform the library is skipped, not an error
 * @property title short name players and administrators read, such as `Hikari connection pool`; the artifact id when absent
 * @property description one or two sentences on what the library is for, shown in `/pn modules` and the menu
 * @property required `true`: the module fails to start when the library cannot be loaded; `false`: it starts without it
 */
class ModuleLibrary private constructor(builder: Builder) {
    val group: String = builder.group
    val artifact: String = builder.artifact
    val version: String = builder.version
    val classifier: String? = builder.classifier
    val sources: List<LibrarySource> = Collections.unmodifiableList(ArrayList(builder.sources))
    val mavenCentral: Boolean = builder.mavenCentral
    val scope: LibraryScope = builder.scope
    val platforms: Set<PlatformType> = Collections.unmodifiableSet(LinkedHashSet(builder.platforms))
    val required: Boolean = builder.required
    val title: String = builder.title ?: builder.artifact
    val description: String? = builder.description

    /** `true` when the library lives in a class loader of its own. */
    val isolated: Boolean get() = scope == LibraryScope.ISOLATED

    /** Whether the library is loaded on [platform]; always when no platforms were listed, or the platform is unknown. */
    fun appliesTo(platform: PlatformType?): Boolean = platforms.isEmpty() || platform == null || platform in platforms

    /** `group:artifact:version`, or `group:artifact:version:classifier`. */
    val coordinates: String get() = listOfNotNull(group, artifact, version, classifier).joinToString(":")

    /** Every place to try, in order: [sources], then Maven Central when it is on. */
    val allSources: List<LibrarySource>
        get() = sources + listOfNotNull(CENTRAL_SOURCE.takeIf { mavenCentral })

    override fun toString(): String = coordinates
    override fun equals(other: Any?): Boolean = other is ModuleLibrary && other.coordinates == coordinates && other.scope == scope
    override fun hashCode(): Int = coordinates.hashCode() * 31 + scope.hashCode()

    /** Java-friendly builder; [build] checks every value. */
    class Builder internal constructor(internal val group: String, internal val artifact: String, internal val version: String) {
        internal var classifier: String? = null
        internal val sources = mutableListOf<LibrarySource>()
        internal var mavenCentral = true
        internal var scope = LibraryScope.PLUGIN
        internal val platforms = linkedSetOf<PlatformType>()
        internal var required = true
        internal var title: String? = null
        internal var description: String? = null

        /** Sets the short name shown to people instead of the artifact id. */
        fun title(value: String): Builder = apply { title = value.trim().ifEmpty { null } }

        /** Says what the library is for, in a sentence or two; players and administrators read it in the menu. */
        fun description(value: String): Builder = apply { description = value.trim().ifEmpty { null } }

        /** Adds an alternative; call it as many times as you like, the first added is tried first. */
        fun source(source: LibrarySource): Builder = apply { sources += source }

        /** Adds an alternative made in place: `.source { link("https://…").sha256("…") }`. */
        fun source(configure: LibrarySource.Builder.() -> Unit): Builder = source(LibrarySource.builder().apply(configure).build())

        /** Sets the classifier of the jar, such as `shaded`. */
        fun classifier(value: String): Builder = apply { classifier = value.trim().ifEmpty { null } }

        /** Chooses which class loader the library joins; [LibraryScope.PLUGIN] by default. */
        fun scope(value: LibraryScope): Builder = apply { scope = value }

        /** Loads the library only on these platforms; without it every platform loads it. */
        fun platforms(vararg types: PlatformType): Builder = apply {
            require(PlatformType.UNIVERSAL !in types) { "UNIVERSAL is not a runtime platform" }
            platforms += types
        }

        /** Turns Maven Central on or off after the sources; on by default. */
        fun mavenCentral(enabled: Boolean): Builder = apply { mavenCentral = enabled }

        /** Lets the module start even when this library cannot be loaded. */
        fun optional(): Builder = apply { required = false }

        /** Validates the library and creates it. */
        fun build(): ModuleLibrary {
            require(group.matches(PART) && artifact.matches(PART)) { "Invalid library name: $group:$artifact" }
            require(version.matches(VERSION)) { "Invalid library version: $version" }
            require(classifier?.matches(PART) ?: true) { "Invalid classifier: $classifier" }
            return ModuleLibrary(this)
        }
    }

    companion object {
        /** Maven Central, the repository used last by default. */
        const val CENTRAL = "https://repo1.maven.org/maven2/"
        private val CENTRAL_SOURCE = LibrarySource.builder().repository(CENTRAL).name("Maven Central").build()
        private val PART = Regex("[A-Za-z0-9_.-]+")
        private val VERSION = Regex("[A-Za-z0-9_.+-]+")
        private val SHA256 = Regex("[0-9A-Fa-f]{64}")

        /** Starts a library `group:artifact:version`. */
        @JvmStatic
        fun builder(group: String, artifact: String, version: String): Builder = Builder(group, artifact, version)

        /** A library from its `group:artifact:version` text with default settings. */
        @JvmStatic
        fun of(coordinates: String): ModuleLibrary {
            val parts = coordinates.trim().split(':')
            require(parts.size == 3 || parts.size == 4) { "Expected group:artifact:version, got: $coordinates" }
            return builder(parts[0], parts[1], parts[2]).apply { parts.getOrNull(3)?.let(::classifier) }.build()
        }
    }
}
