package ru.privatenull.pnlibrary.core.config

import ru.privatenull.pnlibrary.api.config.ConfigSerializationContext
import ru.privatenull.pnlibrary.api.config.ConfigSerializer
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/** Standard serializers available in every plugin configuration scope. */
internal object BuiltInConfigSerializers {
    /** Creates a mutable catalog that a scope may extend with plugin serializers. */
    fun create(): LinkedHashMap<Class<*>, ConfigSerializer<*>> = linkedMapOf(
        UUID::class.java to text(UUID::fromString),
        Duration::class.java to text(::parseDuration),
        Instant::class.java to text(Instant::parse),
        LocalDate::class.java to text(LocalDate::parse),
        LocalDateTime::class.java to text(LocalDateTime::parse),
        OffsetDateTime::class.java to text(OffsetDateTime::parse),
        ZonedDateTime::class.java to text(ZonedDateTime::parse),
        URI::class.java to text(::URI),
        URL::class.java to text { source -> URI.create(source).toURL() },
        Path::class.java to text(Paths::get),
        Locale::class.java to localeSerializer(),
        Pattern::class.java to text(Pattern::compile),
        BigDecimal::class.java to text(::BigDecimal),
        BigInteger::class.java to text(::BigInteger),
    )

    private fun parseDuration(value: String): Duration {
        val source = value.trim()
        val compact = COMPACT_DURATION.matchEntire(source)
            ?: return Duration.parse(source.uppercase(Locale.ROOT))
        val amount = compact.groupValues[1].toLong()
        return when (compact.groupValues[2].lowercase(Locale.ROOT)) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            else -> Duration.ofDays(amount)
        }
    }

    private fun localeSerializer(): ConfigSerializer<Locale> = object : ConfigSerializer<Locale> {
        override fun serialize(value: Locale, context: ConfigSerializationContext): Any = value.toLanguageTag()

        override fun deserialize(value: Any?, context: ConfigSerializationContext): Locale =
            Locale.forLanguageTag(value?.toString().orEmpty())
    }

    private fun <T : Any> text(parser: (String) -> T): ConfigSerializer<T> = object : ConfigSerializer<T> {
        override fun serialize(value: T, context: ConfigSerializationContext): Any = value.toString()

        override fun deserialize(value: Any?, context: ConfigSerializationContext): T =
            parser(value?.toString() ?: error("Value cannot be null"))
    }

    private val COMPACT_DURATION = Regex("^([0-9]+)(ms|s|m|h|d)$", RegexOption.IGNORE_CASE)
}
