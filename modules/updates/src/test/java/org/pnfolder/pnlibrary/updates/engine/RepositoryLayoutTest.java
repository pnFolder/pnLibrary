package org.pnfolder.pnlibrary.updates.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogArtifact;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogCodec;

/** The {@code .pnlibrary} folder of this repository follows the rules in {@code docs/repository.md}. */
class RepositoryLayoutTest {
    private static final Pattern UNIVERSAL_JAR = Pattern.compile("(?i)^pnLibrary-[0-9][^/\\\\]*-universal\\.jar$");

    private final Path folder = findFolder();

    /** The real catalog of pnLibrary and the filled example for plugin authors in {@code integration/template/.pnlibrary}. */
    private static final class Place {
        private final Path file;
        private final String product;
        private final String linkPrefix;

        Place(Path file, String product, String linkPrefix) {
            this.file = file;
            this.product = product;
            this.linkPrefix = linkPrefix;
        }
    }

    private final Map<String, Place> places = places();

    private static Path findFolder() {
        for (Path path = Paths.get("").toAbsolutePath(); path != null; path = path.getParent()) {
            Path candidate = path.resolve(".pnlibrary");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("The .pnlibrary folder was not found above " + Paths.get("").toAbsolutePath());
    }

    private Map<String, Place> places() {
        Map<String, Place> result = new LinkedHashMap<>();
        result.put("releases.json", new Place(folder.resolve("releases.json"), "pnlibrary", "/.pnlibrary/changelogs/"));
        result.put("template releases.json", new Place(
            folder.resolveSibling("integration").resolve("template").resolve(".pnlibrary").resolve("releases.json"),
            "myplugin", "/.pnlibrary/changelogs/"));
        return result;
    }

    private Path changelogs(String name) {
        return places.get(name).file.resolveSibling("changelogs");
    }

    private ReleaseCatalog catalog(String name) throws IOException {
        Path file = places.get(name).file;
        assertTrue(Files.isRegularFile(file), name + " is missing");
        return new ReleaseCatalogCodec().decode(Files.readAllBytes(file));
    }

    @Test
    @DisplayName("releases json and the template example are valid catalogs of their product")
    void releasesJsonAndTheTemplateExampleAreValidCatalogsOfTheirProduct() throws IOException {
        for (Map.Entry<String, Place> entry : places.entrySet()) {
            assertEquals(entry.getValue().product, catalog(entry.getKey()).getProduct(), entry.getKey());
        }
    }

    @Test
    @DisplayName("every listed version has a changelog file named after it")
    void everyListedVersionHasAChangelogFileNamedAfterIt() throws IOException {
        for (String name : places.keySet()) {
            for (CatalogRelease release : catalog(name).getReleases()) {
                Path changelog = changelogs(name).resolve(release.getVersion() + ".md");
                assertTrue(Files.isRegularFile(changelog),
                    name + " lists " + release.getVersion() + " but " + changelog.getFileName() + " is missing");
            }
        }
    }

    @Test
    @DisplayName("every universal artifact names a universal jar and carries a checksum")
    void everyUniversalArtifactNamesAUniversalJarAndCarriesAChecksum() throws IOException {
        for (CatalogRelease release : catalog("releases.json").getReleases()) {
            for (CatalogArtifact artifact : release.getArtifacts()) {
                if (artifact.getPlatform() != PlatformType.UNIVERSAL) {
                    continue;
                }
                assertTrue(UNIVERSAL_JAR.matcher(artifact.getFile()).find(),
                    "the universal artifact of " + release.getVersion() + " must be named a universal jar");
                assertNotNull(artifact.getSha256(), "the universal artifact of " + release.getVersion() + " has no sha256");
            }
        }
    }

    @Test
    @DisplayName("a changelog link points at the file of its own version")
    void aChangelogLinkPointsAtTheFileOfItsOwnVersion() throws IOException {
        for (Map.Entry<String, Place> entry : places.entrySet()) {
            for (CatalogRelease release : catalog(entry.getKey()).getReleases()) {
                URI link = release.getChangelogUrl();
                if (link == null) {
                    continue;
                }
                assertNotNull(link.getPath());
                assertTrue(link.getPath().endsWith(entry.getValue().linkPrefix + release.getVersion() + ".md"),
                    entry.getKey() + ": the changelog link of " + release.getVersion() + " is " + link);
            }
        }
    }

    @Test
    @DisplayName("the channel of a release matches the word in its version")
    void theChannelOfAReleaseMatchesTheWordInItsVersion() throws IOException {
        for (String name : places.keySet()) {
            for (CatalogRelease release : catalog(name).getReleases()) {
                String word = release.getVersion().getPrerelease().isEmpty() ? null : release.getVersion().getPrerelease().get(0);
                assertEquals(release.getChannel().name().toLowerCase(Locale.ROOT), word,
                    name + ": " + release.getVersion() + " is listed in channel " + release.getChannel());
            }
        }
    }
}
