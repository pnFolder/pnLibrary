package ru.privatenull.pnlibrary.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
import ru.privatenull.pnlibrary.api.config.ConfigDocument
import ru.privatenull.pnlibrary.api.config.ConfigSteps

/**
 * Brings an existing file to the version of its [ConfigSteps] and renders the result.
 *
 * The file's marker says which version was applied last; only newer versions run. Files from a
 * newer plugin version are reported through [Result.newerThanCode] and must not be rewritten.
 */
internal class YamlMigrationEngine {
    private val yaml = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        width = 120
    }.let { options -> Yaml(SafeConstructor(LoaderOptions()), Representer(options), options) }

    /**
     * Outcome of [migrate].
     *
     * @property content migrated YAML with the version marker, or the input when [newerThanCode]
     * @property applied human-readable version labels, such as `v2`, in application order
     * @property newerThanCode `true` when the file was written by a newer version of the plugin
     */
    data class Result(
        val content: String,
        val applied: List<String>,
        val newerThanCode: Boolean = false,
    )

    /**
     * Applies every version of [steps] newer than the version stored in [content].
     *
     * @throws IllegalStateException when a step fails or the version numbering is broken
     * @throws IllegalArgumentException when the YAML root is not an object
     */
    fun migrate(content: String, steps: ConfigSteps<*>): Result {
        val loaded = yaml.load<Any?>(content) ?: emptyMap<String, Any?>()
        require(loaded is Map<*, *>) { "Configuration root must be a YAML object" }
        val root = loaded.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }
        val document = ConfigDocument(root)
        val fileVersion = document.get(ConfigSteps.VERSION_KEY)?.toString()?.trim()?.toIntOrNull()
            ?: ConfigSteps.BASE_VERSION
        if (fileVersion > steps.latestVersion) return Result(content, emptyList(), newerThanCode = true)

        val applied = steps.applyTo(document, fileVersion)
        // The marker is written back by stampDefaults, together with its warning comment.
        document.remove(ConfigSteps.VERSION_KEY)
        val body = document.toMap()
        val dumped = if (body.isEmpty()) "" else yaml.dump(body).replace("\r\n", "\n").trimEnd() + "\n"
        return Result(stampDefaults(dumped, steps), applied.map { "v$it" })
    }

    /** Prepends the warning and the version marker of [steps] to [content]. */
    fun stampDefaults(content: String, steps: ConfigSteps<*>): String =
        VERSION_WARNING + "${ConfigSteps.VERSION_KEY}: ${steps.latestVersion}\n" + content

    private companion object {
        const val VERSION_WARNING =
            "# !!! DO NOT EDIT OR REMOVE THIS LINE: pnLibrary uses it to upgrade this file between versions. !!!\n" +
                "# !!! НЕ МЕНЯЙТЕ И НЕ УДАЛЯЙТЕ ЭТУ СТРОКУ: pnLibrary использует её для обновления файла между версиями. !!!\n"
    }
}
