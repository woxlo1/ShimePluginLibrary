package shimeserver.shimepluginlibrary.shimecommand

/**
 * コマンド引数1つ分のパーサーおよび補完プロバイダー。
 *
 * [parse] は不正な入力に対して [ShimeCommandArgumentException] をスローしなければならず、途中までしか
 * 検証されていない値を返してはならない。[complete] はプライマリスレッド上で呼ばれ、少数の候補リストを返すべきである。
 */
interface ShimeArgument<T> {
    /** 使用方法の行やエラーメッセージで使われる、人間が読める名前。 */
    val name: String

    fun parse(input: String, context: ShimeCommandContext): T

    fun complete(context: ShimeCommandContext, partial: String): List<String> = emptyList()
}
