package ru.privatenull.pnlibrary.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
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
    private val yaml = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        width = 120
    }.let { options -> Yaml(SafeConstructor(LoaderOptions()), Representer(options), options) }

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
        val loaded = yaml.load<Any?>(content) ?: emptyMap<String, Any?>()
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
        // The marker is re-added by stampDefaults together with its warning comment.
        document.remove(plan.versionKey)
        val body = document.toMap()
        val dumped = if (body.isEmpty()) "" else yaml.dump(body).replace("\r\n", "\n").trimEnd() + "\n"
        return Result(
            stampDefaults(dumped, plan),
            route.map { "${it.from} -> ${it.to}" },
        )
    }

    /** Prepends the current schema version to generated default [content]. */
    fun stampDefaults(content: String, plan: ConfigMigrationPlan): String =
        VERSION_WARNING + "${plan.versionKey}: '${plan.currentVersion}'\n" + content

    private companion object {
        const val VERSION_WARNING =
            "# !!! DO NOT EDIT OR REMOVE THIS LINE: pnLibrary uses it to upgrade this file between versions. !!!\n" +
                "# !!! НЕ МЕНЯЙТЕ И НЕ УДАЛЯЙТЕ ЭТУ СТРОКУ: pnLibrary использует её для обновления файла между версиями. !!!\n"
    }
}
