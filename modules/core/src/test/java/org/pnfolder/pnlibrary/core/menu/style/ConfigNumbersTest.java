package org.pnfolder.pnlibrary.core.menu.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;

class ConfigNumbersTest {
    private static ConfigSetting number(String value, double step, String unit, TimeUnit duration, Double min, Double max) {
        return ConfigSetting.builder("p", "Число", "", ConfigSettingKind.INTEGER, value)
            .min(min).max(max).step(step).unit(unit).duration(duration).build();
    }

    private static ConfigSetting number(String value, TimeUnit duration) {
        return number(value, 1.0, "", duration, null, null);
    }

    private static List<String> labels(List<NumberStep> ladder) {
        List<String> labels = new ArrayList<>();
        for (NumberStep step : ladder) {
            labels.add(step.getLabel());
        }
        return labels;
    }

    @Test
    void aTimeIsWrittenOutInWords() {
        assertEquals("5400 сек (1 ч 30 мин)", ConfigNumbers.shown(number("5400", TimeUnit.SECONDS)));
        assertEquals("0 с", ConfigNumbers.human(0));
    }

    @Test
    void stepsOfSecondsGoThroughMinutesToAnHour() {
        List<NumberStep> ladder = ConfigNumbers.ladder(number("30", TimeUnit.SECONDS));

        assertEquals(Arrays.asList("1 с", "1 мин", "10 мин", "1 ч"), labels(ladder));
        assertEquals(60.0, ladder.get(1).getAmount());
    }

    @Test
    void aPlainNumberStepsByItsStepTenTimesAHundredAndAThousand() {
        List<NumberStep> ladder = ConfigNumbers.ladder(number("10", 5.0, "блоков", null, null, null));

        assertEquals(Arrays.asList("5 блоков", "50 блоков", "500 блоков", "5000 блоков"), labels(ladder));
    }

    @Test
    void stepsWiderThanTheBoundsAreDroppedButTwoAlwaysRemain() {
        List<NumberStep> ladder = ConfigNumbers.ladder(number("5", 1.0, "", null, 0.0, 20.0));

        assertTrue(ladder.size() >= 2);
        for (NumberStep step : ladder) {
            assertTrue(step.getAmount() <= 20.0 || step.getAmount() == 10.0);
        }
    }
}
