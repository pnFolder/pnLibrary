package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ModuleServiceKeyTest {
    @Test
    void createsStableInstanceScopedKeys() {
        PluginId nativePlugin = PluginId.of("example");
        ModuleId module = ModuleId.of("main");

        PluginId first = ModuleServiceKey.of(7, nativePlugin, module);
        assertEquals(first, ModuleServiceKey.of(7, nativePlugin, module));
        assertNotEquals(first, ModuleServiceKey.of(8, nativePlugin, module));
        String value = first.getValue();
        assertEquals("m-example-main-", value.substring(0, value.lastIndexOf('-')) + "-");
    }
}
