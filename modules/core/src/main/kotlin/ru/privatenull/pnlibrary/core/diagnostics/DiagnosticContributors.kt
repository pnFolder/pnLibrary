package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Contributor registrations and configuration declarations for one plugin. */
internal class DiagnosticContributors(
    private val sanitizer: DiagnosticValueSanitizer,
) {
    private val contributors = ConcurrentHashMap<String, RegisteredContributor>()

    fun register(
        contributor: DiagnosticsContributor,
        dataDirectory: Path?,
    ): DiagnosticRegistration {
        val id = sanitizer.text(contributor.id, MAXIMUM_ID_LENGTH).also { sanitizedId ->
            require(sanitizedId.isNotEmpty()) { "contributor id must not be empty" }
        }
        val registered = RegisteredContributor(contributor, dataDirectory)
        contributors[id] = registered
        return ContributorRegistration(id, registered)
    }

    fun collect(): Map<String, Any?> = linkedMapOf<String, Any?>().also { result ->
        contributors.forEach { (id, registered) ->
            result[id] = try {
                sanitizer.map(registered.contributor.collect())
            } catch (exception: Exception) {
                mapOf("collectionError" to sanitizer.exception(exception))
            }
        }
    }

    fun configurations(plugin: String): List<RegisteredDiagnosticConfiguration> {
        val files = linkedMapOf<String, RegisteredDiagnosticConfiguration>()
        contributors.values.forEach { registered ->
            collectConfigurations(plugin, registered, files)
        }
        return files.values.toList()
    }

    private fun collectConfigurations(
        plugin: String,
        registered: RegisteredContributor,
        destination: MutableMap<String, RegisteredDiagnosticConfiguration>,
    ) {
        try {
            registered.contributor.configurations().forEach { configuration ->
                if (destination.size < MAXIMUM_CONFIGURATION_FILES) {
                    destination.putIfAbsent(
                        configuration.path,
                        RegisteredDiagnosticConfiguration(plugin, registered.dataDirectory, configuration),
                    )
                }
            }
            registered.contributor.configurationFiles().forEach { path ->
                if (destination.size < MAXIMUM_CONFIGURATION_FILES && path !in destination) {
                    runCatching {
                        destination[path] = RegisteredDiagnosticConfiguration(
                            plugin,
                            registered.dataDirectory,
                            DiagnosticConfiguration.builder(path).build(),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // One malformed contributor must not prevent collection from the others.
        }
    }

    private inner class ContributorRegistration(
        private val id: String,
        private val registered: RegisteredContributor,
    ) : DiagnosticRegistration {
        private val closed = AtomicBoolean(false)
        override val isClosed: Boolean get() = closed.get()

        override fun close() {
            if (closed.compareAndSet(false, true)) contributors.remove(id, registered)
        }
    }

    private data class RegisteredContributor(
        val contributor: DiagnosticsContributor,
        val dataDirectory: Path?,
    )

    private companion object {
        const val MAXIMUM_ID_LENGTH = 96
        const val MAXIMUM_CONFIGURATION_FILES = 64
    }
}

/** A configuration declaration resolved against the owning plugin directory. */
internal data class RegisteredDiagnosticConfiguration(
    val plugin: String,
    val dataDirectory: Path?,
    val configuration: DiagnosticConfiguration,
)
