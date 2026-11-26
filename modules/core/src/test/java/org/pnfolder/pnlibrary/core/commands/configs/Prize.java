package org.pnfolder.pnlibrary.core.commands.configs;

import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;

/** A polymorphic setting type of the test configuration files. */
@ConfigPolymorphic
@ConfigTypes(@ConfigType(type = CoinPrize.class, name = "coin"))
public interface Prize {
}
