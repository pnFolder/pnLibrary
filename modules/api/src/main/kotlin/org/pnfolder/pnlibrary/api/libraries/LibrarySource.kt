package org.pnfolder.pnlibrary.api.libraries

import java.net.URI

/**
 * One place a library's jar can come from, with the settings of that place only. A library lists any number of sources;
 * they are tried in order until one gives a jar that passes its check.
 *
 * ```kotlin
 * LibrarySource.builder().link("https://cdn.example.org/a-1.0.jar").sha256("3c0e…").name("CDN").build()
 * LibrarySource.builder().repository("https://repo.example.org/maven2/").build()   // Maven path under this repository
 * ```
 *
 * @property url HTTPS address: a direct link to the jar, or the root of a Maven repository, see [direct]
 * @property direct `true` for a link to the jar itself, `false` for a Maven repository the path is built under
 * @property sha256 sum the jar from this source must have; `null` means the jar from this source is not checked
 * @property name short label shown in errors and in the menu; the host of [url] when absent
 * @property description a sentence on what this place is, such as `the main repository of the company`; `null` when not given
 */
class LibrarySource private constructor(builder: Builder) {
    val url: String = builder.url ?: error("A source needs a link or a repository")
    val direct: Boolean = builder.direct
    val sha256: String? = builder.sha256
    val name: String = builder.name ?: URI.create(url).host
    val description: String? = builder.description

    override fun toString(): String = "$name ($url)"
    override fun equals(other: Any?): Boolean = other is LibrarySource && other.url == url && other.direct == direct && other.sha256 == sha256
    override fun hashCode(): Int = (url.hashCode() * 31 + direct.hashCode()) * 31 + (sha256?.hashCode() ?: 0)

    /** Java-friendly builder; [build] checks that an address was given. */
    class Builder internal constructor() {
        internal var url: String? = null
        internal var direct = false
        internal var sha256: String? = null
        internal var name: String? = null
        internal var description: String? = null

        /** The jar lies at this exact HTTPS link. */
        fun link(url: String): Builder = apply { this.url = https(url, "Library link"); direct = true }

        /** The jar is looked up by its Maven path under this HTTPS repository. */
        fun repository(url: String): Builder = apply { this.url = https(url, "Repository"); direct = false }

        /** The sum the jar from this source must have, 64 hexadecimal characters. */
        fun sha256(hex: String): Builder = apply {
            require(hex.matches(SHA256)) { "SHA-256 must be 64 hexadecimal characters" }
            sha256 = hex.lowercase()
        }

        /** Gives the source a short label. */
        fun name(value: String): Builder = apply { name = value.trim().ifEmpty { null } }

        /** Says in a sentence what this place is. */
        fun description(value: String): Builder = apply { description = value.trim().ifEmpty { null } }

        /** Creates the source. */
        fun build(): LibrarySource = LibrarySource(this)
    }

    companion object {
        private val SHA256 = Regex("[0-9A-Fa-f]{64}")

        /** Starts a source; set a [Builder.link] or a [Builder.repository]. */
        @JvmStatic
        fun builder(): Builder = Builder()

        internal fun https(value: String, what: String): String {
            val uri = URI.create(value.trim())
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "$what must be an HTTPS link: $value" }
            return uri.toASCIIString()
        }
    }
}
