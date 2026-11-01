package shimeserver.shimepluginlibrary.shimebootstrap

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimeconfig.ShimeConfig
import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabase
import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabaseSettings
import shimeserver.shimepluginlibrary.shimeexception.ShimeConfigurationException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import java.io.File
import java.util.Locale

/**
 * メイン設定の `database` セクションを読み取り、有効な場合にデータベースへ接続する。
 *
 * ローダーは認証情報を決してログに出力しない。SQLite ファイルはプラグインのデータフォルダ内に制限されるため、
 * 不正な設定によってサーバーが任意のファイルを指すことはない。
 */
object ShimePluginDatabaseLoader {
    /** メインプラグインが登録するデータベースに使用する名前。 */
    const val DEFAULT_DATABASE_NAME = "default"

    /**
     * 設定されたデータベースに接続する。
     *
     * @return 接続されたデータベース。`database.enabled` が false の場合は `null`。
     * @throws ShimeConfigurationException 設定された種別または SQLite パスが無効な場合。
     */
    @JvmStatic
    fun load(plugin: JavaPlugin, config: ShimeConfig, library: ShimeLibrary): ShimeDatabase? {
        if (!config.getBoolean("database.enabled", false)) {
            library.logger.info(
                ShimeLogCategory.DATABASE,
                "Database storage is disabled; services that support it will use their in-memory fallback."
            )
            return null
        }
        val type = (config.getString("database.type", "sqlite") ?: "sqlite").trim().lowercase(Locale.ROOT)
        val settings = when (type) {
            "sqlite" -> {
                val fileName = config.getString("database.sqlite-file", "data.db") ?: "data.db"
                ShimeDatabaseSettings.sqlite(resolveSqliteFile(plugin, fileName))
            }
            "mysql" -> ShimeDatabaseSettings.mysql(
                host = config.getString("database.host", "127.0.0.1") ?: "127.0.0.1",
                port = config.getInt("database.port", 3306),
                database = config.getString("database.name", "minecraft") ?: "minecraft",
                username = config.getString("database.username", "root") ?: "root",
                password = config.getString("database.password", "") ?: "",
                poolSize = config.getInt("database.pool-size", 6),
                useSsl = config.getBoolean("database.use-ssl", false)
            )
            "mariadb" -> ShimeDatabaseSettings.mariadb(
                host = config.getString("database.host", "127.0.0.1") ?: "127.0.0.1",
                port = config.getInt("database.port", 3306),
                database = config.getString("database.name", "minecraft") ?: "minecraft",
                username = config.getString("database.username", "root") ?: "root",
                password = config.getString("database.password", "") ?: "",
                poolSize = config.getInt("database.pool-size", 6),
                useSsl = config.getBoolean("database.use-ssl", false)
            )
            else -> throw ShimeConfigurationException(
                "Unsupported database.type '$type'. Expected sqlite, mysql or mariadb."
            )
        }
        library.logger.info(
            ShimeLogCategory.DATABASE,
            "Connecting database '$DEFAULT_DATABASE_NAME' ($settings)."
        )
        return library.databaseManager.connect(DEFAULT_DATABASE_NAME, settings)
    }

    private fun resolveSqliteFile(plugin: JavaPlugin, fileName: String): File {
        val base = plugin.dataFolder.canonicalFile.toPath()
        val candidate = File(plugin.dataFolder, fileName).canonicalFile
        if (!candidate.toPath().startsWith(base)) {
            throw ShimeConfigurationException(
                "database.sqlite-file must stay inside the plugin data folder: '$fileName'."
            )
        }
        candidate.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw ShimeConfigurationException("Could not create database directory '${parent.path}'.")
            }
        }
        return candidate
    }
}
