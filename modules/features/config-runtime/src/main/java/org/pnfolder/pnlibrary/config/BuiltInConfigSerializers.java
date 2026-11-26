package org.pnfolder.pnlibrary.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

/** Standard serializers available in every plugin configuration scope. */
final class BuiltInConfigSerializers {
    private static final Pattern COMPACT_DURATION = Pattern.compile("^([0-9]+)(ms|s|m|h|d)$", Pattern.CASE_INSENSITIVE);

    private BuiltInConfigSerializers() {
    }

    /** Creates a mutable catalog that a scope may extend with plugin serializers. */
    @NotNull
    static LinkedHashMap<Class<?>, ConfigSerializer<?>> create() {
        LinkedHashMap<Class<?>, ConfigSerializer<?>> catalog = new LinkedHashMap<Class<?>, ConfigSerializer<?>>();
        catalog.put(UUID.class, text(UUID::fromString));
        catalog.put(Duration.class, text(BuiltInConfigSerializers::parseDuration));
        catalog.put(Instant.class, text(Instant::parse));
        catalog.put(LocalDate.class, text(LocalDate::parse));
        catalog.put(LocalDateTime.class, text(LocalDateTime::parse));
        catalog.put(OffsetDateTime.class, text(OffsetDateTime::parse));
        catalog.put(ZonedDateTime.class, text(ZonedDateTime::parse));
        catalog.put(URI.class, text(URI::create));
        catalog.put(URL.class, text(source -> {
            try {
                return URI.create(source).toURL();
            } catch (java.net.MalformedURLException error) {
                throw new IllegalArgumentException(error.getMessage(), error);
            }
        }));
        catalog.put(Path.class, text(Paths::get));
        catalog.put(Locale.class, localeSerializer());
        catalog.put(Pattern.class, text(Pattern::compile));
        catalog.put(BigDecimal.class, text(BigDecimal::new));
        catalog.put(BigInteger.class, text(BigInteger::new));
        return catalog;
    }

    private static Duration parseDuration(String value) {
        String source = value.trim();
        Matcher compact = COMPACT_DURATION.matcher(source);
        if (!compact.matches()) {
            return Duration.parse(source.toUpperCase(Locale.ROOT));
        }
        long amount = Long.parseLong(compact.group(1));
        switch (compact.group(2).toLowerCase(Locale.ROOT)) {
            case "ms":
                return Duration.ofMillis(amount);
            case "s":
                return Duration.ofSeconds(amount);
            case "m":
                return Duration.ofMinutes(amount);
            case "h":
                return Duration.ofHours(amount);
            default:
                return Duration.ofDays(amount);
        }
    }

    private static ConfigSerializer<Locale> localeSerializer() {
        return new ConfigSerializer<Locale>() {
            @Override
            @NotNull
            public Object serialize(@NotNull Locale value, @NotNull ConfigSerializationContext context) {
                return value.toLanguageTag();
            }

            @Override
            @NotNull
            public Locale deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
                return Locale.forLanguageTag(value == null ? "" : value.toString());
            }
        };
    }

    private static <T> ConfigSerializer<T> text(Function<String, T> parser) {
        return new ConfigSerializer<T>() {
            @Override
            @NotNull
            public Object serialize(@NotNull T value, @NotNull ConfigSerializationContext context) {
                return value.toString();
            }

            @Override
            @NotNull
            public T deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
                if (value == null) {
                    throw new IllegalStateException("Value cannot be null");
                }
                return parser.apply(value.toString());
            }
        };
    }
}
