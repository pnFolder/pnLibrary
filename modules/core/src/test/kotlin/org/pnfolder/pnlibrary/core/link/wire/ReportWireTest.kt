package org.pnfolder.pnlibrary.core.link.wire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin
import org.pnfolder.pnlibrary.core.link.report.LinkReport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant

class ReportWireTest {
    private fun roundTrip(report: LinkReport, limit: Int): LinkReport {
        val bytes = ByteArrayOutputStream()
        ReportWire.write(DataOutputStream(bytes), report, limit)
        return ReportWire.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }

    @Test
    fun `a report survives the wire and a missing latest version stays null`() {
        val report = LinkReport("paper", "1.2", 7, listOf(LinkPlugin("shop", "1.0", null, "ok")), Instant.ofEpochMilli(1234))

        val back = roundTrip(report, 10)

        assertEquals(report, back)
        assertNull(back.plugins.single().latest)
    }

    @Test
    fun `only the first plugins up to the limit are sent`() {
        val plugins = (1..5).map { LinkPlugin("p$it", "1", "2", "ok") }

        assertEquals(2, roundTrip(LinkReport("paper", "1", 0, plugins, Instant.EPOCH), 2).plugins.size)
    }
}
