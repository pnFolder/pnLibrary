package org.pnfolder.pnlibrary.config.yaml;

import org.pnfolder.pnlibrary.config.yaml.schema.ConfigObjectIntrospector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigCodec;
import org.pnfolder.pnlibrary.api.config.ConfigIgnore;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigOrder;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger;

class ConfigReflectionCostTest {
    public static class Base {
        public String baseName = "x";

        @ConfigOrder(5)
        public int late = 1;

        public transient int skipped = 2;
    }

    public static class Child extends Base {
        @ConfigIgnore
        public int ignored = 3;

        @ConfigOrder(-1)
        public int first = 4;

        public int port = 5;
        public final List<Integer> list = Collections.singletonList(1);
        public static int shared = 6;
    }

    public static class Fresh extends Base {
    }

    /** The discovery as it was before the cache: every call reads the declared fields of the whole hierarchy. */
    private static List<Field> discoveredAgain(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
                        && !field.isAnnotationPresent(ConfigIgnore.class)) {
                    fields.add(field);
                }
            }
        }
        Collections.sort(fields, Comparator.comparingInt((Field field) -> {
            ConfigOrder order = field.getAnnotation(ConfigOrder.class);
            return order == null ? 0 : order.value();
        }));
        return fields;
    }

    @Test
    void cachedFieldsEqualAFreshDiscovery() {
        ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE);
        for (Class<?> type : Arrays.asList(Base.class, Child.class, Object.class)) {
            assertEquals(discoveredAgain(type), introspector.fields(type), type.getName());
        }
        List<String> names = new ArrayList<String>();
        for (Field field : introspector.fields(Child.class)) {
            names.add(field.getName());
        }
        assertEquals(Arrays.asList("first", "baseName", "port", "list", "late"), names);
    }

    @Test
    void declaredFieldsAreReadOnceAndPerClassHoweverOftenTheyAreAsked() {
        ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE);
        int before = ConfigObjectIntrospector.DECLARED_FIELD_READS.get();

        for (int index = 0; index < 5_000; index++) {
            introspector.fields(Fresh.class);
        }
        for (int index = 0; index < 100; index++) {
            new ConfigObjectIntrospector(ConfigNamingStrategy.SNAKE_CASE).fields(Fresh.class);
        }

        assertEquals(2, ConfigObjectIntrospector.DECLARED_FIELD_READS.get() - before); // Fresh and Base
    }

    private static final class CountingCodec implements ConfigCodec<Integer> {
        final AtomicInteger decodes = new AtomicInteger();

        @Override
        public String encode(Integer value) {
            return "port: " + value + "\n";
        }

        @Override
        public Integer decode(@NotNull String yaml) {
            decodes.incrementAndGet();
            Matcher matcher = Pattern.compile("port: ([0-9]+)").matcher(yaml);
            if (!matcher.find()) {
                throw new IllegalStateException("port is missing");
            }
            return Integer.parseInt(matcher.group(1));
        }
    }

    private static CodeFirstYaml<Integer> yaml(Path file, CountingCodec codec) {
        return new CodeFirstYaml<Integer>(file.toFile(), 3306, codec, new PlatformPnLogger(new PlatformLogging() { }, "test", "test"));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    void savingDecodesTheDocumentOnceAndWritesTheSameFile(@TempDir Path directory) throws IOException {
        CountingCodec codec = new CountingCodec();
        Path file = directory.resolve("config.yml");
        CodeFirstYaml<Integer> managed = yaml(file, codec);
        managed.load();
        codec.decodes.set(0);

        managed.save(5432);

        assertEquals(1, codec.decodes.get());
        assertTrue(read(file).endsWith("port: 5432\n"));
        assertEquals(Integer.valueOf(5432), managed.get());
    }

    @Test
    void creatingTheFileFromDefaultsDecodesOnce(@TempDir Path directory) throws IOException {
        CountingCodec codec = new CountingCodec();
        Path file = directory.resolve("config.yml");

        yaml(file, codec).load();

        assertEquals(1, codec.decodes.get());
        assertTrue(read(file).endsWith("port: 3306\n"));
    }

    @Test
    void rewritingAChangedFileDecodesOnceAndAnInvalidDocumentIsNotWritten(@TempDir Path directory) throws IOException {
        CountingCodec codec = new CountingCodec();
        Path file = directory.resolve("config.yml");
        Files.write(file, "port: 1\n# note\n".getBytes(StandardCharsets.UTF_8));
        codec.decodes.set(0);

        yaml(file, codec).load();

        assertTrue(codec.decodes.get() <= 1, "decoded " + codec.decodes.get() + " times");
        Path broken = directory.resolve("broken.yml");
        CodeFirstYaml<Integer> managed = yaml(broken, codec);
        managed.load();
        String before = read(broken);
        ConfigCodec<Integer> failing = new ConfigCodec<Integer>() {
            @Override
            public String encode(Integer value) {
                return "nonsense\n";
            }

            @Override
            public Integer decode(@NotNull String yaml) {
                throw new IllegalStateException("cannot read");
            }
        };
        try {
            new CodeFirstYaml<Integer>(broken.toFile(), 1, failing, new PlatformPnLogger(new PlatformLogging() { }, "t", "t")).save(7);
        } catch (Exception expected) {
            // the failed save must leave the file as it was
        }
        assertEquals(before, read(broken));
    }
}
