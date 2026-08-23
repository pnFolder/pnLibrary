package ru.privatenull.pnlibrary.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import ru.privatenull.pnlibrary.banner.PluginBanner;

/**
 * Строитель красивого многострочного отчёта для консоли.
 * Сохраняет порядок добавления компонентов и печатается методом {@link #show()}.
 */
public final class MBox {

    private final PluginLogger logger;
    private final String title;
    private final Map<String, PluginBanner.Entry> entries = new LinkedHashMap<>();
    private final Map<String, Throwable> errors = new LinkedHashMap<>();
    private boolean shown;

    MBox(PluginLogger logger, String title) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.title = PluginLogger.requireText(title, "title");
    }

    /** Добавляет успешно завершённый компонент. */
    public MBox ok(String component) {
        return ok(component, null);
    }

    /** Добавляет успешно завершённый компонент с пояснением. */
    public MBox ok(String component, String details) {
        return entry(component, PluginBanner.Status.OK, details);
    }

    /** Добавляет предупреждение. */
    public MBox warn(String component) {
        return warn(component, null);
    }

    /** Добавляет предупреждение с пояснением. */
    public MBox warn(String component, String details) {
        return entry(component, PluginBanner.Status.WARN, details);
    }

    /** Добавляет ошибку без исключения. */
    public MBox fail(String component) {
        return fail(component, (String) null);
    }

    /** Добавляет ошибку с понятным пояснением. */
    public MBox fail(String component, String details) {
        return entry(component, PluginBanner.Status.FAIL, details);
    }

    /**
     * Добавляет ошибку и запоминает исключение. При {@link #show()} рядом с
     * MBox будет напечатан полный stack trace.
     */
    public MBox fail(String component, Throwable error) {
        String name = PluginLogger.requireText(component, "component");
        Throwable checkedError = Objects.requireNonNull(error, "error");
        entry(name, PluginBanner.Status.FAIL, PluginLogger.throwableMessage(checkedError));
        errors.put(name, checkedError);
        return this;
    }

    /** Добавляет намеренно пропущенный компонент. */
    public MBox skip(String component) {
        return skip(component, null);
    }

    /** Добавляет намеренно пропущенный компонент с пояснением. */
    public MBox skip(String component, String details) {
        return entry(component, PluginBanner.Status.SKIP, details);
    }

    /**
     * Быстро показывает состояние опционального компонента.
     * Активный компонент получает {@code OK}, неактивный — {@code SKIP}.
     *
     * @param component название компонента
     * @param active запущен ли компонент
     * @return этот MBox
     */
    public MBox componentStatus(String component, boolean active) {
        return active
                ? ok(component, "Активен")
                : skip(component, "Не настроен");
    }

    /** Добавляет компонент с произвольным статусом. */
    public MBox entry(String component, PluginBanner.Status status, String details) {
        ensureNotShown();
        String name = PluginLogger.requireText(component, "component");
        entries.put(name, new PluginBanner.Entry(
                Objects.requireNonNull(status, "status"), details));
        if (status != PluginBanner.Status.FAIL) {
            errors.remove(name);
        }
        return this;
    }

    /**
     * Печатает блок. Один экземпляр MBox можно напечатать только один раз,
     * чтобы случайно не дублировать сообщения и stack trace.
     */
    public void show() {
        ensureNotShown();
        shown = true;
        logger.printBox(this);
    }

    /** Псевдоним {@link #show()} для кода, где удобнее название {@code log}. */
    public void log() {
        show();
    }

    /** @return заголовок блока */
    public String title() {
        return title;
    }

    /** @return неизменяемое представление добавленных компонентов */
    public Map<String, PluginBanner.Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    Map<String, Throwable> errors() {
        return Collections.unmodifiableMap(errors);
    }

    PluginBanner.Status overallStatus() {
        if (contains(PluginBanner.Status.FAIL)) return PluginBanner.Status.FAIL;
        if (contains(PluginBanner.Status.WARN)) return PluginBanner.Status.WARN;
        if (!entries.isEmpty() && entries.values().stream()
                .allMatch(entry -> entry.status() == PluginBanner.Status.SKIP)) {
            return PluginBanner.Status.SKIP;
        }
        return PluginBanner.Status.OK;
    }

    private boolean contains(PluginBanner.Status status) {
        return entries.values().stream().anyMatch(entry -> entry.status() == status);
    }

    private void ensureNotShown() {
        if (shown) {
            throw new IllegalStateException("MBox has already been shown");
        }
    }
}
