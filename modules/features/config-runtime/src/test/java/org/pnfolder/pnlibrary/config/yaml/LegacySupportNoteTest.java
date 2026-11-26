package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class LegacySupportNoteTest {
    private final List<String> note = Arrays.asList(
            "# Если у вас возникли проблемы с плагином, пишите нам: мы всегда рады помочь.",
            "# If you have any problem with a plugin, write to us: we are always happy to help.",
            "# Discord: https://discord.gg/old",
            "# https://example.org/second-link");

    @Test
    void theNoteIsRemovedTogetherWithAllItsLinkLinesAndNothingElse() {
        List<String> lines = new ArrayList<String>();
        lines.add("_config-version: 1");
        lines.addAll(note);
        lines.add("retries: 4");
        lines.add("");
        assertEquals("_config-version: 1\nretries: 4\n", LegacySupportNote.removeFrom(String.join("\n", lines)));
    }

    @Test
    void contentWithoutANoteIsReturnedUnchanged() {
        String content = "# my note\n# https://example.org/docs\nretries: 4\n";
        assertEquals(content, LegacySupportNote.removeFrom(content));
    }
}
