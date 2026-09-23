package ru.privatenull.pnlibrary.api.updates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaUpdateApiTest {
    @Test
    void buildsProductDescriptorFromJava() {
        ProductDescriptor descriptor = ProductDescriptor.builder("economy", "3.4.0")
            .pnLibraryApi(1, 2)
            .build();

        assertEquals("economy", descriptor.getId().getValue());
        assertEquals("3.4.0", descriptor.getVersion().toString());
    }
}
