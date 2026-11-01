package shimeserver.shimepluginlibrary.shimeintegration

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext

/**
 * 他のプラグインや外部システムへの任意のブリッジ。
 *
 * 利用可否は実行時に確認しなければならない。実装側は対象プラグインが導入されていると仮定しては
 * ならず、[enable] が一度も呼ばれていない場合でも [disable] は安全に呼び出せなければならない。
 */
interface ShimeIntegration {
    /** 検索に使用する安定した識別子。 */
    val id: String

    /** 対象システムが現在利用可能かどうかを返す。 */
    fun isAvailable(): Boolean

    /** インテグレーションを有効化する。有効化が成功するごとに最大 1 回呼び出される。 */
    fun enable(context: ShimeContext)

    /** インテグレーションが保持するリソースを解放する。冪等でなければならない。 */
    fun disable()
}
