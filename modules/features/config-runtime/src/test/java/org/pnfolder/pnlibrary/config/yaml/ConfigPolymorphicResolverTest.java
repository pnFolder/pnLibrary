package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;

class ConfigPolymorphicResolverTest {
    private final ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.AS_DECLARED);
    private final List<Annotation> none = Collections.emptyList();

    @Test
    void resolvesBuiltInAliasesCaseInsensitively() {
        ConfigPolymorphicResolver resolver = resolver(Collections.<RuntimeConfigType>emptyList(), "shop");

        assertTrue(resolver.supports(Payment.class, none));
        assertEquals(CardPayment.class, resolver.decodingType(Payment.class, none, "LEGACY-CARD", "payment"));
    }

    @Test
    void visibleExternalTypeWorksWithItsPlainWordAndWithOwnerNamespace() {
        RuntimeConfigType external = new RuntimeConfigType("economy", Payment.class, TokenPayment.class, "token",
                Collections.<String>emptySet(), 0);
        ConfigPolymorphicResolver resolver = resolver(Collections.singletonList(external), "shop");

        assertEquals(TokenPayment.class, resolver.decodingType(Payment.class, none, "token", "payment"));
        assertEquals(TokenPayment.class, resolver.decodingType(Payment.class, none, "economy::token", "payment"));
        assertEquals("token", resolver.encodingType(Payment.class, none, TokenPayment.class).serializedName());
    }

    @Test
    void anUnknownWordFailsWithADedicatedExceptionThatAListCanSkip() {
        ConfigPolymorphicResolver resolver = resolver(Collections.<RuntimeConfigType>emptyList(), "shop");

        assertThrows(UnknownConfigTypeException.class, () -> resolver.decodingType(Payment.class, none, "nope", "payment"));
    }

    private static RuntimeConfigType addition(String owner, Class<?> implementation) {
        return new RuntimeConfigType(owner, Payment.class, implementation, "token", Collections.<String>emptySet(), 0);
    }

    @Test
    void twoAdditionsWithOneWordAndOnePriorityAreAmbiguousUntilNamespaced() {
        ConfigPolymorphicResolver resolver = resolver(
                Arrays.asList(addition("economy", TokenPayment.class), addition("bank", CardPayment.class)), "shop");

        assertThrows(IllegalArgumentException.class, () -> resolver.decodingType(Payment.class, none, "token", "payment"));
        assertEquals(CardPayment.class, resolver.decodingType(Payment.class, none, "bank::token", "payment"));
    }

    @Test
    void consumerOwnedRuntimeTypeCanUseAnUnqualifiedName() {
        RuntimeConfigType owned = new RuntimeConfigType("shop", Payment.class, TokenPayment.class, "token",
                new LinkedHashSet<String>(Collections.singletonList("coins")), 10);
        ConfigPolymorphicResolver resolver = resolver(Collections.singletonList(owned), "SHOP");

        assertEquals(TokenPayment.class, resolver.decodingType(Payment.class, none, "coins", "payment"));
    }

    private ConfigPolymorphicResolver resolver(List<RuntimeConfigType> types, String consumer) {
        return new ConfigPolymorphicResolver(() -> types, consumer, introspector);
    }

    @ConfigPolymorphic
    @ConfigTypes(@ConfigType(type = CardPayment.class, name = "card", aliases = {"legacy-card"}))
    private interface Payment {
    }

    private static final class CardPayment implements Payment {
    }

    private static final class TokenPayment implements Payment {
    }
}
