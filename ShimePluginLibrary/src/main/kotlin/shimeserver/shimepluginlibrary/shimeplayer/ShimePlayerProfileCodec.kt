package shimeserver.shimepluginlibrary.shimeplayer

import shimeserver.shimepluginlibrary.shimestorage.ShimeStorageCodec
import java.util.UUID

/** プレイヤープロフィール用の YAML/JSON マップコーデック。 */
object ShimePlayerProfileCodec : ShimeStorageCodec<ShimePlayerProfile> {
    override fun encode(value: ShimePlayerProfile): Map<String, Any?> {
        return linkedMapOf(
            "uuid" to value.uuid.toString(),
            "name" to value.name,
            "first-join" to value.firstJoinMillis,
            "last-join" to value.lastJoinMillis,
            "last-quit" to value.lastQuitMillis,
            "play-time" to value.playTimeMillis,
            "locale" to value.locale,
            "session-count" to value.sessionCount,
            "metadata" to value.metadata.toMap(),
            "login-history" to value.loginHistory.map { record ->
                linkedMapOf(
                    "join" to record.joinMillis,
                    "quit" to record.quitMillis
                )
            }
        )
    }

    override fun decode(data: Map<String, Any?>): ShimePlayerProfile {
        val uuid = UUID.fromString(data["uuid"] as? String ?: error("Player profile is missing 'uuid'."))
        val metadata = LinkedHashMap<String, String>()
        (data["metadata"] as? Map<*, *>)?.forEach { (key, value) ->
            if (key is String && value != null) {
                metadata[key] = value.toString()
            }
        }
        val history = ArrayList<ShimeLoginRecord>()
        (data["login-history"] as? List<*>)?.forEach { entry ->
            val map = entry as? Map<*, *> ?: return@forEach
            val join = (map["join"] as? Number)?.toLong() ?: return@forEach
            val quit = (map["quit"] as? Number)?.toLong() ?: 0L
            if (join > 0L) {
                history += ShimeLoginRecord(join, quit)
            }
        }
        return ShimePlayerProfile(
            uuid = uuid,
            name = data["name"] as? String ?: uuid.toString(),
            firstJoinMillis = (data["first-join"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastJoinMillis = (data["last-join"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastQuitMillis = (data["last-quit"] as? Number)?.toLong() ?: 0L,
            playTimeMillis = (data["play-time"] as? Number)?.toLong() ?: 0L,
            locale = data["locale"] as? String ?: "en_US",
            metadata = metadata,
            sessionCount = (data["session-count"] as? Number)?.toInt() ?: history.size,
            loginHistory = java.util.concurrent.CopyOnWriteArrayList(history)
        )
    }
}
