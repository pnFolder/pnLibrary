package org.pnfolder.pnlibrary.api.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticVersionTest {
    @Test
    fun `numeric prerelease comparison supports values beyond integer limits`() {
        val lower = SemanticVersion.parse("2.0.0-beta.999999999999999999999")
        val higher = SemanticVersion.parse("2.0.0-beta.1000000000000000000000")
        assertTrue(lower < higher)
        assertTrue(higher > lower)
        assertTrue(higher < SemanticVersion.parse("2.0.0-beta.alpha"))
        assertEquals(0, higher.compareTo(SemanticVersion.parse(higher.toString())))
    }

    @Test
    fun `prerelease identifiers cannot be mutated through the public value`() {
        val version = SemanticVersion.parse("2.0.0-beta.1")
        assertThrows(UnsupportedOperationException::class.java) {
            (version.prerelease as MutableList<String>)[0] = "alpha"
        }
        assertEquals("2.0.0-beta.1", version.toString())
    }

    @Test
    fun `numeric prerelease identifiers compare numerically`() {
        assertTrue(SemanticVersion.parse("2.0.0-beta.10") > SemanticVersion.parse("2.0.0-beta.2"))
    }

    @Test
    fun `channel suffixes order from dev to stable and a bare release is newest`() {
        val order = listOf("dev.9", "alpha.1", "beta.1", "rc.1", "stable.1", "stable.2").map { SemanticVersion.parse("0.1.0-$it") }
        assertEquals(order, order.shuffled().sorted())
        assertTrue(SemanticVersion.parse("0.1.0") > order.last())
        assertTrue(SemanticVersion.parse("0.1.1-dev.1") > order.last())
    }

    @Test
    fun `rejects invalid semver leading zeros and empty identifiers`() {
        assertNull(SemanticVersion.tryParse("02.0.0"))
        assertNull(SemanticVersion.tryParse("2.0.0-beta..1"))
        assertNull(SemanticVersion.tryParse("2.0.0-beta.01"))
        assertNull(SemanticVersion.tryParse("2.0.0+!"))
    }

    @Test
    fun `compares alphanumeric prerelease identifiers case sensitively`() {
        assertTrue(SemanticVersion.parse("1.0.0-BETA") < SemanticVersion.parse("1.0.0-beta"))
    }

    @Test
    fun `validates and ignores build metadata for precedence`() {
        assertEquals(
            SemanticVersion.parse("1.2.3+linux.x64"),
            SemanticVersion.parse("1.2.3+windows.x64"),
        )
    }

    @Test
    fun `rejects inverted range`() {
        assertThrows(IllegalArgumentException::class.java) { VersionRange.closed("3.0.0", "2.0.0") }
    }
}
