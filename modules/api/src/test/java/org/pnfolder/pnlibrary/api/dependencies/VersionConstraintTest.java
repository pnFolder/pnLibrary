package org.pnfolder.pnlibrary.api.dependencies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class VersionConstraintTest {
    private static SemanticVersion v(String text) {
        return SemanticVersion.parse(text);
    }

    @Test
    void theMinimumIsIncludedAndOlderVersionsAreRefused() {
        VersionConstraint constraint = new VersionConstraint(v("1.7.0"));

        assertTrue(constraint.accepts(v("1.7.0")));
        assertTrue(constraint.accepts(v("9.0.0")));
        assertFalse(constraint.accepts(v("1.6.9")));
    }

    @Test
    void anInclusiveMaximumKeepsItselfAndAnExclusiveOneDoesNot() {
        assertTrue(new VersionConstraint(v("1.0.0"), v("2.0.0"), null).accepts(v("2.0.0")));
        assertFalse(new VersionConstraint(v("1.0.0"), null, v("2.0.0")).accepts(v("2.0.0")));
        assertTrue(new VersionConstraint(v("1.0.0"), null, v("2.0.0")).accepts(v("1.9.9")));
    }

    @Test
    void contradictingBoundsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new VersionConstraint(v("1.0.0"), v("2.0.0"), v("3.0.0")));
        assertThrows(IllegalArgumentException.class, () -> new VersionConstraint(v("2.0.0"), v("1.0.0"), null));
        assertThrows(IllegalArgumentException.class, () -> new VersionConstraint(v("2.0.0"), null, v("2.0.0")));
    }
}
