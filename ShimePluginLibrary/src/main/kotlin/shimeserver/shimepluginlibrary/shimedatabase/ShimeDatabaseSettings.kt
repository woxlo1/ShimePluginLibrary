package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimeexception.ShimeConfigurationException
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.io.File

/**
 * データベースの接続設定。
 *
 * [toString] は診断時にオブジェクトを安全にログ出力できるよう、意図的にパスワードを伏せる。
 */
class ShimeDatabaseSettings(
    val type: ShimeDatabaseType,
    val host: String = "127.0.0.1",
    val port: Int = if (type == ShimeDatabaseType.MARIADB) 3306 else 3306,
    val database: String = "minecraft",
    val username: String = "root",
    val password: String = "",
    val sqliteFile: File? = null,
    val poolSize: Int = 6,
    val connectionTimeoutMillis: Long = 5_000,
    val queryTimeoutSeconds: Int = 30,
    val maxLifetimeMillis: Long = 1_800_000,
    val useSsl: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap()
) {
    init {
        if (type != ShimeDatabaseType.SQLITE) {
            ShimeValidation.requireNonBlank(host, "database.host")
            ShimeValidation.requireInRange(port, "database.port", 1, 65_535)
            ShimeValidation.requireNonBlank(database, "database.name")
            ShimeValidation.requireNonBlank(username, "database.username")
        } else if (sqliteFile == null) {
            throw ShimeConfigurationException("SQLite settings require a file.")
        }
        ShimeValidation.requireInRange(poolSize, "database.pool-size", 1, 64)
        ShimeValidation.requirePositive(connectionTimeoutMillis, "database.connection-timeout", allowZero = false)
        ShimeValidation.requirePositive(queryTimeoutSeconds, "database.query-timeout", allowZero = false)
    }

    val jdbcUrl: String
        get() = when (type) {
            ShimeDatabaseType.SQLITE -> "jdbc:sqlite:${sqliteFile!!.absolutePath}"
            ShimeDatabaseType.MYSQL -> buildString {
                append("jdbc:mysql://").append(host).append(':').append(port).append('/').append(database)
                append("?useSSL=").append(useSsl)
                append("&characterEncoding=UTF-8")
                append("&useUnicode=true")
                append("&serverTimezone=UTC")
                append("&allowPublicKeyRetrieval=true")
            }
            ShimeDatabaseType.MARIADB -> buildString {
                append("jdbc:mariadb://").append(host).append(':').append(port).append('/').append(database)
                append("?useSsl=").append(useSsl)
                append("&useUnicode=true")
                append("&characterEncoding=UTF-8")
            }
        }

    val driverClassName: String
        get() = when (type) {
            ShimeDatabaseType.SQLITE -> "org.sqlite.JDBC"
            ShimeDatabaseType.MYSQL -> "com.mysql.cj.jdbc.Driver"
            ShimeDatabaseType.MARIADB -> "org.mariadb.jdbc.Driver"
        }

    override fun toString(): String {
        return "ShimeDatabaseSettings(type=$type, host='$host', port=$port, database='$database', " +
            "username='$username', password='***', poolSize=$poolSize)"
    }

    companion object {
        @JvmStatic
        fun sqlite(file: File, poolSize: Int = 1): ShimeDatabaseSettings {
            return ShimeDatabaseSettings(type = ShimeDatabaseType.SQLITE, sqliteFile = file, poolSize = poolSize)
        }

        @JvmStatic
        @JvmOverloads
        fun mysql(
            host: String,
            port: Int = 3306,
            database: String,
            username: String,
            password: String,
            poolSize: Int = 6,
            useSsl: Boolean = false
        ): ShimeDatabaseSettings {
            return ShimeDatabaseSettings(
                type = ShimeDatabaseType.MYSQL,
                host = host,
                port = port,
                database = database,
                username = username,
                password = password,
                poolSize = poolSize,
                useSsl = useSsl
            )
        }

        @JvmStatic
        @JvmOverloads
        fun mariadb(
            host: String,
            port: Int = 3306,
            database: String,
            username: String,
            password: String,
            poolSize: Int = 6,
            useSsl: Boolean = false
        ): ShimeDatabaseSettings {
            return ShimeDatabaseSettings(
                type = ShimeDatabaseType.MARIADB,
                host = host,
                port = port,
                database = database,
                username = username,
                password = password,
                poolSize = poolSize,
                useSsl = useSsl
            )
        }
    }
}
