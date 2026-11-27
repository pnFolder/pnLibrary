package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleDefinitionBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.Dependencies;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

class ModuleProductResolverTest {
    private final Object owner = new Object();
    private final ModuleId id = ModuleId.of("shop");

    private ModuleProductResolver resolver(String version) {
        PlatformOwnership platform = mock(PlatformOwnership.class);
        when(platform.ownerDetails(owner)).thenReturn(
            version == null ? Collections.emptyMap() : Collections.singletonMap("version", version));
        return new ModuleProductResolver(platform);
    }

    private static ModuleDefinitionBuilder withVault() {
        ModuleDefinitionBuilder definition = new ModuleDefinitionBuilder();
        definition.depends(Dependencies.plugin("Vault", "1.7.0", "https://example.org"));
        return definition;
    }

    @Test
    void aModuleWithNoUpdatesProductOrDependenciesHasNoDescriptor() {
        assertNull(resolver("1.0.0").resolve(owner, id, new ModuleDefinitionBuilder()));
    }

    @Test
    void dependenciesAloneGiveADescriptorWithThePluginsVersion() {
        ProductDescriptor descriptor = resolver("2.3.4").resolve(owner, id, withVault());

        assertEquals("shop", descriptor.getId().getValue());
        assertEquals("2.3.4", descriptor.getVersion().toString());
    }

    @Test
    void theVersionOfTheDefinitionIsUsedWhenThePlatformHasNone() {
        ModuleDefinitionBuilder definition = withVault();
        definition.setMetadataVersion("0.9.0");

        assertEquals("0.9.0", resolver(null).resolve(owner, id, definition).getVersion().toString());
    }

    @Test
    void noVersionAnywhereIsAnErrorThatNamesTheModule() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> resolver(null).resolve(owner, id, withVault()));

        assertEquals(true, error.getMessage().contains("shop"));
    }
}
