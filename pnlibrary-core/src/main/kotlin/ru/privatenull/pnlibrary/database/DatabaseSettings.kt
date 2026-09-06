package ru.privatenull.pnlibrary.database

import java.io.File
import java.net.URI

enum class DatabaseType {
    SQLITE, MYSQL, MONGODB, REDIS
}

sealed class DatabaseSettings(val type: DatabaseType)

data class JdbcSettings @JvmOverloads constructor(
    val driverType: DatabaseType,
    val file: File? = null,
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "",
    val password: String = "",
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 2,
    val connectionTimeoutMs: Long = 10_000,
    val idleTimeoutMs: Long = 600_000,
    val maxLifetimeMs: Long = 1_800_000,
) : DatabaseSettings(driverType)

data class MongoSettings(
    val uri: String,
    val database: String,
) : DatabaseSettings(DatabaseType.MONGODB)

data class RedisSettings(
    val uri: String,
) : DatabaseSettings(DatabaseType.REDIS)
