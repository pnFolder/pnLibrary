package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

class ConfigSerializerResolverTest {
    private static int instances;

    @Test
    void annotationSerializerTakesPrecedenceAndIsReused() {
        instances = 0;
        Map<Class<?>, ConfigSerializer<?>> registered =
                Collections.<Class<?>, ConfigSerializer<?>>singletonMap(AnnotatedValue.class, new TextSerializer("registered"));
        ConfigSerializerResolver resolver = new ConfigSerializerResolver(registered);

        ConfigSerializer<Object> first = resolver.forType(AnnotatedValue.class);
        ConfigSerializer<Object> second = resolver.forType(AnnotatedValue.class);

        assertEquals(1, instances);
        assertSame(first, second);
        assertEquals("annotation", first.serialize(new AnnotatedValue("value"), context()));
    }

    @Test
    void fieldAnnotationOverridesTheSerializerSelectedForItsType() throws NoSuchFieldException {
        Map<Class<?>, ConfigSerializer<?>> registered =
                Collections.<Class<?>, ConfigSerializer<?>>singletonMap(AnnotatedValue.class, new TextSerializer("registered"));
        ConfigSerializerResolver resolver = new ConfigSerializerResolver(registered);
        Field field = FieldOwner.class.getDeclaredField("value");

        ConfigSerializer<Object> selected = resolver.forField(field);

        assertEquals("field", selected.serialize(new AnnotatedValue("value"), context()));
    }

    private static ConfigSerializationContext context() {
        return new ConfigSerializationContext("value", AnnotatedValue.class, AnnotatedValue.class,
                Collections.<java.lang.annotation.Annotation>emptyList(), null);
    }

    @ConfigSerializeWith(CountingSerializer.class)
    private static final class AnnotatedValue {
        final String text;

        AnnotatedValue(String text) {
            this.text = text;
        }
    }

    private static final class FieldOwner {
        @ConfigSerializeWith(FieldSerializer.class)
        AnnotatedValue value;
    }

    private static final class CountingSerializer implements ConfigSerializer<AnnotatedValue> {
        CountingSerializer() {
            instances++;
        }

        @Override
        @NotNull
        public Object serialize(@NotNull AnnotatedValue value, @NotNull ConfigSerializationContext context) {
            return "annotation";
        }

        @Override
        @NotNull
        public AnnotatedValue deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
            return new AnnotatedValue(String.valueOf(value));
        }
    }

    private static final class FieldSerializer implements ConfigSerializer<AnnotatedValue> {
        @Override
        @NotNull
        public Object serialize(@NotNull AnnotatedValue value, @NotNull ConfigSerializationContext context) {
            return "field";
        }

        @Override
        @NotNull
        public AnnotatedValue deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
            return new AnnotatedValue(String.valueOf(value));
        }
    }

    private static final class TextSerializer implements ConfigSerializer<AnnotatedValue> {
        private final String result;

        TextSerializer(String result) {
            this.result = result;
        }

        @Override
        @NotNull
        public Object serialize(@NotNull AnnotatedValue value, @NotNull ConfigSerializationContext context) {
            return result;
        }

        @Override
        @NotNull
        public AnnotatedValue deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
            return new AnnotatedValue(String.valueOf(value));
        }
    }
}
