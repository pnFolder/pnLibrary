package ru.privatenull.pnlibrary.metrics;

/** Серверная платформа, для которой запущена текущая сессия bStats. */
public enum MetricsPlatform {
    /** Bukkit, Spigot или Paper. */
    BUKKIT,

    /** BungeeCord или совместимый прокси. */
    BUNGEECORD,

    /** Velocity Proxy. */
    VELOCITY
}
