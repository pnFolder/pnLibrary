package org.pnfolder.pnlibrary.api.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;

class FileDownloadsArchitectureTest {
    @Test
    @DisplayName("public downloads API describes files only")
    void publicDownloadsApiDescribesFilesOnly() throws Exception {
        Class<?> downloads = Class.forName("org.pnfolder.pnlibrary.api.downloads.FileDownloads");
        Class<?> builder = null;
        for (Class<?> declared : downloads.getDeclaredClasses()) {
            if (declared.getSimpleName().equals("Builder")) {
                builder = declared;
            }
        }
        assertNotNull(builder);

        for (Method method : builder.getMethods()) {
            assertFalse(method.getName().equals("component"));
            assertFalse(method.getName().equals("plugin"));
        }
        Set<String> destinations = new HashSet<>();
        for (DownloadDestination destination : DownloadDestination.values()) {
            destinations.add(destination.name());
        }
        assertEquals(new HashSet<>(Arrays.asList("DATA_FOLDER", "CACHE")), destinations);
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.pnfolder.pnlibrary.api.downloads.PluginDownloads"));
    }
}
