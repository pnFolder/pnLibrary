package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Conventions every production source in the repository follows, checked on the source text so they hold for
 * all modules at once: explicit imports, one message style, and layers that never reach sideways or upwards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceConventionsTest {
    private static final List<String> BUILD_DIRECTORIES = Arrays.asList("/build/", "/.gradle/", "/out/");
    private static final Pattern PUBLIC_BUILDER_CONSTRUCTOR =
        Pattern.compile("\\bpublic\\s+\\w*Builder\\s*\\(");
    private static final List<String> REFLECTION =
        Arrays.asList("Class.forName(", ".getMethod(", ".getDeclaredField(", ".getDeclaredMethod(", ".invoke(");

    /**
     * Files whose job is the legacy text format itself: parsers and serializers, the redactor that strips codes
     * from reports, and docs describing accepted input. Example plugins are left out: they show plain Bukkit use.
     */
    private static final Set<String> LEGACY_TEXT_ALLOWED = new HashSet<>(Arrays.asList(
        "modules/api/src/main/java/org/pnfolder/pnlibrary/api/text/serializer/ComponentSerializerType.java",
        "modules/core/src/main/java/org/pnfolder/pnlibrary/core/text/CachedComponents.java",
        "modules/core/src/main/java/org/pnfolder/pnlibrary/core/text/LegacyToMini.java",
        "modules/core/src/main/java/org/pnfolder/pnlibrary/core/diagnostics/redaction/DiagnosticRedactor.java",
        "modules/core/src/main/java/org/pnfolder/pnlibrary/core/menu/style/MenuColors.java",
        "modules/features/currency-api/src/main/java/org/pnfolder/pnlibrary/api/currency/CurrencyCommandSettings.java",
        "platforms/bukkit/runtime/src/main/java/org/pnfolder/pnlibrary/bukkit/item/ItemText.java",
        "platforms/bukkit/runtime/src/main/java/org/pnfolder/pnlibrary/bukkit/menu/HexColors.java",
        "platforms/bukkit/runtime/src/main/java/org/pnfolder/pnlibrary/bukkit/menu/PluginsMenu.java"));

    /** Frozen binary API: the constructor was Kotlin `internal`, which the JVM exposes as public. */
    private static final Set<String> PUBLIC_BUILDER_CONSTRUCTOR_ALLOWED = new HashSet<>(Arrays.asList(
        "modules/api/src/main/java/org/pnfolder/pnlibrary/api/events/listener/EventListenerBuilder.java"));

    private Path root;
    private List<Path> productionSources;
    private List<String> publicApiModules;

    @BeforeAll
    void collectSources() throws IOException {
        root = ProjectRoot.find();
        productionSources = new ArrayList<>();
        for (String group : Arrays.asList("modules", "platforms")) {
            try (Stream<Path> paths = Files.walk(root.resolve(group))) {
                productionSources.addAll(paths
                    .filter(path -> Files.isRegularFile(path)
                        && (path.toString().endsWith(".java") || path.toString().endsWith(".java")))
                    .filter(path -> has(path, "src/main") && BUILD_DIRECTORIES.stream().noneMatch(it -> has(path, it)))
                    .collect(Collectors.toList()));
            }
        }
        publicApiModules = new ArrayList<>();
        for (String line : Files.readAllLines(root.resolve("gradle/public-modules.properties"))) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                publicApiModules.add(trimmed.substring(0, trimmed.indexOf('=') < 0 ? trimmed.length() : trimmed.indexOf('=')) + "/");
            }
        }
    }

    private List<Path> sources() {
        return productionSources.stream().filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
    }

    private List<Path> publicApiSources() {
        return sources().stream().filter(file -> publicApiModules.stream().anyMatch(it -> has(file, it)))
            .collect(Collectors.toList());
    }

    @Test
    @DisplayName("production code imports every name explicitly")
    void productionCodeImportsEveryNameExplicitly() throws IOException {
        List<Path> offenders = new ArrayList<>();
        for (Path file : sources()) {
            for (String line : lines(file)) {
                if (line.startsWith("import ") && line.trim().endsWith(".*")) {
                    offenders.add(file);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Wildcard imports in: " + relative(offenders));
    }

    @Test
    @DisplayName("chat colors come from the palette, never from legacy section codes")
    void chatColorsComeFromThePaletteNeverFromLegacySectionCodes() throws IOException {
        List<Path> offenders = new ArrayList<>();
        for (Path file : sources()) {
            if (LEGACY_TEXT_ALLOWED.contains(relative(file))) {
                continue;
            }
            for (String line : lines(file)) {
                if (line.indexOf('§') >= 0) {
                    offenders.add(file);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Legacy § color codes outside the allowed adapters in: " + relative(offenders));
    }

    @Test
    @DisplayName("the runtime core never depends on a platform")
    void theRuntimeCoreNeverDependsOnAPlatform() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : productionSources) {
            if (has(file, "modules/core/")) {
                String found = platformReferences(file, PlatformSourceReferences.allPackages());
                if (found != null) {
                    offenders.add(found);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "core references platform code in: " + offenders);
    }

    @Test
    @DisplayName("platform runtimes never depend on each other")
    void platformRuntimesNeverDependOnEachOther() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, List<String>> platform : PlatformSourceReferences.PACKAGES.entrySet()) {
            List<String> forbidden = new ArrayList<>();
            for (Map.Entry<String, List<String>> other : PlatformSourceReferences.PACKAGES.entrySet()) {
                if (!other.getKey().equals(platform.getKey())) {
                    forbidden.addAll(other.getValue());
                }
            }
            for (Path file : productionSources) {
                if (has(file, "platforms/" + platform.getKey() + "/")) {
                    String found = platformReferences(file, forbidden);
                    if (found != null) {
                        offenders.add(found);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "platform runtimes reference each other in: " + offenders);
    }

    @Test
    @DisplayName("public builders are created through builder(), never a public constructor")
    void publicBuildersAreCreatedThroughBuilderNeverAPublicConstructor() throws IOException {
        List<Path> offenders = new ArrayList<>();
        for (Path file : publicApiSources()) {
            if (PUBLIC_BUILDER_CONSTRUCTOR_ALLOWED.contains(relative(file))) {
                continue;
            }
            for (String line : lines(file)) {
                if (PUBLIC_BUILDER_CONSTRUCTOR.matcher(line).find()) {
                    offenders.add(file);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Builders with a public constructor in: " + relative(offenders));
    }

    @Test
    @DisplayName("public API modules hold contracts, not reflective platform code")
    void publicApiModulesHoldContractsNotReflectivePlatformCode() throws IOException {
        // A self-contained feature module keeps its implementation in an `internal` package; that is not API.
        List<Path> offenders = new ArrayList<>();
        for (Path file : publicApiSources()) {
            if (has(file, "/internal/")) {
                continue;
            }
            for (String line : lines(file)) {
                if (REFLECTION.stream().anyMatch(line::contains)) {
                    offenders.add(file);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Reflection belongs in a runtime module, found in: " + relative(offenders));
    }

    private String platformReferences(Path file, List<String> forbidden) throws IOException {
        Set<String> references = PlatformSourceReferences.find(
            new String(Files.readAllBytes(file), StandardCharsets.UTF_8), forbidden);
        return references.isEmpty() ? null : relative(file) + ": " + references;
    }

    private static boolean has(Path path, String fragment) {
        return path.toString().replace('\\', '/').contains(fragment);
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file);
    }

    private String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private List<String> relative(List<Path> files) {
        return files.stream().map(this::relative).collect(Collectors.toList());
    }
}
