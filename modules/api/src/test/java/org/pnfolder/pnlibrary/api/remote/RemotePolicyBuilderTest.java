package org.pnfolder.pnlibrary.api.remote;

import org.pnfolder.pnlibrary.api.remote.result.DenyAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RemotePolicyBuilderTest {
    @Test
    void builderValidatesSourceIntervalAndImmutableValues() {
        RemotePolicySettings policy = RemotePolicySettings.builder()
            .source("https://example.org/policy.java")
            .checkEvery(Duration.ofHours(2))
            .onDeny(DenyAction.DISABLE_MODULE)
            .value("  environment  ", "production")
            .build();

        assertEquals("production", policy.getValues().get("environment"));
        assertEquals(
            "file:///C:/policies/AcceptancePolicy.java",
            RemotePolicySettings.builder().source("file:///C:/policies/AcceptancePolicy.java").build().getSource());
        assertThrows(UnsupportedOperationException.class, () -> policy.getValues().put("changed", "true"));
        assertThrows(IllegalArgumentException.class,
            () -> RemotePolicySettings.builder().source("https:///missing-host.java"));
        assertThrows(IllegalArgumentException.class,
            () -> RemotePolicySettings.builder().source("file:///C:/policies/policy.txt"));
        assertThrows(IllegalArgumentException.class,
            () -> RemotePolicySettings.builder().source("file:///C:/policies/Policy.kt"));
        assertThrows(IllegalArgumentException.class,
            () -> RemotePolicySettings.builder().source("https://example.org/policy.java").checkEvery(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> RemotePolicySettings.builder().source("https://example.org/policy.java").value(" ", "invalid"));
    }
}
