package org.pnfolder.pnlibrary.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which release the installer takes, with the answers of GitHub and of the repository's list written out by hand. */
class ReleaseChoiceTest {
    private static final String OWNER = BootstrapDefaults.REPOSITORY_OWNER;
    private static final String NAME = BootstrapDefaults.REPOSITORY_NAME;
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + NAME;
    private static final String LIST = "https://raw.githubusercontent.com/" + OWNER + "/" + NAME
            + "/refs/heads/main/.pnlibrary/releases.json";
    private static final String DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final Map<String, String> answers = new LinkedHashMap<>();

    private static String github(String version, boolean prerelease) {
        return github(version, prerelease, false);
    }

    private static String github(String version, boolean prerelease, boolean draft) {
        return "{\"tag_name\":\"v" + version + "\",\"prerelease\":" + prerelease + ",\"draft\":" + draft
                + ",\"assets\":[{\"name\":\"pnLibrary-" + version + "-universal.jar\",\"digest\":\"" + DIGEST
                + "\",\"browser_download_url\":\"https://github.com/o/r/releases/download/v" + version
                + "/pnLibrary-" + version + "-universal.jar\",\"size\":1000}]}";
    }

    private static String listOf(String... releases) {
        StringBuilder entries = new StringBuilder();
        for (String release : releases) {
            if (entries.length() > 0) entries.append(',');
            entries.append(release);
        }
        return "{\"schema\":1,\"product\":\"pnlibrary\",\"releases\":[" + entries + "]}";
    }

    private static final String SUM = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static String described(String version, String channel, String sum) {
        return "{\"version\":\"" + version + "\",\"channel\":\"" + channel + "\",\"installer\":{\"file\":\"pnLibrary-"
                + version + "-universal.jar\",\"url\":\"https://example.org/files/pnLibrary-" + version
                + "-universal.jar\",\"sha256\":\"" + sum + "\",\"size\":4096}}";
    }

    private static String entry(String version, String channel) {
        return "{\"version\":\"" + version + "\",\"channel\":\"" + channel + "\"}";
    }

    private LibraryRelease choose(String minimum) throws IOException {
        BootstrapOptions options = minimum == null
                ? BootstrapOptions.builder().build() : BootstrapOptions.builder(minimum).build();
        return new GitHubReleaseClient(options, (url, accept) -> {
            String answer = answers.get(url);
            if (answer == null) throw new IOException("GitHub returned HTTP 404");
            return answer;
        }).release();
    }

    @Test
    void theStableReleaseOfTheListWinsOverANewerBeta() throws IOException {
        answers.put(LIST, listOf(entry("2.0.0-beta.1", "beta"), entry("1.0.0", "stable")));
        answers.put(API + "/releases/tags/v1.0.0", github("1.0.0", false));

        LibraryRelease release = choose(null);

        assertEquals("1.0.0", release.version());
        assertTrue(release.stable());
    }

    @Test
    void aReleaseThatTheListDescribesIsTakenFromTheListWithoutAskingGitHub() throws IOException {
        answers.put(LIST, listOf(described("1.0.0", "stable", SUM)));

        LibraryRelease release = choose(null);

        assertEquals("1.0.0", release.version());
        assertEquals("pnLibrary-1.0.0-universal.jar", release.fileName());
        assertEquals("https://example.org/files/pnLibrary-1.0.0-universal.jar", release.uri().toString());
        assertEquals(SUM, release.sha256());
        assertEquals(4096L, release.size());
        assertTrue(release.stable());
    }

    @Test
    void aListedBetaWithItsFileIsTakenFromTheListToo() throws IOException {
        answers.put(LIST, listOf(described("2.0.0-beta.1", "beta", SUM)));

        LibraryRelease release = choose(null);

        assertEquals("2.0.0-beta.1", release.version());
        assertFalse(release.stable());
        assertEquals(SUM, release.sha256());
    }

    @Test
    void aBrokenChecksumInTheListMeansGitHubSuppliesTheFile() throws IOException {
        answers.put(LIST, listOf(described("1.0.0", "stable", "not-a-checksum")));
        answers.put(API + "/releases/tags/v1.0.0", github("1.0.0", false));

        LibraryRelease release = choose(null);

        assertEquals("1.0.0", release.version());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", release.sha256());
    }

    @Test
    void withoutAStableReleaseTheListGivesWhatItHas() throws IOException {
        answers.put(LIST, listOf(entry("2.0.0-alpha.1", "alpha"), entry("2.0.0-rc.1", "beta"), entry("1.9.0-dev.3", "dev")));
        answers.put(API + "/releases/tags/v2.0.0-rc.1", github("2.0.0-rc.1", true));

        LibraryRelease release = choose(null);

        assertEquals("2.0.0-rc.1", release.version());
        assertFalse(release.stable());
    }

    @Test
    void anEmptyListSendsTheInstallerToGitHubForTheStableRelease() throws IOException {
        answers.put(LIST, listOf());
        answers.put(API + "/releases?per_page=30", "[" + github("2.0.0-beta.1", true) + "," + github("1.0.0", false) + "]");

        LibraryRelease release = choose(null);

        assertEquals("1.0.0", release.version());
        assertTrue(release.stable());
    }

    @Test
    void aMissingListSendsTheInstallerToGitHubAndWithoutAStableReleaseThereTakesWhatIsThere() throws IOException {
        answers.put(API + "/releases?per_page=30",
                "[" + github("2.0.0-beta.1", true) + "," + github("2.0.0-beta.2", true) + "," + github("9.9.9", false, true) + "]");

        LibraryRelease release = choose(null);

        assertEquals("2.0.0-beta.2", release.version());
        assertFalse(release.stable());
    }

    @Test
    void aVersionOfTheListThatGitHubDoesNotHaveFallsBackToGitHubItself() throws IOException {
        answers.put(LIST, listOf(entry("3.0.0", "stable")));
        answers.put(API + "/releases?per_page=30", "[" + github("1.0.0", false) + "]");

        assertEquals("1.0.0", choose(null).version());
    }

    @Test
    void theLowestVersionOfThePluginIsRespectedEverywhere() throws IOException {
        answers.put(LIST, listOf(entry("1.0.0", "stable")));
        answers.put(API + "/releases/tags/v1.0.0", github("1.0.0", false));
        answers.put(API + "/releases?per_page=30", "[" + github("1.5.0", false) + "," + github("1.0.0", false) + "]");

        assertEquals("1.5.0", choose("1.2.0").version());
        assertEquals("1.0.0", choose("1.0.0").version());
    }

    @Test
    void whenNothingExistsTheInstallerSaysSo() {
        answers.put(LIST, listOf());
        answers.put(API + "/releases?per_page=30", "[]");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> choose(null));
        assertTrue(error.getMessage().contains("no pnLibrary release"), error.getMessage());

        IllegalStateException tooOld = assertThrows(IllegalStateException.class, () -> choose("5.0.0"));
        assertTrue(tooOld.getMessage().contains("5.0.0 or newer"), tooOld.getMessage());
    }

    @Test
    void aReleaseWithoutAVerifiedFileIsNotTaken() {
        String unverified = github("1.0.0", false).replace(DIGEST, "none");
        answers.put(API + "/releases?per_page=30", "[" + unverified + "]");

        assertThrows(IllegalStateException.class, () -> choose(null));
    }
}
