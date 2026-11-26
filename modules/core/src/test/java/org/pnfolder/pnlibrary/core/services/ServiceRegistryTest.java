package org.pnfolder.pnlibrary.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;

class ServiceRegistryTest {
    private interface GreetingService {
        String value();
    }

    private static final class Greeting implements GreetingService {
        private final String value;

        Greeting(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    @Test
    void serviceManagerDirectlyRegistersAndUnregistersAService() {
        try (ServiceRegistry services = new ServiceRegistry()) {
            services.register(GreetingService.class, new Greeting("hello"));
            assertEquals("hello", services.require(GreetingService.class).value());

            services.unregister(GreetingService.class);

            assertNull(services.get(GreetingService.class));
        }
    }

    @Test
    void highestNumericPriorityWinsAndAllProvidersRemainAvailable() {
        try (ServiceRegistry services = new ServiceRegistry()) {
            services.register(PluginId.of("fallback"), GreetingService.class, new Greeting("fallback"), -100);
            PluginId preferredOwner = PluginId.of("preferred");
            services.register(preferredOwner, GreetingService.class, new Greeting("preferred"), 500);

            assertEquals("preferred", services.require(GreetingService.class).value());
            List<String> values = new ArrayList<>();
            for (GreetingService service : services.all(GreetingService.class)) {
                values.add(service.value());
            }
            assertEquals(Arrays.asList("preferred", "fallback"), values);

            services.unregister(preferredOwner, GreetingService.class);
            assertEquals("fallback", services.require(GreetingService.class).value());
        }
    }

    @Test
    void allServicesOwnedByAPluginAreRemovedTogether() {
        try (ServiceRegistry services = new ServiceRegistry()) {
            ServiceScope owned = services.ownedBy(PluginId.of("example"));
            owned.register(GreetingService.class, new Greeting("hello"));
            assertEquals("hello", services.get(GreetingService.class).value());

            owned.close();

            assertNull(services.get(GreetingService.class));
            assertThrows(IllegalStateException.class, () -> owned.register(GreetingService.class, new Greeting("late")));
        }
    }

    @Test
    void oneOwnerCannotAccidentallyPublishTheSameContractTwice() {
        try (ServiceRegistry services = new ServiceRegistry()) {
            PluginId owner = PluginId.of("example");
            services.register(owner, GreetingService.class, new Greeting("first"));

            assertThrows(IllegalStateException.class,
                () -> services.register(owner, GreetingService.class, new Greeting("duplicate")));
        }
    }

    @Test
    void theRegistryListsAServiceByItsTypeNameWithItsOwner() {
        try (ServiceRegistry services = new ServiceRegistry()) {
            services.register(CharSequence.class, "hello");

            List<RegistryEntry<Object>> entries = services.registry().entries();

            assertEquals(1, entries.size());
            assertEquals("java.lang.CharSequence", entries.get(0).getKey());
            assertEquals("pnLibrary", entries.get(0).getOwner());
        }
    }
}
