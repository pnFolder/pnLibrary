package ru.privatenull.pnlibrary.api.version

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticVersionTest {
    @Test
    fun `numeric prerelease identifiers compare numerically`() {
        assertTrue(SemanticVersion.parse("2.0.0-beta.10") > SemanticVersion.parse("2.0.0-beta.2"))
    }

    @Test
    fun `rejects invalid semver leading zeros and empty identifiers`() {
        assertNull(SemanticVersion.tryParse("02.0.0"))
        assertNull(SemanticVersion.tryParse("2.0.0-beta..1"))
        assertNull(SemanticVersion.tryParse("2.0.0-beta.01"))
    }

    @Test
    fun `rejects inverted range`() {
        assertThrows(IllegalArgumentException::class.java) { VersionRange.closed("3.0.0", "2.0.0") }
    }
}
