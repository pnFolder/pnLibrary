package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.List;

public class Clan {
    private final String name;
    private final int level;
    private final long balance;
    private final WarInfo war;
    private final List<Member> members;

    public Clan(String name, int level, long balance, WarInfo war, List<Member> members) {
        this.name = name;
        this.level = level;
        this.balance = balance;
        this.war = war;
        this.members = members;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public long getBalance() {
        return balance;
    }

    public WarInfo getWar() {
        return war;
    }

    public List<Member> getMembers() {
        return members;
    }
}
