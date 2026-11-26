package org.pnfolder.pnlibrary.core.placeholders.examples;

/** The small "clans" world the examples talk about. A real plugin has its own classes instead. */
public class WarInfo {
    private final int score;
    private final int wins;

    public WarInfo(int score, int wins) {
        this.score = score;
        this.wins = wins;
    }

    public int getScore() {
        return score;
    }

    public int getWins() {
        return wins;
    }
}
