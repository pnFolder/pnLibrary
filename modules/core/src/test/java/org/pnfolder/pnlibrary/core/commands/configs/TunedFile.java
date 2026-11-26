package org.pnfolder.pnlibrary.core.commands.configs;

import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigEditable;

/** A configuration file with two settings that may be changed in game. */
public class TunedFile {
    @ConfigEditable(title = "Приветствие", description = "Текст при входе.")
    public String greeting = "Привет";

    @ConfigEditable(title = "Пул", description = "Сколько соединений.", apply = ConfigApply.RESTART)
    public int pool = 10;
}
