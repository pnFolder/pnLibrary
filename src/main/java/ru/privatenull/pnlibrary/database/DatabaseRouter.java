package ru.privatenull.pnlibrary.database;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Opens exactly one configured backend and exposes only its matching manager.
 * A plugin owns one router and shares it between all repositories.
 */
public final class DatabaseRouter implements AutoCloseable {
    private final DatabaseSettings settings;
    private final AtomicBoolean open = new AtomicBoolean();
    private JdbcDatabase jdbc;
    private MongoDatabaseManager mongo;
    private RedisDatabaseManager redis;

    public DatabaseRouter(DatabaseSettings settings) {
        if (settings == null) throw new IllegalArgumentException("Database settings cannot be null");
        this.settings = settings;
    }

    /** Parses, opens and returns a router suitable for a plugin's onEnable lifecycle. */
    public static DatabaseRouter from(ConfigurationSection section, File dataFolder) {
        DatabaseRouter router = new DatabaseRouter(DatabaseSettings.from(section, dataFolder));
        router.open();
        return router;
    }

    public synchronized void open() {
        if (!open.compareAndSet(false, true)) return;
        try {
            switch (settings.type()) {
                case SQLITE, MYSQL -> {
                    jdbc = new JdbcDatabase((JdbcSettings) settings);
                    jdbc.open();
                }
                case MONGODB -> {
                    mongo = new MongoDatabaseManager((MongoSettings) settings);
                    mongo.open();
                }
                case REDIS -> {
                    redis = new RedisDatabaseManager((RedisSettings) settings);
                    redis.open();
                }
            }
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public DatabaseType type() {
        return settings.type();
    }

    public boolean isOpen() {
        return open.get();
    }

    /**
     * Routes repository creation to the active backend without exposing a
     * repeated switch statement in every consuming plugin.
     */
    public <T> T route(Function<JdbcDatabase, T> jdbcRoute,
                       Function<MongoDatabaseManager, T> mongoRoute,
                       Function<RedisDatabaseManager, T> redisRoute) {
        if (!isOpen()) throw new IllegalStateException("Database router is not open");
        T result = switch (type()) {
            case SQLITE, MYSQL -> requireRoute(jdbcRoute, "JDBC").apply(jdbc());
            case MONGODB -> requireRoute(mongoRoute, "MongoDB").apply(mongo());
            case REDIS -> requireRoute(redisRoute, "Redis").apply(redis());
        };
        if (result == null) throw new IllegalStateException("Database route returned null for " + type());
        return result;
    }

    private static <I, O> Function<I, O> requireRoute(Function<I, O> route, String name) {
        if (route == null) throw new IllegalArgumentException(name + " route is not configured");
        return route;
    }

    public JdbcDatabase jdbc() {
        if (jdbc == null) throw wrong(DatabaseType.SQLITE, DatabaseType.MYSQL);
        return jdbc;
    }

    public MongoDatabaseManager mongo() {
        if (mongo == null) throw wrong(DatabaseType.MONGODB);
        return mongo;
    }

    public RedisDatabaseManager redis() {
        if (redis == null) throw wrong(DatabaseType.REDIS);
        return redis;
    }

    private IllegalStateException wrong(DatabaseType... expected) {
        return new IllegalStateException("Backend " + settings.type() + " is active; requested "
                + java.util.Arrays.toString(expected));
    }

    @Override
    public synchronized void close() {
        if (redis != null) redis.close();
        if (mongo != null) mongo.close();
        if (jdbc != null) jdbc.close();
        redis = null;
        mongo = null;
        jdbc = null;
        open.set(false);
    }
}
