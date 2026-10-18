package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion

/** Installed component metadata shared by embedded descriptors and explicit registration. */
class ProductDescriptor private constructor(
    builder: Builder,
    private val boundId: ProductId? = null,
) {
    /** Product identity assigned during module registration. */
    val id: ProductId get() = requireNotNull(boundId) {
        "Product descriptor must be bound by registerModule before its id is read"
    }
    /** Whether this descriptor has been bound to a product identity. */
    val isBound: Boolean get() = boundId != null
    /** Installed semantic version. */
    val version: SemanticVersion = builder.version
        ?: throw IllegalArgumentException("Product version is required")
    /** Inclusive pnLibrary API generations accepted by this product. */
    val supportedApi: ApiVersionRange = builder.supportedApi
        ?: throw IllegalArgumentException("Supported pnLibrary API range is required")
    /** Maximum-risk release channel accepted by this product. */
    val channel: UpdateChannel = builder.channel
    /** Oldest Java feature version supported by this installation. */
    val minimumJava: Int = builder.minimumJava
    /** Newest Java feature version supported, or `null` when unbounded. */
    val maximumJava: Int? = builder.maximumJava

    /** Copies this descriptor and binds it to normalized product [id]. */
    fun bindTo(id: String): ProductDescriptor = ProductDescriptor(
        Builder()
            .version(version.toString())
            .pnLibraryApi(supportedApi.minimum, supportedApi.maximum)
            .channel(channel)
            .java(minimumJava, maximumJava),
        ProductId.of(id),
    )

    /** Fluent builder for installed product metadata. */
    class Builder internal constructor() {
        internal var version: SemanticVersion? = null
        internal var supportedApi: ApiVersionRange? = null
        internal var channel: UpdateChannel = UpdateChannel.STABLE
        internal var minimumJava: Int = 8
        internal var maximumJava: Int? = null

        /** Sets the inclusive pnLibrary API-generation range. */
        fun pnLibraryApi(minimum: Int, maximum: Int): Builder = apply {
            supportedApi = ApiVersionRange(minimum, maximum)
        }

        /** Parses and sets the installed semantic [value]. */
        fun version(value: String): Builder = apply { version = SemanticVersion.parse(value) }

        /** Sets the maximum-risk update channel. */
        fun channel(value: UpdateChannel): Builder = apply { channel = value }

        /** Sets the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null): Builder = apply {
            require(minimum >= 8) { "Minimum Java must be at least 8" }
            require(maximum == null || maximum >= minimum) { "Maximum Java must be >= minimum Java" }
            minimumJava = minimum
            maximumJava = maximum
        }

        /** Validates required metadata and creates an unbound descriptor. */
        fun build(): ProductDescriptor = ProductDescriptor(this)
    }

    /** Creates product descriptors and standard library metadata. */
    companion object {
        /** Returns an empty product-descriptor builder. */
        @JvmStatic
        fun builder(): Builder =
            Builder()

        /** Descriptor for pnLibrary itself when no generated descriptor is available. */
        @JvmStatic
        fun library(version: String): ProductDescriptor = builder()
            .version(version)
            .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
            .build()
            .bindTo("pnlibrary")
    }
}
