package org.pnfolder.pnlibrary.core.commands.configs;

import java.util.ArrayList;
import java.util.List;

/** A configuration file with a list of polymorphic values. */
public class PrizeFile {
    public List<Prize> prizes = new ArrayList<>();

    public PrizeFile() {
        prizes.add(new CoinPrize());
    }
}
