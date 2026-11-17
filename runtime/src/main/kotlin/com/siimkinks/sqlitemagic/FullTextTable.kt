@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.SqlOperator.MATCH
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Full-text index of a table, generated for each `@FullTextIndex`.
 *
 * Search the table with [MATCH] in a query that selects from it: the query joins the index with an `INNER JOIN`
 * directly after the table. Join the index explicitly when the table is a source more than once or only through a
 * relationship. The index has no entity: select from it with `SELECT.COLUMNS(…).FROM(…)`. Write statements do not
 * take it, since its table's triggers keep it in sync.
 *
 * Example:
 * ```
 * SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite") ORDER_BY ARTICLE_FTS.RANK.ASC()
 * ```
 */
sealed class FullTextTable(
  name: String,
  alias: String?,
  contentTable: Table<*>,
  contentRowIdColumnName: String
) : Source {
  internal val table = FullTextIndexTable(
    name = name,
    alias = alias,
    contentTable = contentTable,
    contentRowIdColumnName = contentRowIdColumnName
  )

  @InternalSqliteMagicApi
  protected val fieldTable: Table<Nothing> get() = table

  private val operand = ExpressionNode.Field(
    table = table,
    name = name
  )

  /**
   * Search every indexed column of this index.
   *
   * SQLite reports a malformed query string when the query runs.
   *
   * Example:
   * ```
   * SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite OR room")
   * ```
   *
   * @param query Full-text query
   * @return Expression
   * @see [SQLite documentation: Full-text query syntax](https://www.sqlite.org/fts5.html#full_text_query_syntax)
   */
  infix fun MATCH(query: String) = binaryPredicate(
    left = operand,
    operator = MATCH,
    right = textValue(query)
  )

  /**
   * Search every indexed column of this index.
   *
   * SQLite reports a malformed query string when the query runs.
   *
   * Example:
   * ```
   * val query = ARTICLE.TITLE.asParameter("query")
   * val search = (SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH query))
   *     .compile()
   * ```
   *
   * @param query Full-text query, such as a parameter
   * @return Expression
   * @see [SQLite documentation: Full-text query syntax](https://www.sqlite.org/fts5.html#full_text_query_syntax)
   */
  infix fun MATCH(query: SqlExpression<String?, *, *>) = binaryPredicate(
    left = operand,
    operator = MATCH,
    right = query.node
  )

  /**
   * Create an alias for this index.
   *
   * An aliased index is not joined automatically.
   *
   * @param alias The alias name
   * @return New index with provided alias
   */
  @CheckResult
  abstract infix fun AS(alias: String): FullTextTable

  override fun toString() = table.toString()
}

/**
 * FTS5 full-text index of a table, generated for each `@FullTextIndex`.
 *
 * @see FullTextTable
 */
abstract class Fts5Table @InternalSqliteMagicApi protected constructor(
  name: String,
  alias: String?,
  contentTable: Table<*>,
  contentRowIdColumnName: String
) : FullTextTable(
  name = name,
  alias = alias,
  contentTable = contentTable,
  contentRowIdColumnName = contentRowIdColumnName
) {
  /**
   * Rank of a matching row: lower is a better match. Order by it ascending to get the best matches first.
   *
   * SQLite returns NULL for it in a query that does not search this index with [MATCH].
   */
  @Suppress("PropertyName")
  val RANK = Column<Nothing, Double, SqlNullable, Double?, Double?, SqlNullable, Double?>(
    table = table,
    name = "rank",
    nullness = SqlNullable,
    parameterEncoding = SqlType.DOUBLE.nullBypassParameterEncoding()
  )

  abstract override infix fun AS(alias: String): Fts5Table
}

/**
 * Column of a full-text index.
 *
 * @param R Kotlin type of the column values
 * @param N Column nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this column is selected)
 * @param W Kotlin type of the values this column is compared with
 * @param PN Nullability of the parameters created from this column -- either [Required] or [SqlNullable]
 * @param PT Return type of the parameters created from this column (when selected)
 */
class FullTextColumn<R, N : SqlNullability, T, W, PN : SqlNullability, PT> @InternalSqliteMagicApi constructor(
  table: Table<Nothing>,
  name: String,
  nullness: N,
  parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ValueRead<T>? = null
) : Column<Nothing, R, N, T, W, PN, PT>(
  table = table,
  name = name,
  nullness = nullness,
  parameterEncoding = parameterEncoding,
  selectedRead = selectedRead
) {
  /**
   * Search only this column of its index.
   *
   * Example:
   * ```
   * SELECT FROM ARTICLE WHERE (ARTICLE_FTS.TITLE MATCH "sqlite")
   * ```
   *
   * @param query Full-text query
   * @return Expression
   * @see [SQLite documentation: Full-text query syntax](https://www.sqlite.org/fts5.html#full_text_query_syntax)
   */
  infix fun MATCH(query: String) = binaryPredicate(
    left = node,
    operator = MATCH,
    right = textValue(query)
  )

  /**
   * Search only this column of its index.
   *
   * @param query Full-text query, such as a parameter
   * @return Expression
   * @see [SQLite documentation: Full-text query syntax](https://www.sqlite.org/fts5.html#full_text_query_syntax)
   */
  infix fun MATCH(query: SqlExpression<String?, *, *>) = binaryPredicate(
    left = node,
    operator = MATCH,
    right = query.node
  )
}

internal class FullTextIndexTable(
  name: String,
  alias: String?,
  val contentTable: Table<*>,
  private val contentRowIdColumnName: String
) : Table<Nothing>(
  name = name,
  alias = alias
) {
  val quotedContentRowIdColumnName = quoteSqlIdentifier(contentRowIdColumnName)

  override fun addDependencies(destination: QueryDependencies.Builder) = contentTable.addDependencies(destination)

  override fun AS(alias: String) = FullTextIndexTable(
    name = name,
    alias = alias,
    contentTable = contentTable,
    contentRowIdColumnName = contentRowIdColumnName
  )
}
