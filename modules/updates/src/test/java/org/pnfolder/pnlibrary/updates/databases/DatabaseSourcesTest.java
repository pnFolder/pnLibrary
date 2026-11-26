package org.pnfolder.pnlibrary.updates.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.config.ManagedConfigFile;
import org.pnfolder.pnlibrary.config.yaml.DatabasePath;

class DatabaseSourcesTest {
    private static ManagedConfigFile file(String... names) {
        List<DatabasePath> databases = new ArrayList<DatabasePath>();
        for (String name : names) {
            databases.add(new DatabasePath(name.toLowerCase(), DatabaseKind.MYSQL, name));
        }
        return new ManagedConfigFile(PluginId.of("pnClans"), Paths.get("plugins/pnClans"), "config.yml",
                Collections.<String>emptyList(), databases);
    }

    @Test
    void databasesAreFoundByPluginWithoutRegardToCaseAndForgottenOnClose() throws Exception {
        DatabaseSources sources = new DatabaseSources();
        AutoCloseable handle = sources.register(file("Кланы", "Банк"));
        List<String> found = new ArrayList<String>();
        for (DatabaseSource source : sources.of("PNCLANS")) {
            found.add(source.getName());
        }
        assertEquals(Arrays.asList("Кланы", "Банк"), found);
        handle.close();
        assertEquals(0, sources.of("pnclans").size());
    }

    @Test
    void twoDatabasesWithOneNameAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSources().register(file("Кланы", "Кланы")));
    }
}
