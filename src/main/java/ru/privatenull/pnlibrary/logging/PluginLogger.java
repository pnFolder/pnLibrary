package ru.privatenull.pnlibrary.logging;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;

import ru.privatenull.pnlibrary.banner.PluginBanner;

/**
 * Единый логгер плагина: короткие цветные сообщения, полный stack trace и
 * создание структурированных {@link MBox}.
 *
 * <p>Экземпляр не требуется создавать вручную. Используйте
 * {@code runtime.log()} либо {@code identity.log()}.</p>
 */
public final class PluginLogger {

    private static final String INDENT = "          ";

    private final PluginBanner.Identity identity;
    private final Logger delegate;

    /**
     * Создаёт логгер для идентификатора плагина.
     * Обычно вызывается самой pnLibrary.
     *
     * @param identity идентификатор плагина
     */
    public PluginLogger(PluginBanner.Identity identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
        delegate = identity.plugin().getLogger();
    }

    /** Выводит обычное информационное сообщение. */
    public void info(String message) {
        line(ChatColor.AQUA, "i", message);
    }

    /** Выводит сообщение об успешно завершённом действии. */
    public void success(String message) {
        line(ChatColor.GREEN, "✓", message);
    }

    /** Выводит некритичное предупреждение. */
    public void warn(String message) {
        line(ChatColor.GOLD, "⚠", message);
    }

    /** Выводит сообщение об ошибке без stack trace. */
    public void error(String message) {
        line(ChatColor.RED, "✕", message);
    }

    /**
     * Выводит понятное сообщение и полный stack trace через штатный логгер
     * Bukkit. Причина никогда не теряется.
     *
     * @param message контекст операции
     * @param error исходное исключение
     */
    public void error(String message, Throwable error) {
        String checkedMessage = requireText(message, "message");
        Throwable checkedError = Objects.requireNonNull(error, "error");
        line(ChatColor.RED, "✕", checkedMessage + ": " + throwableMessage(checkedError));
        delegate.log(Level.SEVERE, checkedMessage, checkedError);
    }

    /**
     * Выводит диагностическое сообщение с уровнем {@link Level#FINE}.
     * Его видимость определяется настройками штатного Bukkit logger.
     */
    public void debug(String message) {
        delegate.fine(requireText(message, "message"));
    }

    /**
     * Начинает построение красивого структурированного блока.
     *
     * @param title заголовок блока
     * @return новый MBox
     */
    public MBox mBox(String title) {
        return new MBox(this, title);
    }

    /** Англоязычный псевдоним {@link #mBox(String)}. */
    public MBox box(String title) {
        return mBox(title);
    }

    /** @return исходный Bukkit logger для редких нестандартных случаев */
    public Logger raw() {
        return delegate;
    }

    /** @return идентификатор владельца логгера */
    public PluginBanner.Identity identity() {
        return identity;
    }

    void printBox(MBox box) {
        ConsoleCommandSender console = console();
        PluginBanner.Status status = box.overallStatus();

        console.sendMessage("");
        console.sendMessage(status.color() + "/\\_/\\");
        console.sendMessage(status.color() + "( " + face(status) + " )   "
                + ChatColor.AQUA + identity.plugin().getName()
                + ChatColor.DARK_GRAY + " > " + ChatColor.WHITE + box.title());
        console.sendMessage(status.color() + "> ^ <");
        console.sendMessage("");
        box.entries().forEach((component, entry) -> {
            console.sendMessage(ChatColor.DARK_GRAY + INDENT + "> " + ChatColor.WHITE
                    + component + ChatColor.DARK_GRAY + "  " + entry.status().color()
                    + "[ " + entry.status().displayName() + " ]");
            if (entry.details() != null) {
                console.sendMessage(ChatColor.DARK_GRAY + "            └ "
                        + ChatColor.GRAY + entry.details());
            }
        });
        console.sendMessage("");

        box.errors().forEach((component, error) ->
                delegate.log(Level.SEVERE, box.title() + " — " + component, error));
    }

    private void line(ChatColor color, String symbol, String message) {
        ConsoleCommandSender console = console();
        console.sendMessage(color + "/\\_/\\");
        console.sendMessage(color + "( " + face(symbol) + " )   "
                + color + symbol + " " + requireText(message, "message"));
        console.sendMessage(color + "> ^ <");
    }

    private ConsoleCommandSender console() {
        return identity.plugin().getServer().getConsoleSender();
    }

    private static String face(PluginBanner.Status status) {
        return switch (status) {
            case OK -> "^.^";
            case WARN -> "o.o";
            case FAIL -> "x.x";
            case SKIP -> "-.-";
        };
    }

    private static String face(String symbol) {
        return switch (symbol) {
            case "✓" -> "^.^";
            case "⚠" -> "o.o";
            case "✕" -> "x.x";
            default -> "i.i";
        };
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    static String throwableMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
