package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.Dependencies;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

class ModuleDependencyValidatorTest {
    private final PluginDependency vault = Dependencies.plugin("Vault", "1.7.0", "https://example.org/vault");

    private ModuleDependencyValidator validator(Map<String, String> installed, boolean downloads) {
        PlatformOwnership platform = mock(PlatformOwnership.class);
        when(platform.installedPlugins()).thenReturn(installed);
        return new ModuleDependencyValidator(platform, () -> downloads);
    }

    private void validate(ModuleDependencyValidator validator, PluginDependency dependency) {
        validator.validate(Collections.singletonList(dependency), Collections.<String, ProductDescriptor>emptyMap());
    }

    @Test
    void anInstalledPluginOfAHighEnoughVersionPassesNamesIgnoreCase() {
        assertDoesNotThrow(() -> validate(validator(Collections.singletonMap("vault", "1.7.3"), false), vault));
    }

    @Test
    void aMissingPluginNamesItsDownloadPage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> validate(validator(Collections.<String, String>emptyMap(), false), vault));

        assertTrue(error.getMessage().contains("Vault"));
        assertTrue(error.getMessage().contains("https://example.org/vault"));
    }

    @Test
    void aTooOldPluginIsReportedWithBothVersions() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> validate(validator(Collections.singletonMap("Vault", "1.5.0"), false), vault));

        assertTrue(error.getMessage().contains("1.5.0"));
        assertTrue(error.getMessage().contains("1.7.0"));
    }

    @Test
    void anOptionalPluginNeverBlocks() {
        PluginDependency optional = Dependencies.plugin("Vault", "1.7.0", "https://example.org/vault", false);

        assertDoesNotThrow(() -> validate(validator(Collections.<String, String>emptyMap(), false), optional));
    }

    @Test
    void aMissingPluginThatCanBeDownloadedAutomaticallyDoesNotBlock() {
        PluginDependency automatic = Dependencies.plugin("Vault", "1.7.0", "https://example.org/Vault.jar", repeat('a', 64));

        assertDoesNotThrow(() -> validate(validator(Collections.<String, String>emptyMap(), true), automatic));
        assertThrows(IllegalArgumentException.class,
            () -> validate(validator(Collections.<String, String>emptyMap(), false), automatic));
    }

    private static String repeat(char letter, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(letter);
        }
        return text.toString();
    }
}
