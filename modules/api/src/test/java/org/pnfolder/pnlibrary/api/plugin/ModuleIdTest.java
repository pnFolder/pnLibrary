package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModuleIdTest {
    @Test
    void idsNormalizeAndCompareByValue() {
        assertEquals(ModuleId.of("core"), ModuleId.of(" CORE "));
        assertEquals("core", ModuleId.of(" CORE ").getValue());
    }

    @Test
    void invalidIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ModuleId.of("bad id"));
        assertThrows(IllegalArgumentException.class, () -> ModuleId.of(""));
        assertThrows(IllegalArgumentException.class, () -> ModuleId.of(new String(new char[65]).replace('\0', 'a')));
    }
}
