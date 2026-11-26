package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaceholderConverterTest {
    private final PlaceholderEngine engine = new PlaceholderEngine(problem -> { });

    private Object read(String address) throws Exception {
        return engine.resolve(address).toCompletableFuture().get();
    }

    @Test
    void aConverterNamedOnTheParameterReadsTheText() throws Exception {
        engine.scope("test/box").register(new TestBoxValues());

        assertEquals("alpha", read("box.named.alpha"));
        assertEquals("mine", read("box.named.me"));
    }

    @Test
    void aConverterSaysWhyTheTextDoesNotFit() throws Exception {
        engine.scope("test/box").register(new TestBoxValues());

        assertEquals("failed: no box called none", read("box.named.none"));
    }

    @Test
    void aConverterMayAnswerLater() throws Exception {
        engine.scope("test/box").register(new TestBoxValues());

        assertEquals("ABC", read("box.later.abc"));
    }

    @Test
    void aConverterSeesTheArgumentsBeforeItAndTheRoute() throws Exception {
        engine.scope("test/box").register(new TestBoxValues());

        assertEquals("one/1/second/chain", read("box.chain.one.two"));
    }
}
