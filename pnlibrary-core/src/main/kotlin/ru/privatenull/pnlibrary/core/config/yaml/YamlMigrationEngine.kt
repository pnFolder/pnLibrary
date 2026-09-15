package ru.privatenull.pnlibrary.core.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import ru.privatenull.pnlibrary.api.config.ConfigDocument
import ru.privatenull.pnlibrary.api.config.ConfigMigrationPlan

/**
 * Applies a complete YAML migration chain before the managed file is modified.
 *
 * Every step operates on one in-memory [ConfigDocument]. The version field is
 * advanced only after its step succeeds, and serialization occurs only after the
 * complete route has finished.
 */
internal class YamlMigrationEngine {
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        width = 120
    })

    /**
     * Successful migration output.
     *
     * @property content normalized migrated YAML
     * @property applied ordered human-readable `from -> to` step descriptions
     */
    data class Result(
        val content: String,
        val applied: List<String>,
    )

    /**
     * Migrates [content] to [ConfigMigrationPlan.currentVersion].
     *
     * @throws IllegalStateException when the source version is unavailable or a
     * migration step fails
     * @throws IllegalArgumentException when the YAML root is not an object or no
     * valid migration route exists
     */
    fun migrate(content: String, plan: ConfigMigrationPlan): Result {
        val loaded = yaml.load<Any?>(content)
        require(loaded is Map<*, *>) { "Configuration root must be a YAML object" }
        val root = loaded.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }
        val document = ConfigDocument(root)
        val sourceVersion = document.get(plan.versionKey)?.toString()
            ?: plan.assumedVersion
            ?: throw IllegalStateException(
                "Configuration version '${plan.versionKey}' is missing and no assumed version is configured"
            )
        val route = plan.path(sourceVersion)
        route.forEach { step ->
            try {
                step.migration.migrate(document)
                document.set(plan.versionKey, step.to)
            } catch (error: Exception) {
                throw IllegalStateException("Configuration migration ${step.from} -> ${step.to} failed", error)
            }
        }
        if (route.isEmpty()) document.set(plan.versionKey, plan.currentVersion)
        return Result(
            yaml.dump(document.toMap()).replace("\r\n", "\n").trimEnd() + "\n",
            route.map { "${it.from} -> ${it.to}" },
        )
    }

    /** Prepends the current schema version to generated default [content]. */
    fun stampDefaults(content: String, plan: ConfigMigrationPlan): String =
        "${plan.versionKey}: '${plan.currentVersion}'\n" + content
}
