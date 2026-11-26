package org.pnfolder.pnlibrary.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PluginDescriptorTest {
    @Test
    void pluginYmlIsValidAndNamesTheEntryClass() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream, "plugin.yml is missing");
            Map<?, ?> descriptor = (Map<?, ?>) new Yaml().load(stream);
            assertEquals("org.pnfolder.pnlibrary.bukkit.PnLibraryBukkitPlugin", descriptor.get("main"));
            assertNotNull(Class.forName(descriptor.get("main").toString()));
        }
    }
}
