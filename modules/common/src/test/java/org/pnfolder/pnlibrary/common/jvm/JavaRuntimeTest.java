package org.pnfolder.pnlibrary.common.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaRuntimeTest {
    @Test
    void parsesLegacyAndModernSpecificationVersions() {
        assertEquals(8, JavaRuntime.parseFeature("1.8"));
        assertEquals(17, JavaRuntime.parseFeature("17"));
        assertEquals(21, JavaRuntime.parseFeature("21.0.1"));
        assertEquals(25, JavaRuntime.parseFeature("25-ea"));
        assertEquals(8, JavaRuntime.parseFeature("unknown"));
        assertEquals(8, JavaRuntime.parseFeature(null));
    }
}
