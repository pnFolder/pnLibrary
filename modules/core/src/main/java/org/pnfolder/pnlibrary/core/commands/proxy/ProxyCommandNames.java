package org.pnfolder.pnlibrary.core.commands.proxy;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * The names of the control command on a proxy.
 *
 * <p>A player's {@code /pn} on a proxied network reaches the proxy first, so a proxy that owned {@code /pn} would hide the
 * one on the game server. The proxy therefore answers to {@code /pnproxy} and {@code /pnp}, and {@link #rewrite} turns the
 * {@code /pn} that the shared command text and buttons mention into {@link #NAME}, so a click on the proxy runs the proxy
 * command.
 */
public final class ProxyCommandNames {
    public static final String NAME = "pnproxy";
    public static final Set<String> ALIASES = Collections.singleton("pnp");

    private static final Pattern ROOT = Pattern.compile("/pn(?=[ \t]|$)");
    private static final TextReplacementConfig TEXT = TextReplacementConfig.builder().match(ROOT).replacement("/" + NAME).build();

    private ProxyCommandNames() {
    }

    public static boolean owns(@NotNull String alias) {
        if (alias.equalsIgnoreCase(NAME)) {
            return true;
        }
        for (String known : ALIASES) {
            if (known.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static Component rewrite(@NotNull Component message) {
        return clicks(message.replaceText(TEXT));
    }

    private static Component clicks(Component component) {
        ClickEvent click = component.clickEvent();
        java.util.List<Component> children = new java.util.ArrayList<Component>();
        for (Component child : component.children()) {
            children.add(clicks(child));
        }
        Component result = component.children(children);
        boolean runs = click != null
                && (click.action() == ClickEvent.Action.RUN_COMMAND || click.action() == ClickEvent.Action.SUGGEST_COMMAND);
        if (!runs || !click.value().startsWith("/pn")) {
            return result;
        }
        return result.clickEvent(ClickEvent.clickEvent(click.action(), ROOT.matcher(click.value()).replaceFirst("/" + NAME)));
    }
}
