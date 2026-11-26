package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeUpdateSummary;

class RuntimeUpdateSummaryTest {
    @Test
    void latestPublicationPerChannelIgnoresReleasesWithoutADate() {
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-02-01T00:00:00Z");
        Map<String, Object> summary = RuntimeUpdateSummary.of(Collections.singletonList(registration(
            new ReleaseSummary("1.0.0", UpdateChannel.STABLE, older),
            new ReleaseSummary("1.1.0", UpdateChannel.STABLE, newer),
            new ReleaseSummary("1.2.0", UpdateChannel.STABLE, null),
            new ReleaseSummary("2.0.0", UpdateChannel.BETA, null))));

        assertEquals(Collections.singletonMap("STABLE", newer.toString()), summary.get("latestPublishedAtByChannel"));
        assertEquals(1, summary.get("staleChannelCount"));
    }

    private static UpdateRegistration registration(ReleaseSummary... releases) {
        return new UpdateRegistration() {
            @Override
            public @NotNull String getRepository() {
                return "owner/name";
            }

            @Override
            public @NotNull UpdateSnapshot getSnapshot() {
                return new UpdateSnapshot("Demo", "1.0.0", null, UpdateChannel.STABLE, UpdateState.UP_TO_DATE, 21, 21, false, null,
                    null, null, null, Arrays.asList(releases), Collections.emptyList(), null, null);
            }

            @Override
            public void checkNow() {
            }

            @Override
            public void downloadNow() {
            }

            @Override
            public void close() {
            }
        };
    }
}
