package org.pnfolder.pnlibrary.config;

import java.util.concurrent.TimeUnit;

import org.pnfolder.pnlibrary.api.config.ConfigDuration;
import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigHint;
import org.pnfolder.pnlibrary.api.config.ConfigStep;

public final class RichFile {
    @ConfigEditable(title = "Задержка", description = "Через сколько срабатывает.")
    @ConfigDuration(unit = TimeUnit.SECONDS)
    public int delay = 30;

    @ConfigEditable(title = "Радиус")
    @ConfigStep(step = 5.0, unit = "блоков")
    public int radius = 10;

    @ConfigEditable(title = "Режим")
    @ConfigHint(values = {"alpha", "beta", "gamma"}, strict = true)
    public String mode = "alpha";

    @ConfigEditable(title = "Место")
    @ConfigHint(format = "мир;x;y;z", examples = {"world;0;64;0"})
    public String place = "world;0;64;0";
}
