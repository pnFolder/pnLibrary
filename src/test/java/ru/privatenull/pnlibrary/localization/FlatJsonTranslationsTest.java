package ru.privatenull.pnlibrary.localization;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlatJsonTranslationsTest {
    @Test
    void parsesEscapedFlatTranslationObject() throws Exception {
        String json = "{\"item.minecraft.stone\":\"Stone\",\"escaped\":\"line\\n\\u0410\"}";
        var values = FlatJsonTranslations.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Stone", values.get("item.minecraft.stone"));
        assertEquals("line\nА", values.get("escaped"));
    }

    @Test
    void rejectsNonStringValues() {
        String json = "{\"broken\":1}";
        assertThrows(Exception.class, () -> FlatJsonTranslations.read(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }
}
