package org.pnfolder.pnlibrary.core.plugin.module.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;

class MinecraftGateTest {
    private final ModulePreferences preferences = new ModulePreferences();
    private final MinecraftSupportRule plugin = new MinecraftSupportRule();
    private final MinecraftSupportRule module = new MinecraftSupportRule();

    private static final java.util.Map<String, String> PLUGIN = java.util.Collections.unmodifiableMap(new java.util.HashMap<String, String>() {{
        put("id", "shop");
        put("name", "ShopPlugin");
        put("version", "2.4.1");
    }});

    MinecraftGateTest() {
        plugin.add(MinecraftVersions.from(MinecraftVersion.V1_16_5));
        plugin.setUnsupportedAction(UnsupportedAction.STOP_PLUGIN);
    }

    private MinecraftGate open() {
        return MinecraftGate.open("shop:main", preferences, module, plugin, "1.12.2");
    }

    @Test
    void forceIsAllowedByDefaultAndStartsTheModuleAnyway() {
        MinecraftGate gate = open();

        assertTrue(gate.isForceAllowed());
        assertNotNull(gate.reason());
        gate.setForced(true);
        assertTrue(gate.isForced());
        assertEquals(null, gate.reason());
    }

    @Test
    void theAuthorCanForbidForceAndTheModuleStaysStopped() {
        plugin.setForceAllowed(false);
        MinecraftGate gate = open();

        assertFalse(gate.isForceAllowed());
        assertThrows(IllegalStateException.class, () -> gate.setForced(true));
        assertFalse(gate.isForced());
        assertNotNull(gate.reason());
    }

    @Test
    void aSavedForceNoLongerWorksOnceTheAuthorForbidsIt() {
        preferences.setAutoStart(MinecraftGate.KEY + "shop:main", false);
        plugin.setForceAllowed(false);

        MinecraftGate gate = open();

        assertFalse(gate.isForced());
        assertNotNull(gate.reason());
    }

    @Test
    void theModuleChoiceBeatsThePluginChoice() {
        plugin.setForceAllowed(false);
        module.setForceAllowed(true);

        assertTrue(open().isForceAllowed());
    }

    @Test
    void theConsoleCardNamesTheVersionsAndTheWayOut() {
        String text = String.join("\n", MinecraftBlockCard.of("main", PLUGIN, open()).render());
        assertTrue(text.contains("ShopPlugin") && text.contains("2.4.1"), text);
        assertTrue(text.contains("Нужно: 1.16.5+") && text.contains("На сервере: 1.12.2"), text);
        assertTrue(text.contains("/pn modules mc force shop:main on"), text);

        plugin.setForceAllowed(false);
        String forbidden = String.join("\n", MinecraftBlockCard.of("main", PLUGIN, open()).render());
        assertTrue(forbidden.contains("запретил автор плагина"), forbidden);
        assertFalse(forbidden.contains("/pn modules mc force"), forbidden);
    }
}
