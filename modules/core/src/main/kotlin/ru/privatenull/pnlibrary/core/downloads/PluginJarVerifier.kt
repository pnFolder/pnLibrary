package ru.privatenull.pnlibrary.core.downloads

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.file.Path
import java.util.jar.JarFile

/** Verifies that a downloaded plugin JAR declares the expected identity and version. */
internal class PluginJarVerifier(
    private val platform: PlatformType,
) {
    fun verify(path: Path, expected: ExternalPluginDependency) {
        val descriptor = readDescriptor(path)
        verifyPluginIdentity(descriptor, expected)
        verifyPluginVersion(descriptor, expected)
    }

    private fun readDescriptor(path: Path): PluginDescriptor = JarFile(path.toFile()).use { jar ->
        val descriptorName = when (platform) {
            PlatformType.BUKKIT -> "plugin.yml"
            PlatformType.BUNGEECORD -> "bungee.yml"
            PlatformType.VELOCITY -> "velocity-plugin.json"
        }
        val entry = jar.getJarEntry(descriptorName)
            ?: throw IllegalArgumentException("JAR плагина не содержит поддерживаемого descriptor")

        @Suppress("UNCHECKED_CAST")
        val values = jar.getInputStream(entry).use { input ->
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(input) as? Map<String, Any?>
        } ?: throw IllegalArgumentException("descriptor плагина повреждён")

        PluginDescriptor(
            names = listOfNotNull(values["id"]?.toString(), values["name"]?.toString()),
            version = values["version"]?.toString()
                ?: throw IllegalArgumentException("descriptor плагина не содержит version"),
        )
    }

    private fun verifyPluginIdentity(
        descriptor: PluginDescriptor,
        expected: ExternalPluginDependency,
    ) {
        if (!expected.verifyPluginId) return
        require(descriptor.names.any { it.equals(expected.plugin, ignoreCase = true) }) {
            "скачанный JAR объявляет ${descriptor.names.joinToString("/")}, ожидался ${expected.plugin}"
        }
    }

    private fun verifyPluginVersion(
        descriptor: PluginDescriptor,
        expected: ExternalPluginDependency,
    ) {
        if (!expected.verifyVersion) return
        val version = SemanticVersion.tryParse(descriptor.version)
            ?: throw IllegalArgumentException(
                "версия скачанного ${expected.plugin} не распознана: ${descriptor.version}",
            )
        require(expected.versions.accepts(version)) {
            "версия скачанного ${expected.plugin} $version не соответствует требованию ${expected.minimumVersion}"
        }
    }

    private data class PluginDescriptor(
        val names: List<String>,
        val version: String,
    )
}
