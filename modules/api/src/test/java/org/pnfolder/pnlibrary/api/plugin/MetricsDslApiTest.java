package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class MetricsDslApiTest {
    @Test
    void pluginMetricsConfigurationExposesOnlyTheSharedDslEntryPoint() {
        List<Method> methods = new ArrayList<>();
        for (Method method : PluginBuilder.class.getMethods()) {
            if (method.getName().equals("metrics")) {
                methods.add(method);
            }
        }
        assertEquals(1, methods.size());
        assertArrayEquals(new Class<?>[] {Consumer.class}, methods.get(0).getParameterTypes());
    }
}
