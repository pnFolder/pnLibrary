package ru.privatenull.pnlibrary.core.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import ru.privatenull.pnlibrary.api.config.ConfigDocument
import ru.privatenull.pnlibrary.api.config.ConfigMigrationPlan

/** Applies a complete migration chain in memory before the managed file is touched. */
internal class YamlMigrationEngine {
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        width = 120
    })

    data class Result(val content: String, val applied: List<String>)

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
            } catch (error: Throwable) {
                throw IllegalStateException("Configuration migration ${step.from} -> ${step.to} failed", error)
            }
        }
        if (route.isEmpty()) document.set(plan.versionKey, plan.currentVersion)
        return Result(
            yaml.dump(document.toMap()).replace("\r\n", "\n").trimEnd() + "\n",
            route.map { "${it.from} -> ${it.to}" },
        )
    }

    fun stampDefaults(content: String, plan: ConfigMigrationPlan): String =
        "${plan.versionKey}: '${plan.currentVersion}'\n" + content
}
