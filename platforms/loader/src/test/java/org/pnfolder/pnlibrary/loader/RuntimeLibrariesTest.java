package org.pnfolder.pnlibrary.loader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.byteflux.libby.Library;
import org.junit.jupiter.api.Test;

/** The light JAR packs no Adventure, so everything the runtime imports from it must be in these lists. */
class RuntimeLibrariesTest {
    private static Set<String> names(Library[] libraries) {
        return Arrays.stream(libraries).map(Library::getArtifactId).collect(Collectors.toSet());
    }

    @Test
    void bukkitGetsEveryAdventureModuleTheRuntimeUses() {
        Set<String> names = names(RuntimeLibraries.BUKKIT);
        for (String required : new String[] {
            "adventure-api", "adventure-text-minimessage", "adventure-text-serializer-plain",
            "adventure-text-serializer-legacy", "adventure-text-serializer-gson", "adventure-platform-bukkit", "kotlin-stdlib"
        }) {
            assertTrue(names.contains(required), "Bukkit list lacks " + required);
        }
    }

    @Test
    void bungeeGetsEveryAdventureModuleTheRuntimeUses() {
        Set<String> names = names(RuntimeLibraries.BUNGEE);
        for (String required : new String[] {
            "adventure-api", "adventure-text-minimessage", "adventure-text-serializer-plain",
            "adventure-text-serializer-legacy", "adventure-text-serializer-gson", "kotlin-stdlib"
        }) {
            assertTrue(names.contains(required), "BungeeCord list lacks " + required);
        }
    }
}
