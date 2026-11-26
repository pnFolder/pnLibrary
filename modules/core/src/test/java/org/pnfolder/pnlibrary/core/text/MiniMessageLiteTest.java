package org.pnfolder.pnlibrary.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

class MiniMessageLiteTest {
    private static final class Run {
        final String text;
        final Integer color;
        final Set<TextDecoration> decorations;

        Run(String text, Integer color, Set<TextDecoration> decorations) {
            this.text = text;
            this.color = color;
            this.decorations = decorations;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Run)) {
                return false;
            }
            Run run = (Run) other;
            return text.equals(run.text) && Objects.equals(color, run.color) && decorations.equals(run.decorations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, color, decorations);
        }

        @Override
        public String toString() {
            return "Run(" + text + ", " + color + ", " + decorations + ")";
        }
    }

    private static final List<String> SAMPLES = Arrays.asList(
        "plain", "<red>red", "<red>a</red>b", "<red>a<blue>b</blue>c</red>d", "<red>a<blue>b</red>c",
        "<#ff8800>hex</#ff8800> tail", "<color:#00ff00>x</color>y", "<color:gold>x</color>", "<c:aqua>c</c>",
        "<bold>b</bold>n", "<b>b</b>", "<i>i</i>", "<em>e</em>", "<italic>x</italic>", "<u>u</u>", "<underlined>u</underlined>",
        "<st>s</st>", "<strikethrough>s</strikethrough>", "<obf>o</obf>", "<obfuscated>o</obfuscated>",
        "<red><bold>both</bold> red only", "<bold><red>x</red></bold>", "<red>a<reset>b", "a</red>b", "<bold>a<reset>b", "<bold>a<reset>b</bold>c",
        "line<br>two", "line<newline>two", "a \\<red> literal", "<unknown>text</unknown>", "1 < 2 and 3 > 2",
        "<RED>Upper</RED>", "<dark_red>d<dark_gray>g<gray>g2<light_purple>l<white>w", "<green>x</green><yellow>y</yellow>",
        "<red>unclosed <bold>nest", "<>empty", "<red", "text<", "<red><red>double</red>still", "<!bold>neg",
        "<bold>a<!bold>b</!bold>c", "<black>k<dark_blue>b<dark_green>g<dark_aqua>a<gold>g<blue>b<aqua>a");

    private static List<Run> runs(Component component) {
        List<Run> result = new ArrayList<Run>();
        walk(component, Style.empty(), result);
        List<Run> merged = new ArrayList<Run>();
        for (Run run : result) {
            Run last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && Objects.equals(last.color, run.color) && last.decorations.equals(run.decorations)) {
                merged.set(merged.size() - 1, new Run(last.text + run.text, last.color, last.decorations));
            } else {
                merged.add(run);
            }
        }
        return merged;
    }

    private static void walk(Component node, Style parent, List<Run> result) {
        Style style = node.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (node instanceof TextComponent && !((TextComponent) node).content().isEmpty()) {
            String content = ((TextComponent) node).content();
            Set<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);
            for (TextDecoration decoration : TextDecoration.values()) {
                if (style.decoration(decoration) == TextDecoration.State.TRUE) {
                    decorations.add(decoration);
                }
            }
            Run previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (content.equals("\n") && previous != null) {
                result.add(new Run("\n", previous.color, previous.decorations));
            } else {
                result.add(new Run(content, style.color() == null ? null : style.color().value(), decorations));
            }
        }
        for (Component child : node.children()) {
            walk(child, style, result);
        }
    }

    @Test
    void liteMatchesMiniMessageOnTheSupportedSubset() {
        MiniMessage real = MiniMessage.miniMessage();
        for (String sample : SAMPLES) {
            assertEquals(runs(real.deserialize(sample)), runs(MiniMessageLite.deserialize(sample)), "input: " + sample);
        }
    }

    @Test
    void gradientKeepsTheTextAndTheFirstColor() {
        assertEquals(Collections.singletonList(new Run("go", 0xFF0000, Collections.<TextDecoration>emptySet())),
            runs(MiniMessageLite.deserialize("<gradient:#ff0000:#00ff00>go</gradient>")));
    }

    private static CachedComponents components(MiniText mini, List<String> warnings) {
        return new CachedComponents(Mockito.mock(PlaceholderService.class), new ComponentCache(), mini, warnings::add);
    }

    @Test
    void anOldAdventureSwitchesToLiteParsingAndWarnsOnce() {
        List<String> warnings = new ArrayList<String>();
        CachedComponents components = components(new MiniText(() -> {
            throw new NoSuchMethodError("Component.compact()");
        }), warnings);
        Component first = components.deserialize("<red>hi", ComponentSerializerType.MINI_MESSAGE);
        components.deserialize("&aok", ComponentSerializerType.ADAPTIVE);
        components.serialize(first);
        assertEquals(Collections.singletonList(new Run("hi", 0xFF5555, Collections.<TextDecoration>emptySet())), runs(first));
        assertEquals(1, warnings.size());
    }

    @Test
    void adaptiveReadsTheSectionAndAmpersandHexFormOfBukkit() {
        CachedComponents components = components(new MiniText(), new ArrayList<String>());
        List<Run> expected = Collections.singletonList(new Run("go", 0xFC7D37, Collections.<TextDecoration>emptySet()));
        for (String input : Arrays.asList("&x&F&C&7&D&3&7go", "§x§F§C§7§D§3§7go", "&#FC7D37go")) {
            assertEquals(expected, runs(components.deserialize(input, ComponentSerializerType.ADAPTIVE)), input);
        }
    }

    @Test
    void anUnexpectedFailureIsNotSwallowed() {
        IllegalStateException boom = new IllegalStateException("boom");
        CachedComponents components = components(new MiniText(() -> {
            throw boom;
        }), new ArrayList<String>());
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> components.deserialize("<red>x", ComponentSerializerType.MINI_MESSAGE));
        assertSame(boom, thrown);
    }
}
