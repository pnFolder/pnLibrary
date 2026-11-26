package org.pnfolder.pnlibrary.api.metrics;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.security.SecretValue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretValueTest {
    @Test
    void temporarySecretIsErasedAfterUseWithoutChangingOwnedValue() {
        SecretValue secret = new SecretValue("password".toCharArray());
        char[][] borrowed = new char[1][];
        secret.useSecret(value -> {
            borrowed[0] = value;
            assertEquals("password", new String(value));
            value[0] = 'x';
            return null;
        });
        assertArrayEquals(new char[8], borrowed[0]);
        assertEquals("password", secret.useSecret(String::new));
        secret.close();
    }

    @Test
    void temporarySecretIsErasedWhenCallbackThrows() {
        SecretValue secret = new SecretValue("token".toCharArray());
        char[][] borrowed = new char[1][];
        assertThrows(IllegalStateException.class, () -> secret.useSecret(value -> {
            borrowed[0] = value;
            throw new IllegalStateException("consumer failed");
        }));
        assertArrayEquals(new char[5], borrowed[0]);
        secret.close();
        assertThrows(IllegalStateException.class, () -> secret.useSecret(value -> value.length));
    }
}
