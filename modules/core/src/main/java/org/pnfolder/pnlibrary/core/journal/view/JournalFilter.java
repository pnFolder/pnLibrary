package org.pnfolder.pnlibrary.core.journal.view;

import org.pnfolder.pnlibrary.core.journal.JournalLevel;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** One tab of the journal list: the command word, the tab label and the levels it shows. */
final class JournalFilter {
    /** The tabs in their order. */
    static final List<JournalFilter> ALL = Arrays.asList(
        new JournalFilter("all", "все", Collections.<JournalLevel>emptySet()),
        new JournalFilter("error", "ошибки", EnumSet.of(JournalLevel.ERROR)),
        new JournalFilter("warning", "предупр.", EnumSet.of(JournalLevel.WARNING)),
        new JournalFilter("info", "инфо", EnumSet.of(JournalLevel.INFO)));

    final String word;
    final String label;
    final Set<JournalLevel> levels;

    private JournalFilter(String word, String label, Set<JournalLevel> levels) {
        this.word = word;
        this.label = label;
        this.levels = levels;
    }

    boolean shows(List<org.pnfolder.pnlibrary.core.journal.JournalRecord> group) {
        return levels.isEmpty() || levels.contains(group.get(0).getLevel());
    }

    static JournalFilter find(String word) {
        for (JournalFilter filter : ALL) {
            if (filter.word.equals(word) || ("warn".equals(word) && "warning".equals(filter.word))) {
                return filter;
            }
        }
        return null;
    }
}
