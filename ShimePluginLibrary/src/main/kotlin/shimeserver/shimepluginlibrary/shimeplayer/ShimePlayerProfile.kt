package shimeserver.shimepluginlibrary.shimeplayer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * プレイヤーマネージャーがキャッシュする可変のプレイヤープロフィール。
 *
 * 永続化のためのアクセスはマネージャーによって同期されます。各フィールドは意図的に単純にしてあり、
 * 機能プラグインが別のプロフィールシステムを作らずに小さなメタデータ値を付けられるようになっています。
 */
class ShimePlayerProfile(
    val uuid: UUID,
    var name: String,
    var firstJoinMillis: Long = System.currentTimeMillis(),
    var lastJoinMillis: Long = System.currentTimeMillis(),
    var lastQuitMillis: Long = 0L,
    var playTimeMillis: Long = 0L,
    var locale: String = "en_US",
    val metadata: MutableMap<String, String> = ConcurrentHashMap()
) {
    fun metadata(key: String, defaultValue: String? = null): String? = metadata[key] ?: defaultValue

    fun setMetadata(key: String, value: String?) {
        if (value == null) {
            metadata.remove(key)
        } else {
            metadata[key] = value
        }
    }

    fun copy(): ShimePlayerProfile {
        return ShimePlayerProfile(
            uuid = uuid,
            name = name,
            firstJoinMillis = firstJoinMillis,
            lastJoinMillis = lastJoinMillis,
            lastQuitMillis = lastQuitMillis,
            playTimeMillis = playTimeMillis,
            locale = locale,
            metadata = ConcurrentHashMap(metadata)
        )
    }
}
