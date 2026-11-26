package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleMetadataFactory;
import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleDefinitionBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

class ModuleMetadataFactoryTest {
    private PlatformAdapter nativePlatform() {
        Map<String, String> details = new HashMap<>();
        details.put("name", "Native");
        details.put("version", "1.0");
        details.put("authors", "Native author");
        return (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PlatformAdapter.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getType":
                        return PlatformType.BUKKIT;
                    case "getImplementationName":
                        return "Paper";
                    case "ownerDetails":
                        return details;
                    case "details":
                        return Collections.emptyMap();
                    default:
                        return null;
                }
            });
    }

    @Test
    void usesExplicitMetadataBeforeNativePlatformValues() {
        PlatformAdapter platform = nativePlatform();
        ModuleDefinitionBuilder definition = new ModuleDefinitionBuilder();
        definition.setMetadataName("Explicit");
        definition.setMetadataVersion("2.0");
        definition.setMetadataAuthors("pnFolder");

        PluginMetadata metadata = new ModuleMetadataFactory(platform, platform).create(new Object(), ModuleId.of("demo"), definition);

        assertEquals("Explicit", metadata.getName());
        assertEquals("2.0", metadata.getVersion());
        assertEquals("pnFolder", metadata.getAuthors());
        assertEquals("Paper", metadata.getPlatformImplementation());
    }
}
