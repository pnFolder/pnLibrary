package org.pnfolder.pnlibrary.config;

import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigRange;
import org.pnfolder.pnlibrary.api.config.ConfigSecret;

public final class EditableFile {
    @ConfigEditable(title = "Приветствие", description = "Текст при входе.")
    public String greeting = "Привет";

    @ConfigEditable(title = "Пул", description = "Сколько соединений.", apply = ConfigApply.RESTART)
    @ConfigRange(min = 1.0, max = 64.0)
    public int poolSize = 10;

    @ConfigEditable(title = "Сложность", apply = ConfigApply.RELOAD)
    public Difficulty difficulty = Difficulty.EASY;

    @ConfigEditable(title = "Секрет", description = "Не должен показываться.")
    @ConfigSecret
    public String token = "s3cret";

    public String hidden = "not marked";

    public ShopSection shop = new ShopSection();
}
