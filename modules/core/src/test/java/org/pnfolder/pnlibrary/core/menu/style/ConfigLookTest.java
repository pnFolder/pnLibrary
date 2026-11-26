package org.pnfolder.pnlibrary.core.menu.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow;

class ConfigLookTest {
    private static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    private static String joined(List<Fact> facts) {
        StringBuilder text = new StringBuilder();
        for (Fact fact : facts) {
            text.append(fact.getValue());
        }
        return text.toString();
    }

    private static boolean anyLore(ItemSpec item, String part) {
        for (String line : item.getLore()) {
            if (plain(line).contains(part)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aPathIsCutBetweenItsPartsAndNeverInsideAName() {
        String path = "C:/Users/lpvan/Downloads/Server/Paper-Standalone/plugins/pnTest/external";

        List<Fact> lines = ConfigText.fact("Папка", path);

        assertEquals(path, joined(lines));
        assertEquals("Папка", lines.get(0).getLabel());
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                assertTrue(lines.get(index).getLabel().isEmpty());
            }
            if (index < lines.size() - 1) {
                assertTrue(lines.get(index).getValue().endsWith("/"));
            }
            assertTrue(lines.get(index).getValue().length() <= 38);
        }
    }

    @Test
    void aClassNameBreaksAfterItsDotsAndASentenceKeepsItsWordsWhole() {
        List<Fact> classLines = ConfigText.fact("Класс", "org.pnfolder.pnlibrary.example.configuration.VeryLongConfigurationClassName");
        assertEquals("org.pnfolder.pnlibrary.example.configuration.VeryLongConfigurationClassName", joined(classLines));
        for (int index = 0; index < classLines.size() - 1; index++) {
            assertTrue(classLines.get(index).getValue().endsWith("."));
        }

        String words = "очень длинное значение которое никак не помещается в одну строку описания предмета";
        StringBuilder rebuilt = new StringBuilder();
        for (Fact fact : ConfigText.fact("Значение", words)) {
            rebuilt.append(fact.getValue()).append(' ');
        }
        assertEquals(java.util.Arrays.asList(words.split(" ")), java.util.Arrays.asList(rebuilt.toString().trim().split("\\s+")));
    }

    @Test
    void aWordLongerThanAWholeLineIsCutAsTheLastResort() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            text.append('a');
        }

        List<String> lines = ConfigText.softWrap(text.toString(), 20, 30);

        assertEquals(text.toString(), String.join("", lines));
        assertTrue(lines.get(0).length() <= 20);
        for (String line : lines.subList(1, lines.size())) {
            assertTrue(line.length() <= 30);
        }
    }

    @Test
    void aFileShowsItsOwnNameAndNamesItsPluginAndModuleOnlyWhenAsked() {
        ConfigRow row = new ConfigRow("shop/main:sub/rewards.yml", "dev.shop.RewardsConfig", true, false, 2, null);

        ItemSpec alone = ConfigLook.file(row, "shop", "main", "sub/rewards.yml", false, false);
        ItemSpec mixed = ConfigLook.file(row, "shop", "main", "sub/rewards.yml", true, true);

        assertTrue(plain(alone.getName()).endsWith("rewards.yml") && !plain(alone.getName()).contains("shop"));
        assertTrue(!anyLore(alone, "Плагин"));
        assertTrue(anyLore(mixed, "Плагин") && anyLore(mixed, "Модуль"));
        assertTrue(anyLore(mixed, "Папка"));
        assertTrue(anyLore(mixed, "dev.shop.RewardsConfig"));
    }
}
