package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.scan.Scanner;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentAdapters;

class PlaceholderReplyRulesTest {
    private static final String EXAMPLES = "org.pnfolder.pnlibrary.core.placeholders.examples.";

    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });

    @Test
    void aMethodThatDoesNotReturnReplyIsRefused() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> engine.scope("test/plain").register(new PlainValues()));
        assertTrue(String.valueOf(failure.getMessage()).contains("must return Reply"), failure.getMessage());
    }

    @Test
    void aPropertyThatIsNotAReplyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> engine.scope("test/plain").register(new PlainProperty()));
    }

    @Test
    void everyExampleClassOfTheApiIsAValidPlaceholderClass() throws Exception {
        List<String> holders = Arrays.asList(
                "ClanBasics", "ClanArguments", "ClanBank", "AboutTheViewer", "ServerValues", "MeValues", "DuelValues", "RouteTour",
                "ClanWarTree", "ClanConverterValues", "ClanReplies");
        // The examples register their own types in the setup; here any reading will do.
        Class<?> clan = Class.forName(EXAMPLES + "Clan");
        registerBlankReading(clan);
        for (String name : holders) {
            Class<?> type = Class.forName(EXAMPLES + name);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            new Scanner().scan(Collections.singletonList(constructor.newInstance()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerBlankReading(Class<T> type) {
        ArgumentAdapters.register(type, "clan", text -> null);
    }
}
