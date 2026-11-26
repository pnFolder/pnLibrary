package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The clans of the examples and who belongs to which. */
public final class Clans {
    private static final Clan WOLVES = new Clan("Wolves", 12, 1_250_000, new WarInfo(340, 2),
            Arrays.asList(new Member("Alex", 47), new Member("Mia", 31)));
    private static final Clan OWLS = new Clan("Owls", 4, 80_000, new WarInfo(90, 0), Arrays.asList(new Member("Bob", 5)));
    private static final Map<UUID, Clan> BY_PLAYER = new HashMap<>();

    private Clans() {
    }

    public static void join(UUID player, Clan clan) {
        BY_PLAYER.put(player, clan);
    }

    public static Clan of(UUID player) {
        return player == null ? null : BY_PLAYER.get(player);
    }

    public static Clan byName(String text) {
        for (Clan clan : Arrays.asList(WOLVES, OWLS)) {
            if (clan.getName().equalsIgnoreCase(text)) {
                return clan;
            }
        }
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        names.add(WOLVES.getName());
        names.add(OWLS.getName());
        return names;
    }

    public static Clan wolves() {
        return WOLVES;
    }
}
