package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeUpdateSummary
import java.time.Instant

class RuntimeUpdateSummaryTest {
    @Test
    fun `latest publication per channel ignores releases without a date`() {
        val older = Instant.parse("2026-01-01T00:00:00Z")
        val newer = Instant.parse("2026-02-01T00:00:00Z")
        val summary = RuntimeUpdateSummary.of(
            listOf(
                registration(
                    ReleaseSummary("1.0.0", UpdateChannel.STABLE, older),
                    ReleaseSummary("1.1.0", UpdateChannel.STABLE, newer),
                    ReleaseSummary("1.2.0", UpdateChannel.STABLE, null),
                    ReleaseSummary("2.0.0", UpdateChannel.BETA, null),
                ),
            ),
        )

        assertEquals(mapOf("STABLE" to newer.toString()), summary["latestPublishedAtByChannel"])
        assertEquals(1, summary["staleChannelCount"])
    }

    private fun registration(vararg releases: ReleaseSummary): UpdateRegistration = object : UpdateRegistration {
        override val repository = "owner/name"
        override val snapshot = UpdateSnapshot(
            product = "Demo",
            currentVersion = "1.0.0",
            latestVersion = null,
            channel = UpdateChannel.STABLE,
            state = UpdateState.UP_TO_DATE,
            currentJava = 21,
            requiredJava = 21,
            automaticDownload = false,
            releaseUrl = null,
            message = null,
            availableReleases = releases.toList(),
        )

        override fun checkNow() = Unit
        override fun downloadNow() = Unit
        override fun close() = Unit
    }
}
