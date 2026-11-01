package shimeserver.shimepluginlibrary.shimeservice

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeState

/**
 * 名前で検出可能な、管理されたライフサイクルを持つコンポーネント。
 *
 * 実装は [ShimeServiceRegistry] に登録すべきである。サービスは依存関係順に初期化され、
 * 逆順に停止される。
 */
interface ShimeService {
    /** 検索と依存関係の宣言に使用される、安定した一意の名前。 */
    val serviceName: String

    /** 依存関係がない場合、値が大きいものほど先に初期化される。 */
    val servicePriority: Int
        get() = 0

    /** このサービスの初期化前に準備完了している必要があるサービスの名前。 */
    val serviceDependencies: Set<String>
        get() = emptySet()

    val state: ShimeState

    fun initialize(context: ShimeContext)

    fun shutdown()
}
