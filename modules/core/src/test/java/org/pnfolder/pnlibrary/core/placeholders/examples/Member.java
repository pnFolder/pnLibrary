package org.pnfolder.pnlibrary.core.placeholders.examples;

public class Member {
    private final String name;
    private final int kills;

    public Member(String name, int kills) {
        this.name = name;
        this.kills = kills;
    }

    public String getName() {
        return name;
    }

    public int getKills() {
        return kills;
    }
}
