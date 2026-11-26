package org.pnfolder.pnlibrary.config;

public final class MoneyAward implements Award {
    public int amount;

    public MoneyAward() {
        this(0);
    }

    public MoneyAward(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MoneyAward && ((MoneyAward) other).amount == amount;
    }

    @Override
    public int hashCode() {
        return amount;
    }

    @Override
    public String toString() {
        return "MoneyAward(amount=" + amount + ")";
    }
}
