package org.pnfolder.pnlibrary.config;

import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigRange;

public final class ShopSection {
    @ConfigEditable(title = "Включён", description = "Работает ли магазин.")
    public boolean enabled = true;

    @ConfigEditable(title = "Налог", description = "Доля от цены.")
    @ConfigRange(min = 0.0, max = 1.0)
    public double tax = 0.1;
}
