package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformSourceReferencesTest {
    private static HashSet<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    @DisplayName("imports from native and library platform namespaces are detected")
    void importsFromNativeAndLibraryPlatformNamespacesAreDetected() {
        for (String namespace : PlatformSourceReferences.allPackages()) {
            assertEquals(set(namespace + ".Example"),
                PlatformSourceReferences.find("import " + namespace + ".Example;"));
        }
    }

    @Test
    @DisplayName("fully qualified calls cannot bypass import checks")
    void fullyQualifiedCallsCannotBypassImportChecks() {
        assertEquals(set("org.bukkit.Bukkit.getServer"),
            PlatformSourceReferences.find("org.bukkit.Bukkit.getServer()"));
    }

    @Test
    @DisplayName("static and wildcard imports are detected")
    void staticAndWildcardImportsAreDetected() {
        assertEquals(set("org.bukkit.Bukkit.getServer"),
            PlatformSourceReferences.find("import static org.bukkit.Bukkit.getServer;"));
        assertEquals(set("org.bukkit"), PlatformSourceReferences.find("import org.bukkit.*;"));
    }

    @Test
    @DisplayName("comments and diagnostic string literals are not dependencies")
    void commentsAndDiagnosticStringLiteralsAreNotDependencies() {
        String head = "// org.bukkit.Bukkit.getServer()\n"
            + "/* com.velocitypowered.api.proxy.ProxyServer */\n"
            + "/** @see net.md_5.bungee.api.ProxyServer */\n"
            + "\"org.bukkit.Bukkit\"\n"
            + "\"com.velocitypowered.api.proxy.ProxyServer\"\n";
        assertTrue(PlatformSourceReferences.find(head).isEmpty());
    }

    @Test
    @DisplayName("whitespace and comments do not hide qualified references")
    void whitespaceAndCommentsDoNotHideQualifiedReferences() {
        assertEquals(set("org.bukkit.Bukkit"),
            PlatformSourceReferences.find("org /* comment */ . bukkit\n. Bukkit"));
    }

    @Test
    @DisplayName("similar package names are allowed and a platform can keep its own references")
    void similarPackageNamesAreAllowedAndAPlatformCanKeepItsOwnReferences() {
        assertTrue(PlatformSourceReferences.find("org.bukkitish.Example; example.org.bukkit.Example").isEmpty());
        Map<String, List<String>> others = new LinkedHashMap<>(PlatformSourceReferences.PACKAGES);
        others.remove("bukkit");
        List<String> otherPlatforms = new java.util.ArrayList<>();
        for (List<String> namespaces : others.values()) {
            otherPlatforms.addAll(namespaces);
        }
        assertEquals(Collections.singleton("com.velocitypowered.api.proxy.ProxyServer"),
            PlatformSourceReferences.find("org.bukkit.Bukkit; com.velocitypowered.api.proxy.ProxyServer",
                otherPlatforms));
    }
}
