package org.pnfolder.pnlibrary.core.plugin.module.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.console.ConsoleCard;
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

    private static String text(ConsoleCard card) {
        return String.join("\n", card.render());
    }

    @Test
    void aNewerServerStartsWithACardByDefaultAndNeverWithABlock() {
        MinecraftSupportRule only = new MinecraftSupportRule();
        only.add(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4));
        MinecraftGate gate = MinecraftGate.open("shop:main", preferences, module, only, "1.21.5");

        assertEquals(null, gate.reason());
        String card = text(MinecraftNoticeCard.of("main", PLUGIN, gate));
        assertTrue(card.contains("Проверено: 1.16.5") && card.contains("На сервере: 1.21.5"), card);
        assertTrue(card.contains("ваш страх и риск") && card.contains("ShopPlugin@main"), card);
    }

    @Test
    void aVersionInsideTheRangeSaysNothing() {
        MinecraftSupportRule only = new MinecraftSupportRule();
        only.add(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4));

        assertEquals(null, MinecraftNoticeCard.of("main", PLUGIN,
            MinecraftGate.open("shop:main", preferences, module, only, "1.20.1")));
    }

    @Test
    void blockingNewerStaysABlockEvenWhenOlderOnlyWarns() {
        MinecraftSupportRule rule = new MinecraftSupportRule();
        rule.add(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4));
        rule.setNewerAction(org.pnfolder.pnlibrary.api.compat.declaration.NewerAction.BLOCK);
        rule.setUnsupportedAction(UnsupportedAction.WARN_ONLY);

        MinecraftGate newer = MinecraftGate.open("shop:main", preferences, module, rule, "1.21.5");
        MinecraftGate older = MinecraftGate.open("shop:main", preferences, module, rule, "1.12.2");

        assertNotNull(newer.reason());
        assertEquals(null, older.reason());
        assertTrue(text(MinecraftNoticeCard.of("main", PLUGIN, older)).contains("СТАРАЯ ВЕРСИЯ"));
        assertTrue(text(MinecraftBlockCard.of("main", PLUGIN, newer)).contains("версия новее проверенных"));
    }

    @Test
    void forceCanBeForbiddenAboveTheNewestVersionOnly() {
        MinecraftSupportRule rule = new MinecraftSupportRule();
        rule.add(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4));
        rule.setNewerAction(org.pnfolder.pnlibrary.api.compat.declaration.NewerAction.BLOCK);
        rule.setForceNewerAllowed(false);

        assertFalse(MinecraftGate.open("shop:main", preferences, module, rule, "1.21.5").isForceAllowed());
        assertTrue(MinecraftGate.open("shop:main", preferences, module, rule, "1.12.2").isForceAllowed());
    }

    @Test
    void aForcedStartIsAnnouncedWithTheWayBack() {
        MinecraftGate gate = open();
        gate.setForced(true);

        String card = text(MinecraftNoticeCard.of("main", PLUGIN, gate));
        assertTrue(card.contains("ЗАПУСК НА СВОЙ РИСК") && card.contains("/pn modules mc force shop:main off"), card);
    }

    @Test
    void aBlockedModuleSaysWhetherThePluginOrOnlyTheModuleStops() {
        assertTrue(text(MinecraftBlockCard.of("main", PLUGIN, open(), true)).contains("Плагин остановлен"));
        assertTrue(text(MinecraftBlockCard.of("main", PLUGIN, open(), false)).contains("остальные модули работают"));
    }
}
