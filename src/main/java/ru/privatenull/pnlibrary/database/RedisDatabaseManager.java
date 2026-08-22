package ru.privatenull.pnlibrary.database;

import redis.clients.jedis.JedisPooled;

import java.net.URI;

/** Owns one thread-safe Redis pool for the complete plugin lifecycle. */
public final class RedisDatabaseManager implements AutoCloseable {
    private final RedisSettings settings;
    private JedisPooled client;

    public RedisDatabaseManager(RedisSettings settings) {
        this.settings = settings;
    }

    public synchronized void open() {
        if (client != null) return;
        client = new JedisPooled(URI.create(settings.uri()));
        String pong = client.ping();
        if (!"PONG".equalsIgnoreCase(pong)) throw new IllegalStateException("Redis ping failed: " + pong);
    }

    public JedisPooled client() {
        JedisPooled current = client;
        if (current == null) throw new IllegalStateException("Redis database is not open");
        return current;
    }

    public String key(String suffix) {
        String clean = suffix == null ? "" : suffix.replaceAll("^:+", "");
        return settings.namespace() + ":" + clean;
    }

    public RedisSettings settings() {
        return settings;
    }

    @Override
    public synchronized void close() {
        if (client != null) client.close();
        client = null;
    }
}
