package org.pnfolder.pnlibrary.core.text;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** One open MiniMessage tag: a color, a decoration, or a fade that only keeps its first color. */
final class LiteTag {
    final String name;
    final TextColor color;
    final TextDecoration decoration;
    final boolean state;

    LiteTag(String name, TextColor color, TextDecoration decoration, boolean state) {
        this.name = name;
        this.color = color;
        this.decoration = decoration;
        this.state = state;
    }
}
