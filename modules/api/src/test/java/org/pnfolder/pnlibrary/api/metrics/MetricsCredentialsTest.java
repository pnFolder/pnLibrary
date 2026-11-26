package org.pnfolder.pnlibrary.api.metrics;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsCredentials;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsCredentialsTest {
    @Test
    void genericAccessPreservesProviderValueTypes() {
        MetricsCredentials<Integer> numeric = new MetricsCredentials.BStats(42);
        MetricsCredentials<char[]> text = new MetricsCredentials.FastStats("00123".toCharArray());

        assertEquals(Integer.valueOf(43), numeric.useValue(value -> value + 1));
        assertEquals("00123", text.useValue(String::new));
        text.close();
    }

    @Test
    void tokenCopiesInputAndPreservesLeadingZeros() {
        char[] input = "00123".toCharArray();
        MetricsCredentials.FastStats credentials = new MetricsCredentials.FastStats(input);
        Arrays.fill(input, 'x');
        assertEquals("00123", credentials.useToken(token -> token));
        assertFalse(credentials.toString().contains("00123"));
    }

    @Test
    void closedTokenCannotBeReused() {
        MetricsCredentials.FastStats credentials = new MetricsCredentials.FastStats("secret".toCharArray());
        credentials.close();
        credentials.close();
        assertThrows(IllegalStateException.class, () -> credentials.useToken(token -> token));
    }

    @Test
    void legacyConfigurationTextRedactsItsToken() {
        MetricsProviderConfiguration configuration = new MetricsProviderConfiguration(MetricsProvider.FASTSTATS, true, null,
                Collections.emptyMap(), "00123");
        assertFalse(configuration.toString().contains("00123"));
        MetricsCredentials.FastStats credentials = (MetricsCredentials.FastStats) configuration.credentials();
        assertEquals("00123", credentials.useToken(token -> token));
        credentials.close();
    }

    @Test
    void credentialsRejectInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new MetricsCredentials.BStats(0));
        assertThrows(IllegalArgumentException.class, () -> new MetricsCredentials.FastStats(new char[] {' '}));
    }
}
