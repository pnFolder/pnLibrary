package ru.privatenull.pnlibrary.api.updates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaUpdateApiTest {
    @Test
    void buildsComponentDescriptorFromJava() {
        ComponentDescriptor descriptor = ComponentDescriptor.builder("economy", "3.4.0")
            .pnLibraryApi(1, 2)
            .managedDependency("permissions", "2.1.0", "pnFolder", "Permissions")
            .build();

        assertEquals("economy", descriptor.getId().getValue());
        assertEquals("3.4.0", descriptor.getVersion().toString());
        assertEquals(1, descriptor.getManagedDependencies().size());
    }
}
