package org.pnfolder.pnlibrary.config;

import java.util.ArrayList;
import java.util.List;

public final class AwardsFile {
    public List<Award> awards = new ArrayList<>();

    public AwardsFile() {
        awards.add(new MoneyAward(5));
    }
}
