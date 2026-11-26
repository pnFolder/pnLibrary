package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeConfigurationDetails;

class RuntimeConfigurationDetailsTest {
    @Test
    void configurationDetailsExposeCountsWithoutSensitiveValues() {
        PnLibraryConfig config = PnLibraryConfig.builder()
            .secretKeyPatterns(Arrays.asList("token"))
            .redactValuePatterns(Arrays.asList("password"))
            .build();

        Map<String, Object> details = RuntimeConfigurationDetails.of(config);

        assertEquals(2, details.get("redactionRuleCount"));
        assertEquals(false, details.containsKey("token"));
        assertEquals(false, details.containsKey("password"));
    }
}
