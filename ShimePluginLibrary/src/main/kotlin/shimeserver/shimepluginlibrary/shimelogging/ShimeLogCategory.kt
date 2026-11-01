package shimeserver.shimepluginlibrary.shimelogging

/** ログカテゴリは、メッセージに秘密情報を漏らすことなくライブラリの出力を検索可能に保つ。 */
enum class ShimeLogCategory(val label: String) {
    CORE("core"),
    SERVICE("service"),
    CONFIG("config"),
    MESSAGE("message"),
    DATABASE("database"),
    STORAGE("storage"),
    CACHE("cache"),
    PLAYER("player"),
    ITEM("item"),
    GUI("gui"),
    COMMAND("command"),
    EVENT("event"),
    TASK("task"),
    ECONOMY("economy"),
    SCOREBOARD("scoreboard"),
    NMS("nms"),
    PACKET("packet"),
    MODULE("module"),
    INTEGRATION("integration"),
    WORLD("world"),
    ENTITY("entity"),
    VALIDATION("validation")
}
