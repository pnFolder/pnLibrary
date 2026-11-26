package org.pnfolder.pnlibrary.updates.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryPathsTest {
    @TempDir
    Path directory;

    private final Set<String> outsideData = new HashSet<String>(Arrays.asList(
        "pluginFolder", "plugins", "serverUpdateFolder", "legacyConfig", "legacyDownloadsConfig", "localization",
        "data"));

    private Map<String, Path> locations(LibraryPaths paths) throws IllegalAccessException, InvocationTargetException {
        Map<String, Path> result = new LinkedHashMap<String, Path>();
        for (Method method : LibraryPaths.class.getMethods()) {
            if (method.getParameterCount() == 0 && Path.class.isAssignableFrom(method.getReturnType())
                && method.getName().startsWith("get")) {
                String name = method.getName().substring(3);
                result.put(Character.toLowerCase(name.charAt(0)) + name.substring(1), (Path) method.invoke(paths));
            }
        }
        return result;
    }

    @Test
    void everyStateLocationLiesUnderTheDataFolderAndNothingElseDoes() throws Exception {
        Path plugin = directory.resolve("plugins/pnLibrary");
        LibraryPaths paths = new LibraryPaths(plugin);

        Set<String> outside = new HashSet<String>();
        int inside = 0;
        for (Map.Entry<String, Path> entry : locations(paths).entrySet()) {
            if (outsideData.contains(entry.getKey())) {
                outside.add(entry.getKey());
                continue;
            }
            inside++;
            Path path = entry.getValue();
            assertTrue(path.startsWith(paths.getData()) && !path.equals(paths.getData()),
                entry.getKey() + " is " + path);
        }
        assertEquals(outsideData, outside);
        assertTrue(inside > 0);
        assertEquals(plugin.resolve("data"), paths.getData());
    }

    @Test
    void administratorFilesAndTheServerUpdateFolderStayOutOfData() {
        Path plugin = directory.resolve("plugins/pnLibrary");
        LibraryPaths paths = new LibraryPaths(plugin);

        assertEquals(plugin.resolve("config.yml"), paths.getLegacyConfig());
        assertEquals(plugin.resolve("downloads.yml"), paths.getLegacyDownloadsConfig());
        assertEquals(plugin.resolve("localization"), paths.getLocalization());
        assertEquals(directory.resolve("plugins/update"), paths.getServerUpdateFolder());
        assertEquals(directory.resolve("plugins"), paths.getPlugins());
    }

    @Test
    void noTwoStateLocationsAreTheSame() throws Exception {
        Map<String, Path> inside = locations(new LibraryPaths(directory.resolve("plugins/pnLibrary")));
        inside.keySet().removeAll(outsideData);
        assertEquals(inside.size(), new HashSet<Path>(inside.values()).size());
    }
}
