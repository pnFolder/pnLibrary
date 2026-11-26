package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginRegistrationApiTest {
    @Test
    void registryExposesOnlyPhysicalPluginRegistration() {
        List<Method> registrations = new ArrayList<>();
        for (Method method : PluginRegistry.class.getDeclaredMethods()) {
            if (method.getName().equals("register")) {
                registrations.add(method);
            }
            assertFalse(method.getName().equals("modules") || method.getName().equals("unregisterOwner"));
        }

        assertEquals(1, registrations.size());
        assertEquals(PluginRegistration.class, registrations.get(0).getReturnType());
        assertEquals(1, registrations.get(0).getParameterTypes().length);
        assertEquals(Object.class, registrations.get(0).getParameterTypes()[0]);
    }

    @Test
    void pluginRegistrationOwnsLogicalModulesButNotModuleCapabilities() {
        Set<String> methods = new HashSet<>();
        for (Method method : PluginRegistration.class.getMethods()) {
            methods.add(method.getName());
        }

        assertTrue(methods.contains("registerModule"));
        assertTrue(methods.contains("getModule"));
        assertTrue(methods.contains("unregisterModule"));
        assertTrue(methods.contains("all"));
        assertFalse(methods.contains("getEvents"));
        assertFalse(methods.contains("getTasks"));
    }
}
