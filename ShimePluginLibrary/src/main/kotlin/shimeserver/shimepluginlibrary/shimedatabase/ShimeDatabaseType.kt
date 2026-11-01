package shimeserver.shimepluginlibrary.shimedatabase

/** サポート対象の JDBC データベースファミリー。 */
enum class ShimeDatabaseType(val displayName: String) {
    SQLITE("SQLite"),
    MYSQL("MySQL"),
    MARIADB("MariaDB")
}
