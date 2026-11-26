package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.scan.PhNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** The registered trees of placeholders by group (the first word of an address), and what they tell about themselves. */
final class PlaceholderGroups {
    /** The group of trees that start with an argument: it answers when no word matches. */
    static final String SLOT_GROUP = "";

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PlaceholderEntry>> groups =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<PlaceholderEntry>>();
    private final Consumer<String> report;

    PlaceholderGroups(Consumer<String> report) {
        this.report = report;
    }

    /**
     * The entries of a group, the nearest to {@code consumer} first, then the entries that start with an argument
     * (a word always wins over them). A placeholder inside may still be closed to the consumer.
     */
    List<PlaceholderEntry> visibleEntries(String group, final String consumer) {
        List<PlaceholderEntry> entries = nearestFirst(groups.get(group), consumer);
        entries.addAll(nearestFirst(groups.get(SLOT_GROUP), consumer));
        return entries;
    }

    private static List<PlaceholderEntry> nearestFirst(List<PlaceholderEntry> registered, final String consumer) {
        List<PlaceholderEntry> entries = new ArrayList<PlaceholderEntry>();
        if (registered != null) {
            entries.addAll(registered);
        }
        Collections.sort(entries, (a, b) -> rank(a, consumer) - rank(b, consumer));
        return entries;
    }

    private static int rank(PlaceholderEntry entry, String consumer) {
        Integer near = Ownership.nearness(entry.getOwner(), Visibility.ALL, consumer);
        return near == null ? Integer.MAX_VALUE : near;
    }

    /** Refuses a second tree that starts with an argument for {@code owner}: its arguments would be ambiguous. */
    void requireFreeSlot(String owner, int incoming) {
        int count = incoming;
        List<PlaceholderEntry> taken = groups.get(SLOT_GROUP);
        if (taken != null) {
            for (PlaceholderEntry entry : taken) {
                count += entry.getOwner().equals(owner) ? 1 : 0;
            }
        }
        if (count > 1) {
            throw new IllegalStateException(owner + ": only one placeholder may be bare in all its @Placeholders(root = true) classes,"
                    + " their arguments right after the prefix would be ambiguous");
        }
    }

    /** The PlaceholderAPI prefixes that some entry asks to be shown under. */
    Set<String> publishedPrefixes() {
        Set<String> wanted = new HashSet<String>();
        for (List<PlaceholderEntry> entries : groups.values()) {
            for (PlaceholderEntry entry : entries) {
                if (entry.getPapi() != null) {
                    wanted.add(entry.getPapi());
                }
            }
        }
        return wanted;
    }

    /** Every placeholder, sorted by address. */
    List<PlaceholderInfo> list() {
        List<PlaceholderInfo> found = new ArrayList<PlaceholderInfo>();
        for (List<PlaceholderEntry> entries : groups.values()) {
            for (PlaceholderEntry entry : entries) {
                collect(entry, entry.getRoot(), Collections.emptyList(), found);
            }
        }
        Collections.sort(found, (a, b) -> a.getAddress().compareTo(b.getAddress()));
        return found;
    }

    private void collect(PlaceholderEntry entry, PhNode node, List<String> parent, List<PlaceholderInfo> into) {
        List<String> here = new ArrayList<String>(parent);
        here.add(node.getPart().isSlot() ? "{" + node.getPart().getName() + "}" : node.getPart().getName());
        if (node.getValue() != null) {
            List<String> published = new ArrayList<String>();
            if (entry.getPapi() != null && node.getVisibility() == Visibility.ALL) {
                published.add(entry.getPapi() + "_" + String.join("_", here));
            }
            into.add(new PlaceholderInfo(String.join(".", here), node.getDescription(), entry.getOwner(),
                    node.getValueType().getSimpleName(), node.getFormat(), published, node.getVisibility()));
        }
        for (PhNode child : node.getChildren()) {
            collect(entry, child, here, into);
        }
    }

    private Map<String, Visibility> addresses(PhNode node, String parent) {
        String name = node.getPart().isSlot() ? "{}" : node.getPart().getName();
        String here = parent.isEmpty() ? name : parent + "." + name;
        Map<String, Visibility> found = new LinkedHashMap<String, Visibility>();
        if (node.getValue() != null) {
            found.put(here, node.getVisibility());
        }
        for (PhNode child : node.getChildren()) {
            found.putAll(addresses(child, here));
        }
        return found;
    }

    void add(PlaceholderEntry entry) {
        String name = entry.getGroup();
        warnAboutClashes(entry, name);
        CopyOnWriteArrayList<PlaceholderEntry> list = groups.get(name);
        if (list == null) {
            list = new CopyOnWriteArrayList<PlaceholderEntry>();
            CopyOnWriteArrayList<PlaceholderEntry> raced = groups.putIfAbsent(name, list);
            if (raced != null) {
                list = raced;
            }
        }
        list.add(entry);
    }

    private void warnAboutClashes(PlaceholderEntry entry, String name) {
        List<PlaceholderEntry> others = groups.get(name);
        if (others == null) {
            return;
        }
        Map<String, Visibility> mine = addresses(entry.getRoot(), "");
        for (PlaceholderEntry other : others) {
            if (other.getOwner().equals(entry.getOwner())) {
                if (other.isFromRoot() != entry.isFromRoot()) {
                    report.accept(entry.getOwner() + ": the word '" + name + "' is both a placeholder of a root group and a group;"
                            + " the group answers first");
                }
                continue;
            }
            Map<String, Visibility> theirs = addresses(other.getRoot(), "");
            List<String> same = new ArrayList<String>();
            for (Map.Entry<String, Visibility> address : mine.entrySet()) {
                Visibility there = theirs.get(address.getKey());
                if (there != null && (Ownership.nearness(other.getOwner(), there, entry.getOwner()) != null
                        || Ownership.nearness(entry.getOwner(), address.getValue(), other.getOwner()) != null)) {
                    same.add(address.getKey());
                }
            }
            if (!same.isEmpty()) {
                String more = same.size() > 1 ? " and " + (same.size() - 1) + " more" : "";
                report.accept(entry.getOwner() + " and " + other.getOwner() + " both have {" + same.get(0) + "}" + more
                        + "; the nearer one answers");
            }
        }
    }

    void remove(PlaceholderEntry entry) {
        List<PlaceholderEntry> list = groups.get(entry.getGroup());
        if (list != null) {
            list.remove(entry);
        }
        dropEmpty();
    }

    void removeOwner(String owner) {
        for (CopyOnWriteArrayList<PlaceholderEntry> list : groups.values()) {
            for (PlaceholderEntry entry : list) {
                if (entry.getOwner().equals(owner)) {
                    list.remove(entry);
                }
            }
        }
        dropEmpty();
    }

    private void dropEmpty() {
        for (Map.Entry<String, CopyOnWriteArrayList<PlaceholderEntry>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) {
                groups.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
