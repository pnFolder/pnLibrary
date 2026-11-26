package org.pnfolder.pnlibrary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

class BuiltInConfigSerializersTest {
    private final Map<Class<?>, ConfigSerializer<?>> serializers = BuiltInConfigSerializers.create();

    @Test
    void durationSerializerAcceptsCompactUnitsAndIsoNotation() {
        ConfigSerializer<Duration> serializer = serializer(Duration.class);

        assertEquals(Duration.ofMillis(250), serializer.deserialize("250ms", context(Duration.class)));
        assertEquals(Duration.ofMinutes(5), serializer.deserialize("5m", context(Duration.class)));
        assertEquals(Duration.ofHours(2), serializer.deserialize("PT2H", context(Duration.class)));
    }

    @Test
    void localeSerializerUsesLanguageTagsInBothDirections() {
        ConfigSerializer<Locale> serializer = serializer(Locale.class);
        Locale locale = Locale.forLanguageTag("pt-BR");

        assertEquals("pt-BR", serializer.serialize(locale, context(Locale.class)));
        assertEquals(locale, serializer.deserialize("pt-BR", context(Locale.class)));
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigSerializer<T> serializer(Class<T> type) {
        return (ConfigSerializer<T>) serializers.get(type);
    }

    private static ConfigSerializationContext context(Class<?> type) {
        return new ConfigSerializationContext("value", type, type, Collections.<Annotation>emptyList(), null);
    }
}
