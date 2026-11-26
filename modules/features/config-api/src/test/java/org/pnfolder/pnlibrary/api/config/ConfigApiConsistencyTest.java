package org.pnfolder.pnlibrary.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigApiConsistencyTest {
    @Test
    @DisplayName("configuration document returns a deeply immutable snapshot")
    @SuppressWarnings("unchecked")
    void configurationDocumentReturnsADeeplyImmutableSnapshot() {
        Map<String, Object> nested = Collections.<String, Object>singletonMap("items", Collections.singletonList("one"));
        Map<String, Object> snapshot = new ConfigDocument(Collections.<String, Object>singletonMap("nested", nested)).toMap();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        Map<Object, Object> inner = (Map<Object, Object>) snapshot.get("nested");
        assertThrows(UnsupportedOperationException.class, inner::clear);
        List<Object> items = (List<Object>) inner.get("items");
        assertThrows(UnsupportedOperationException.class, items::clear);
    }

    @Test
    @DisplayName("identifier parts can be read and written back")
    void identifierPartsCanBeReadAndWrittenBack() {
        ConfigId id = ConfigId.parse("Quests/Main:rewards/daily.yml");

        assertEquals("quests", id.getPlugin().getValue());
        assertEquals("main", id.getModule().getValue());
        assertEquals(ConfigRoot.PLUGIN, id.getRoot());
        assertEquals("rewards/daily.yml", id.getPath());
        assertEquals("daily.yml", id.getFileName());
        assertEquals("rewards", id.getDirectory());
        assertEquals("quests/main", id.getModuleAddress().toString());
        assertEquals("quests/main:rewards/daily.yml", id.toString());
        assertEquals(id, ConfigId.of("quests", "main", "rewards\\daily.yml"));
    }

    @Test
    @DisplayName("identifier keeps a foreign root in the text")
    void identifierKeepsAForeignRootInTheText() {
        ConfigId id = ConfigId.parse("quests/main:@plugins/Essentials/config.yml");

        assertEquals(ConfigRoot.PLUGINS, id.getRoot());
        assertEquals("Essentials/config.yml", id.getPath());
        assertEquals("quests/main:@plugins/Essentials/config.yml", id.toString());
        assertEquals(ConfigRoot.SERVER, ConfigId.parse("quests/main:@server/bukkit.yml").getRoot());
    }

    @Test
    @DisplayName("address levels cover more or fewer files")
    void addressLevelsCoverMoreOrFewerFiles() {
        ConfigId file = ConfigId.parse("quests/main:quests.yml");
        ConfigId other = ConfigId.parse("quests/extra:other.yml");

        ConfigAddress plugin = ConfigAddress.parse("quests");
        ConfigAddress module = ConfigAddress.parse("quests/main");
        ConfigAddress single = ConfigAddress.parse("quests/main:quests.yml");
        assertEquals(ConfigAddress.Level.PLUGIN, plugin.getLevel());
        assertEquals(ConfigAddress.Level.MODULE, module.getLevel());
        assertEquals(ConfigAddress.Level.FILE, single.getLevel());
        assertTrue(plugin.matches(file) && plugin.matches(other));
        assertTrue(module.matches(file) && !module.matches(other));
        assertTrue(single.matches(file) && !single.matches(other));
        assertTrue(plugin.overlaps(single) && single.overlaps(plugin));
        assertTrue(!module.overlaps(ConfigAddress.parse("quests/extra")));
        assertTrue(!ConfigAddress.parse("other").overlaps(plugin));
    }

    @Test
    @DisplayName("malformed identifiers are rejected with a clear message")
    void malformedIdentifiersAreRejectedWithAClearMessage() {
        assertThrows(IllegalArgumentException.class, () -> ConfigId.parse("quests"));
        assertThrows(IllegalArgumentException.class, () -> ConfigId.parse("quests/main"));
        assertThrows(IllegalArgumentException.class, () -> ConfigAddress.parse("quests:file.yml"));
        assertThrows(IllegalArgumentException.class, () -> ConfigAddress.parse("a/b/c"));
        assertThrows(IllegalArgumentException.class, () -> ConfigAddress.parse(" "));
    }
}
