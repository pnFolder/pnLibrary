package org.pnfolder.pnlibrary.api.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

class ProductModelTest {
    @Test
    void productIdIsNormalized() {
        assertEquals("pnauth", ProductId.of(" PnAuth ").getValue());
    }

    @Test
    void productIdRejectsUnsafeInput() {
        assertThrows(IllegalArgumentException.class, () -> ProductId.of("../auth"));
    }

    @Test
    void updateRequestDerivesNoProductIdentityFromRepository() {
        PluginUpdateRequest request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnAuth")
            .supportedApi(1, 2)
            .artifact("(?i)^pnauth-.*\\.jar$", 8)
            .build();

        assertEquals("pnAuth", request.getRepositoryName());
        boolean hasComponent = false;
        for (Method method : PluginUpdateRequest.class.getMethods()) {
            hasComponent |= method.getName().equals("getComponent");
        }
        assertFalse(hasComponent);
    }
}
