package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.config.yaml.AnnotatedYamlCodec
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml
import java.nio.file.Path
import java.util.function.Supplier
import java.util.logging.Logger

/**
 * Opens typed YAML files that belong to the library itself rather than to a plugin.
 *
 * The files use the same code-first rules as plugin configurations (defaults, comments, validation,
 * version marker), but need no plugin owner, so they are available before the runtime starts.
 */
object StandaloneConfigs {
    /**
     * Registers the typed YAML file at [file] without loading it.
     *
     * @param file absolute path of the `.yml` file; its folder is created on load
     * @param type annotated mutable model class
     * @param defaults supplier of a fresh default instance
     * @param options synchronization policy of the file
     * @param validator extra semantic validation that runs on every load and save
     * @param logger receiver of synchronization and recovery warnings
     */
    @JvmStatic
    fun <T : Any> yaml(
        file: Path,
        type: Class<T>,
        defaults: Supplier<T>,
        options: ConfigOptions,
        validator: ConfigValueValidator<T>,
        logger: Logger,
    ): ManagedConfig<T> {
        val codec = AnnotatedYamlCodec(
            type = type,
            defaults = defaults,
            serializers = BuiltInConfigSerializers.create(),
            runtimeTypes = { emptyList() },
            consumer = "pnlibrary",
            options = options,
            warning = { message -> logger.warning(message) },
        )
        return CodeFirstYaml(
            file = file.toFile(),
            defaults = defaults.get(),
            codec = codec,
            logger = logger,
            validator = ConfigValueValidator { value -> codec.validate(value) + validator.validate(value) },
            options = options,
        )
    }
}
