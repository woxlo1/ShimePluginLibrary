package shimeserver.shimepluginlibrary.shimedatabase

import java.sql.PreparedStatement
import java.sql.Types
import java.math.BigDecimal
import java.util.UUID

/**
 * [PreparedStatement] への値のバインドを 1 か所にまとめた内部ヘルパー。
 *
 * UUID、[BigDecimal]、列挙型、バイト配列を JDBC が理解できる表現へ変換します。SQL 文字列は呼び出し側が
 * 固定テンプレートとして用意し、値だけをここへ渡す設計です。
 */
internal object ShimeSqlBinder {
    fun bind(statement: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { index, value ->
            bindValue(statement, index + 1, value)
        }
    }

    fun bindValue(statement: PreparedStatement, position: Int, value: Any?) {
        when (value) {
            null -> statement.setNull(position, Types.NULL)
            is UUID -> statement.setString(position, value.toString())
            is BigDecimal -> statement.setBigDecimal(position, value)
            is Enum<*> -> statement.setString(position, value.name)
            is ByteArray -> statement.setBytes(position, value)
            else -> statement.setObject(position, value)
        }
    }
}
