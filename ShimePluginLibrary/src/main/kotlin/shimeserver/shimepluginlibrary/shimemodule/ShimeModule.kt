package shimeserver.shimepluginlibrary.shimemodule

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeState

/**
 * ライフサイクルが管理される機能モジュール。
 *
 * モジュールはサービスより軽量で、任意のゲームプレイ機能向けに設計されている。依存関係は初期化前に
 * [ShimeModuleManager] によって解決され、逆順でシャットダウンされる。
 */
interface ShimeModule {
    /** 安定した一意のモジュール識別子。 */
    val moduleId: String

    /** このモジュールの初期化前に準備完了している必要があるモジュールの識別子。 */
    val moduleDependencies: Set<String>
        get() = emptySet()

    /** 依存関係が存在しない場合、値が大きいほど先に初期化される。 */
    val modulePriority: Int
        get() = 0

    val state: ShimeState

    fun initialize(context: ShimeContext)

    fun shutdown()
}
