package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * A column used in queries and conditions.
 *
 * @param P Parent table type
 * @param R Kotlin type of the column values
 * @param N Column nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this column is selected)
 * @param W Kotlin type of the values this column is compared with and updated to
 * @param PN Nullability of the parameters created from this column -- either [Required] or [SqlNullable]
 * @param PT Return type of the parameters created from this column (when selected)
 */
open class Column<P, R, N : SqlNullability, T, W, PN : SqlNullability, PT> @InternalSqliteMagicApi constructor(
  internal val table: Table<P>,
  internal val name: String,
  nullness: N,
  internal val parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ((Cursor, Int) -> T)? = null,
  alias: String? = null
) : EncodableSqlExpression<W, R, N, T>(
  node = ExpressionNode.Field(
    table = table,
    name = name
  ),
  nullness = nullness,
  nonNullRead = parameterEncoding.nonNullRead,
  selectRead = selectedRead ?: @Suppress("UNCHECKED_CAST") (parameterEncoding.selectRead as (Cursor, Int) -> T),
  encode = parameterEncoding.encode,
  outputAlias = alias
), ParameterSource<W, R, PN, PT>, ReadContract<R, N, T> {

  /**
   * Create an alias for this column.
   *
   * @param alias The alias name
   * @return New column with provided alias
   */
  override infix fun AS(alias: String) = Column(
    table = table,
    name = name,
    nullness = nullness,
    parameterEncoding = parameterEncoding,
    selectedRead = selectRead,
    alias = alias
  )

  /**
   * Create a named parameter for the values of this column.
   *
   * Example:
   * ```
   * val name = AUTHOR.NAME.asParameter("name")
   * val compiled = (SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS name))
   *     .compile()
   * val adas = compiled
   *     .bind(name TO "Ada")
   *     .execute()
   * ```
   *
   * @param name Name of the parameter, used in error messages
   * @return Parameter
   */
  override fun asParameter(name: String) = SqlParameter(
    name = name,
    encoding = parameterEncoding
  )
}

/**
 * A unique column used in queries and conditions.
 *
 * @param P Parent table type
 * @param R Kotlin type of the column values
 * @param N Column nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this column is selected)
 * @param W Kotlin type of the values this column is compared with and updated to
 * @param PN Nullability of the parameters created from this column -- either [Required] or [SqlNullable]
 * @param PT Return type of the parameters created from this column (when selected)
 */
class UniqueColumn<P, R, N : SqlNullability, T, W, PN : SqlNullability, PT> @InternalSqliteMagicApi constructor(
  table: Table<P>,
  name: String,
  nullness: N,
  parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ((Cursor, Int) -> T)? = null,
  alias: String? = null
) : Column<P, R, N, T, W, PN, PT>(
  table = table,
  name = name,
  nullness = nullness,
  parameterEncoding = parameterEncoding,
  selectedRead = selectedRead,
  alias = alias
), Unique<P> {
  override infix fun AS(alias: String) = UniqueColumn(
    table = table,
    name = name,
    nullness = nullness,
    parameterEncoding = parameterEncoding,
    selectedRead = selectRead,
    alias = alias
  )
}
