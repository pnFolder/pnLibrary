package org.pnfolder.pnlibrary.core.menu.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuTextTest {
    @Test
    void timeLeftPicksTheTwoLargestUnitsAndNeverShowsZeroMinutes() {
        assertEquals("1 мин", MenuText.timeLeft(Duration.ofSeconds(5)));
        assertEquals("2 ч 10 мин", MenuText.timeLeft(Duration.ofMinutes(130)));
        assertEquals("3 д 4 ч", MenuText.timeLeft(Duration.ofHours(76)));
    }

    @Test
    void agoSpeaksOfThePastAndTheFuture() {
        Instant now = Instant.now();

        assertEquals("только что", MenuText.ago(now.minusSeconds(10)));
        assertEquals("5 мин назад", MenuText.ago(now.minus(Duration.ofMinutes(5)).minusSeconds(5)));
        assertTrue(MenuText.ago(now.plus(Duration.ofHours(3))).startsWith("через "));
    }

    @Test
    void progressBarIsNeverEmptyAndNeverLongerThanTheWidestBar() {
        int widest = MenuText.progressBar(100, 100).length();

        assertEquals(1, MenuText.progressBar(0, 100).length());
        assertEquals(widest, MenuText.progressBar(500, 100).length());
        assertEquals(1, MenuText.progressBar(0, 0).length());
    }

    @Test
    void everyMenuPermissionIsListedOnce() throws IllegalAccessException {
        Set<String> declared = new HashSet<>();
        for (Field field : MenuPermissions.class.getDeclaredFields()) {
            if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                declared.add((String) field.get(null));
            }
        }

        assertEquals(declared, new HashSet<>(MenuPermissions.ALL));
        assertEquals(MenuPermissions.ALL.size(), new HashSet<>(MenuPermissions.ALL).size());
    }
}
