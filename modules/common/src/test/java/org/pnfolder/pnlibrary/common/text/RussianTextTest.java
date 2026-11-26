package org.pnfolder.pnlibrary.common.text;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RussianTextTest {
    @Test
    void nounsAgreeWithTheNumber() {
        long[] counts = {1L, 2L, 5L, 11L, 12L, 21L, 22L, 25L, 111L, 0L};
        List<String> forms = new ArrayList<>();
        for (long count : counts) {
            forms.add(RussianText.counted(count, "плагин", "плагина", "плагинов"));
        }
        assertEquals(
            Arrays.asList("1 плагин", "2 плагина", "5 плагинов", "11 плагинов", "12 плагинов", "21 плагин",
                "22 плагина", "25 плагинов", "111 плагинов", "0 плагинов"),
            forms);
    }

    @Test
    void durationsUseOneSetOfAbbreviations() {
        assertEquals("3 д", RussianText.period(Duration.ofDays(3).plusHours(5)));
        assertEquals("5 ч", RussianText.period(Duration.ofHours(5)));
        assertEquals("1 мин", RussianText.period(Duration.ofSeconds(10)));
        assertEquals("ещё 30 ч", RussianText.remaining(Duration.ofHours(30)));
        assertEquals("ещё 3 д", RussianText.remaining(Duration.ofDays(3)));
        assertEquals("ещё 20 мин", RussianText.remaining(Duration.ofMinutes(20)));
        assertEquals("только что", RussianText.ago(Duration.ofSeconds(30)));
        assertEquals("4 д назад", RussianText.ago(Duration.ofDays(4)));
        assertEquals("только что", RussianText.ago(Duration.ofMinutes(-5)));
    }
}
