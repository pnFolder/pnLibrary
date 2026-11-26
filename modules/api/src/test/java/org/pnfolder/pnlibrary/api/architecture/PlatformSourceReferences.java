package org.pnfolder.pnlibrary.api.architecture;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds static platform references without treating comments or literal text as dependencies. */
final class PlatformSourceReferences {
    static final Map<String, List<String>> PACKAGES = new LinkedHashMap<>();

    static {
        PACKAGES.put("bukkit", Arrays.asList("org.pnfolder.pnlibrary.bukkit", "org.bukkit", "org.spigotmc",
            "io.papermc.paper", "com.destroystokyo.paper"));
        PACKAGES.put("bungee", Arrays.asList("org.pnfolder.pnlibrary.bungee", "net.md_5.bungee"));
        PACKAGES.put("velocity", Arrays.asList("org.pnfolder.pnlibrary.velocity", "com.velocitypowered"));
    }

    private PlatformSourceReferences() {
    }

    static List<String> allPackages() {
        List<String> all = new java.util.ArrayList<>();
        for (List<String> namespaces : PACKAGES.values()) {
            all.addAll(namespaces);
        }
        return all;
    }

    static Set<String> find(String source) {
        return find(source, allPackages());
    }

    static Set<String> find(String source, Collection<String> forbiddenPackages) {
        Set<String> references = new LinkedHashSet<>();
        StringBuilder name = new StringBuilder();
        boolean expectsIdentifier = true;
        int length = source.length();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (source.startsWith("//", i)) {
                // comments never separate the parts of a qualified name
                int end = source.indexOf('\n', i);
                i = end < 0 ? length : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < length && Character.isJavaIdentifierPart(source.charAt(i))) {
                    i++;
                }
                if (!expectsIdentifier) {
                    finish(name, forbiddenPackages, references);
                }
                name.append(source, start, i);
                expectsIdentifier = false;
            } else if (c == '.') {
                if (!expectsIdentifier) {
                    name.append('.');
                    expectsIdentifier = true;
                } else {
                    finish(name, forbiddenPackages, references);
                }
                i++;
            } else {
                finish(name, forbiddenPackages, references);
                expectsIdentifier = true;
                i = c == '"' || c == '\'' ? skipLiteral(source, i) : i + 1;
            }
        }
        finish(name, forbiddenPackages, references);
        return references;
    }

    private static int skipLiteral(String source, int start) {
        char quote = source.charAt(start);
        int i = start + 1;
        while (i < source.length() && source.charAt(i) != quote) {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        return i + 1;
    }

    private static void finish(StringBuilder name, Collection<String> forbiddenPackages, Set<String> references) {
        String reference = name.toString();
        while (reference.endsWith(".")) {
            reference = reference.substring(0, reference.length() - 1);
        }
        for (String forbidden : forbiddenPackages) {
            if (reference.equals(forbidden) || reference.startsWith(forbidden + ".")) {
                references.add(reference);
                break;
            }
        }
        name.setLength(0);
    }
}
