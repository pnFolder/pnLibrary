package org.pnfolder.pnlibrary.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the top-level packages of {@code core} layered: a package may depend on others, but no chain of imports
 * may lead back to where it started. Without this, shared helpers drift into a hub package and every
 * feature ends up importing the hub that wires it.
 */
class CorePackageCyclesTest {
    private final Path root = Paths.get("src/main/java/org/pnfolder/pnlibrary/core");
    private final Pattern importPattern = Pattern.compile("^import org\\.pnfolder\\.pnlibrary\\.core\\.(\\w+)\\.");

    private Map<String, Set<String>> graph() throws IOException {
        Map<String, Set<String>> edges = new HashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream.filter(it -> it.toString().endsWith(".java"))::iterator) {
                Path relative = root.relativize(file);
                if (relative.getNameCount() <= 1) {
                    continue;
                }
                String owner = relative.getName(0).toString();
                for (String line : Files.readAllLines(file)) {
                    Matcher match = importPattern.matcher(line);
                    if (match.find() && !match.group(1).equals(owner)) {
                        edges.computeIfAbsent(owner, key -> new HashSet<>()).add(match.group(1));
                    }
                }
            }
        }
        return edges;
    }

    private List<String> visit(Map<String, Set<String>> graph, String start, String node, List<String> path, Set<String> seen) {
        Set<String> next = graph.containsKey(node) ? new TreeSet<>(graph.get(node)) : Collections.emptySet();
        for (String candidate : next) {
            if (candidate.equals(start)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(start);
                return cycle;
            }
            if (!seen.contains(candidate)) {
                List<String> longer = new ArrayList<>(path);
                longer.add(candidate);
                Set<String> wider = new HashSet<>(seen);
                wider.add(candidate);
                List<String> found = visit(graph, start, candidate, longer, wider);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<String> findCycle(Map<String, Set<String>> graph) {
        for (String start : new TreeSet<>(graph.keySet())) {
            List<String> found = visit(graph, start, start, Collections.singletonList(start), Collections.singleton(start));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void corePackagesDoNotDependOnEachOtherInACycle() throws IOException {
        assertTrue(Files.isDirectory(root), "Expected to run from the core module directory");
        List<String> cycle = findCycle(graph());
        assertTrue(cycle == null, "Package cycle in core: " + (cycle == null ? "" : String.join(" -> ", cycle)));
    }
}
