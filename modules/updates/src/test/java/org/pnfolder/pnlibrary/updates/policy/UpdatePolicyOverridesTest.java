package org.pnfolder.pnlibrary.updates.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;

class UpdatePolicyOverridesTest {
    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("updates/preferences.json");
    }

    private UpdatePolicyOverrides open() {
        return new UpdatePolicyOverrides(new UpdatePreferencesStore(file()));
    }

    @Test
    void nothingIsOverriddenUntilAChoiceIsMade() {
        UpdatePolicyOverrides overrides = open();
        assertNull(overrides.channel("pnlibrary"));
        assertFalse(Files.exists(file()));
    }

    @Test
    void choicesApplyImmediatelyAndSurviveReopening() {
        UpdatePolicyOverrides overrides = open();
        overrides.setChannel("pnlibrary", UpdateChannel.BETA);
        overrides.setJavaBuild("pnlibrary", 17);

        assertEquals(UpdateChannel.BETA, overrides.channel("pnlibrary"));

        UpdatePolicyOverrides reopened = open();
        assertEquals(UpdateChannel.BETA, reopened.channel("pnlibrary"));
        assertEquals(Integer.valueOf(17), reopened.javaBuild("pnlibrary"));
        assertNull(reopened.channel("shop"));
    }

    @Test
    void aLaterChoiceReplacesTheEarlierOne() {
        UpdatePolicyOverrides overrides = open();
        overrides.setChannel("pnlibrary", UpdateChannel.RC);
        overrides.setChannel("pnlibrary", UpdateChannel.STABLE);

        assertEquals(UpdateChannel.STABLE, overrides.channel("pnlibrary"));
    }

    @Test
    void removingAChannelChoiceHandsTheDecisionBackToThePluginAndSurvivesReopening() {
        UpdatePolicyOverrides overrides = open();
        overrides.setChannel("shop", UpdateChannel.BETA);
        overrides.setChannel("pnlibrary", UpdateChannel.RC);

        overrides.setChannel("shop", null);

        assertNull(overrides.channel("shop"));
        UpdatePolicyOverrides reopened = open();
        assertNull(reopened.channel("shop"));
        assertEquals(UpdateChannel.RC, reopened.channel("pnlibrary"));
    }

    @Test
    void unreadableOrInvalidContentIsIgnoredWithAWarning() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), "{ not json".getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<>();
        assertNull(new UpdatePolicyOverrides(new UpdatePreferencesStore(file(), warnings::add)).channel("pnlibrary"));
        assertEquals(1, warnings.size());

        Files.write(file(), "{\"channels\":{\"pnlibrary\":\"nightly\",\"shop\":\"BETA\"},\"automatic\":{}}"
            .getBytes(StandardCharsets.UTF_8));
        UpdatePolicyOverrides tolerant = open();
        assertNull(tolerant.channel("pnlibrary"));
        assertEquals(UpdateChannel.BETA, tolerant.channel("shop"));
    }
}
