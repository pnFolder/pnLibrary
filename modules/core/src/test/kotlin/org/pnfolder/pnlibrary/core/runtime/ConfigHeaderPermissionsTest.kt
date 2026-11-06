package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.ConfigComment
import java.nio.file.Files
import java.nio.file.Paths

class ConfigHeaderPermissionsTest {
    // The header of config.yml is the administrator's list of permissions, so it must name every one plugin.yml declares.
    @Test
    fun `the config header lists every permission of plugin yml`() {
        val pluginYml = Paths.get("../../platforms/bukkit/runtime/src/main/resources/plugin.yml")
        val declared = Files.readAllLines(pluginYml)
            .mapNotNull { Regex("""^  (pnlibrary\.[a-z.]+):\s*$""").find(it)?.groupValues?.get(1) }
        assertTrue(declared.size >= 10, "plugin.yml permissions were not found")
        val header = DiagnosticsSettings::class.java.getAnnotation(ConfigComment::class.java).value.joinToString("\n")
        val missing = declared.filterNot { it in header }
        assertTrue(missing.isEmpty(), "config.yml header does not mention: $missing")
    }
}
