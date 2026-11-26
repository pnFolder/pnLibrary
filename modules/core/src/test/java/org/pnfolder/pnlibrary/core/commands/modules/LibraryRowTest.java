package org.pnfolder.pnlibrary.core.commands.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LibraryRowTest {
    private static LibraryRow row(String owner) {
        return new LibraryRow(owner, "Hikari", "g:a:1", "1", null, Collections.<String>emptyList(), true, true, false, false);
    }

    @Test
    @DisplayName("a plugin owns its own libraries and those of its modules but not another plugin's")
    void aPluginOwnsItsOwnLibrariesAndThoseOfItsModulesButNotAnotherPluginsEither() {
        assertTrue(row("shop").belongsTo("shop"));
        assertTrue(row("shop:cart").belongsTo("shop"));
        assertTrue(row("shop:cart").belongsTo("shop:cart"));
        assertFalse(row("shop:cart").belongsTo("shop:other"));
        assertFalse(row("shopping").belongsTo("shop"));
    }

    @Test
    @DisplayName("whose library it is is told in words and split into plugin and module")
    void whoseLibraryItIsIsToldInWordsAndSplitIntoPluginAndModule() {
        assertEquals("плагину shop", row("shop").getOwnerText());
        assertEquals("модулю cart плагина shop", row("shop:cart").getOwnerText());
        assertEquals("shop", row("shop:cart").getPlugin());
        assertEquals("cart", row("shop:cart").getModule());
        assertNull(row("shop").getModule());
    }

    @Test
    @DisplayName("the place is counted from the server folder and split into folder and file")
    void thePlaceIsCountedFromTheServerFolderAndSplitIntoFolderAndFile() {
        Path root = Paths.get("srv");
        String place = LibraryRow.placeOf(root.resolve("plugins/pnLibrary/libs/a/b-1.jar").toString(), root);
        assertEquals("plugins/pnLibrary/libs/a/b-1.jar", place);
        LibraryRow located = row("shop").withPlace(place);
        assertEquals("plugins/pnLibrary/libs/a", located.getFolder());
        assertEquals("b-1.jar", located.getFileName());
        assertNull(row("shop").getFolder());
    }
}
