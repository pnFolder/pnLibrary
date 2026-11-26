package org.pnfolder.pnlibrary.config;

import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(@ConfigType(type = MoneyAward.class, name = "money"))
public interface Award {
}
