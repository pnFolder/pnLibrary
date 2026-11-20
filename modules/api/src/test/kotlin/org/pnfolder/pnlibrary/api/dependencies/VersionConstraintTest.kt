package org.pnfolder.pnlibrary.api.dependencies

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.version.SemanticVersion

class VersionConstraintTest {
    private fun v(text: String) = SemanticVersion.parse(text)

    @Test
    fun `the minimum is included and older versions are refused`() {
        val constraint = VersionConstraint(v("1.7.0"))

        assertTrue(constraint.accepts(v("1.7.0")))
        assertTrue(constraint.accepts(v("9.0.0")))
        assertFalse(constraint.accepts(v("1.6.9")))
    }

    @Test
    fun `an inclusive maximum keeps itself and an exclusive one does not`() {
        assertTrue(VersionConstraint(v("1.0.0"), maximumInclusive = v("2.0.0")).accepts(v("2.0.0")))
        assertFalse(VersionConstraint(v("1.0.0"), maximumExclusive = v("2.0.0")).accepts(v("2.0.0")))
        assertTrue(VersionConstraint(v("1.0.0"), maximumExclusive = v("2.0.0")).accepts(v("1.9.9")))
    }

    @Test
    fun `contradicting bounds are refused`() {
        assertThrows(IllegalArgumentException::class.java) { VersionConstraint(v("1.0.0"), v("2.0.0"), v("3.0.0")) }
        assertThrows(IllegalArgumentException::class.java) { VersionConstraint(v("2.0.0"), maximumInclusive = v("1.0.0")) }
        assertThrows(IllegalArgumentException::class.java) { VersionConstraint(v("2.0.0"), maximumExclusive = v("2.0.0")) }
    }
}
