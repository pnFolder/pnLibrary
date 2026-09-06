package ru.privatenull.pnlibrary.api

import java.util.function.Supplier

/**
 * A declarative diagnostics container that a pnFolder plugin registers with pnLibrary.
 *
 * The public builder deliberately uses Java SAM types. pnLibrary relocates its
 * Kotlin runtime, while consuming plugins may use a different Kotlin runtime.
 * ```kotlin
 * val container = DiagnosticContainer.builder("auction")
 *     .snapshot(Supplier {
 *         mapOf(
 *             "activeLots" to auctionService.activeLots(),
 *             "cacheSize"  to cache.size,
 *         )
 *     })
 *     .configuration(DiagnosticConfiguration.file("config.yml")
 *         .exclude("storage.internalPool")
 *         .secretKeyRegex("(?i).*(password|token).*")
 *         .build())
 *     .configuration("messages.yml")
 *     .build()
 * ```
 */
class DiagnosticContainer private constructor(builder: Builder) : DiagnosticsContributor {

    private val _id: String = run {
        require(builder.id.matches(Regex("[A-Za-z0-9_.-]{1,96}"))) {
            "Invalid diagnostic container id: ${builder.id}"
        }
        builder.id
    }

    private val fixedData: Map<String, Any?> = builder.fixed.toMap()
    private val snapshotProvider: Supplier<Map<String, Any?>> =
        builder.snapshotProvider ?: Supplier { fixedData }

    private val _configurations: List<DiagnosticConfiguration> =
        builder.configurations.toList()

    override val id: String get() = _id

    override fun collect(): Map<String, Any?> =
        snapshotProvider.get()

    override fun configurationFiles(): Collection<String> =
        _configurations.map { it.path }

    override fun configurations(): Collection<DiagnosticConfiguration> =
        _configurations

    // ── Builder ───────────────────────────────────────────────────────────────

    class Builder internal constructor(internal val id: String) {
        internal var snapshotProvider: Supplier<Map<String, Any?>>? = null
        internal val fixed: MutableMap<String, Any?> = linkedMapOf()
        internal val configurations: MutableList<DiagnosticConfiguration> = mutableListOf()

        /** Supplies a dynamic snapshot. Called each time a report is collected. */
        fun snapshot(provider: Supplier<Map<String, Any?>>): Builder = apply {
            snapshotProvider = provider
        }

        /** Adds a fixed static key-value pair to the snapshot. */
        fun value(name: String, v: Any?): Builder = apply { fixed[name] = v }

        /** Adds a configuration file with optional redaction rules. */
        fun configuration(config: DiagnosticConfiguration): Builder = apply {
            configurations.add(config)
        }

        /** Convenience: add a configuration file by path. */
        fun configuration(path: String): Builder = apply {
            configurations.add(DiagnosticConfiguration.file(path).build())
        }

        fun build(): DiagnosticContainer = DiagnosticContainer(this)
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        @JvmStatic fun builder(id: String): Builder = Builder(id)
    }
}
