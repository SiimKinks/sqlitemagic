@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.SqlOperator.MATCH
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.appendQuotedSqlIdentifier
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.compileMutation

/**
 * Full-text index of a table, generated for each `@FullTextIndex`.
 *
 * Search the table with [MATCH] in a query that selects from it: the query joins the index with an `INNER JOIN`
 * directly after the table. Join the index explicitly when the table is a source more than once or only through a
 * relationship. The index has no entity: select from it with `SELECT.COLUMNS(…).FROM(…)`. Write statements do not
 * take it, since its table's triggers keep it in sync. [BM25], [HIGHLIGHT], [SNIPPET], [OFFSETS] and [MATCHINFO] fail
 * with SQLite's error for a row that a `LEFT JOIN` of the index fills with NULLs.
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
   * @see [SQLite documentation: FTS4 queries](https://www.sqlite.org/fts3.html#full_text_index_queries)
   */
  infix fun MATCH(query: String) = binaryPredicate(
    left = table.operand,
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
   * @see [SQLite documentation: FTS4 queries](https://www.sqlite.org/fts3.html#full_text_index_queries)
   */
  infix fun MATCH(query: SqlExpression<String?, *, *>) = binaryPredicate(
    left = table.operand,
    operator = MATCH,
    right = query.node
  )

  /**
   * Create a statement that rebuilds this index from the rows of its table.
   *
   * The triggers of the table keep the index in sync with it, so a rebuild is needed only after something else
   * changed the table, such as a connection that disabled the triggers. Observed queries of the table re-run after
   * it.
   *
   * Example:
   * ```
   * ARTICLE_FTS
   *     .rebuild()
   *     .execute()
   * ```
   *
   * @return SQL statement
   * @see [SQLite documentation: The 'rebuild' command](https://www.sqlite.org/fts5.html#the_rebuild_command)
   * @see [SQLite documentation: The 'rebuild' command of FTS4](https://www.sqlite.org/fts3.html#rebuild)
   */
  @CheckResult
  fun rebuild(): MutationQuery = FullTextCommand(
    raw = table.command("rebuild"),
    tables = arrayOf(table.contentTable.name)
  )

  /**
   * Create a statement that merges the segments of this index, which makes searches faster.
   *
   * Example:
   * ```
   * ARTICLE_FTS
   *     .optimize()
   *     .execute()
   * ```
   *
   * @return SQL statement
   * @see [SQLite documentation: The 'optimize' command](https://www.sqlite.org/fts5.html#the_optimize_command)
   * @see [SQLite documentation: The 'optimize' command of FTS4](https://www.sqlite.org/fts3.html#optimize)
   */
  @CheckResult
  fun optimize(): MutationQuery = FullTextCommand(
    raw = table.command("optimize")
  )

  /**
   * Create a statement that checks this index against the rows of its table.
   *
   * The statement fails with SQLite's error, such as "database disk image is malformed", when the index is
   * corrupt or differs from the rows of the table; [rebuild] repairs it.
   *
   * Example:
   * ```
   * ARTICLE_FTS
   *     .integrityCheck()
   *     .execute()
   * ```
   *
   * @return SQL statement
   * @see [SQLite documentation: integrity-check](https://www.sqlite.org/fts5.html#the_integrity_check_command)
   * @see [SQLite documentation: The 'integrity-check' command of FTS4](https://www.sqlite.org/fts3.html#integcheck)
   */
  @CheckResult
  fun integrityCheck(): MutationQuery = FullTextCommand(
    raw = when (this) {
      is Fts5Table -> table.command(
        command = "integrity-check",
        rank = 1
      )
      is Fts4Table -> table.command("integrity-check")
    }
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
   * In a query that searches this index with [MATCH], it equals [BM25] with every column weighing the same. SQLite
   * returns NULL for it in other queries.
   */
  @Suppress("PropertyName")
  val RANK = Column<Nothing, Double, SqlNullable, Double?, Double?, SqlNullable, Double?>(
    table = table,
    name = "rank",
    nullness = SqlNullable,
    parameterEncoding = SqlType.DOUBLE.nullBypassParameterEncoding()
  )

  /**
   * Full-text query that matches the rows containing every term of free text, such as user input.
   *
   * The text is split at whitespace and NUL characters and every term is quoted as a phrase, so query syntax in the
   * text (`AND`, `OR`, `NOT`, `NEAR`, `*`, `^`, quotes, column filters) is searched for as text and never fails the
   * query. Blank text matches no rows.
   *
   * Example:
   * ```
   * SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH ARTICLE_FTS.terms(input, prefix = true))
   * ```
   *
   * @param text Free text
   * @param prefix Whether the last term also matches the words it begins, for search as you type
   * @return Full-text query, such as `"sqlite" "ro"*` for `sqlite ro` with [prefix]
   */
  fun terms(text: String, prefix: Boolean = false) = buildString {
    var inTerm = false
    text.forEach { character ->
      when {
        character.isWhitespace() || character == '\u0000' -> if (inTerm) {
          append('"')
          inTerm = false
        }
        else -> {
          if (!inTerm) {
            if (isNotEmpty()) {
              append(' ')
            }
            append('"')
            inTerm = true
          }
          if (character == '"') {
            append('"')
          }
          append(character)
        }
      }
    }
    if (inTerm) {
      append('"')
    }
    when {
      isEmpty() -> append('"').append('"')
      prefix -> append('*')
    }
  }

  abstract override infix fun AS(alias: String): Fts5Table
}

/**
 * FTS4 full-text index of a table, generated for each `@FullTextIndex(version = FTS4)`.
 *
 * A column with the `LANGUAGE_ID` role is a plain field of the index: [MATCH] searches language 0 unless the query
 * also compares that field, as in `(MEMO_FTS MATCH "sqlite") AND (MEMO_FTS.LANGUAGE IS 1)`.
 *
 * @see FullTextTable
 */
abstract class Fts4Table @InternalSqliteMagicApi protected constructor(
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
   * Full-text query that matches the rows containing every term of free text, such as user input.
   *
   * The text is split at whitespace and NUL characters and every term is quoted as a phrase, so query syntax in the
   * text (`AND`, `OR`, `NOT`, `NEAR`, `-`, quotes, column filters) is searched for as text and never fails the query.
   * FTS4 cannot quote a `*` or a quote inside a phrase, so they separate words. A term without a letter or digit is
   * left out, since FTS4 matches no row for a phrase without words. Text without terms matches no rows.
   *
   * Example:
   * ```
   * SELECT FROM MEMO WHERE (MEMO_FTS MATCH MEMO_FTS.terms(input, prefix = true))
   * ```
   *
   * @param text Free text
   * @param prefix Whether the last term also matches the words it begins, for search as you type
   * @return Full-text query, such as `"sqlite" "ro*"` for `sqlite ro` with [prefix]
   */
  fun terms(text: String, prefix: Boolean = false) = buildString {
    var start = -1
    var wordsEnd = -1
    for (index in 0..text.length) {
      when {
        index < text.length && !text[index].separatesTerms() -> if (start < 0) {
          start = index
        }
        start >= 0 -> {
          val end = text.letterOrDigitEnd(
            start = start,
            end = index
          )
          if (end > start) {
            if (isNotEmpty()) {
              append(' ')
            }
            append('"')
            appendTermText(
              text = text,
              start = start,
              end = end
            )
            wordsEnd = length
            appendTermText(
              text = text,
              start = end,
              end = index
            )
            append('"')
          }
          start = -1
        }
      }
    }
    if (prefix && wordsEnd >= 0) {
      setLength(wordsEnd)
      append('*')
      append('"')
    }
  }

  abstract override infix fun AS(alias: String): Fts4Table
}

private fun Char.separatesTerms() = isWhitespace() || this == '\u0000'

private fun String.letterOrDigitEnd(start: Int, end: Int): Int {
  var position = end
  while (position > start) {
    val codePoint = codePointBefore(position)
    if (Character.isLetterOrDigit(codePoint)) {
      return position
    }
    position -= Character.charCount(codePoint)
  }
  return start
}

private fun StringBuilder.appendTermText(text: String, start: Int, end: Int) {
  for (index in start..<end) {
    when (val character = text[index]) {
      '"', '*' -> append(' ')
      else -> append(character)
    }
  }
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
sealed class FullTextColumn<R, N : SqlNullability, T, W, PN : SqlNullability, PT>(
  table: Table<Nothing>,
  name: String,
  internal val position: Int,
  nullness: N,
  parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ValueRead<T>?
) : Column<Nothing, R, N, T, W, PN, PT>(
  table = table,
  name = name,
  nullness = nullness,
  parameterEncoding = parameterEncoding,
  selectedRead = selectedRead
) {
  internal val index get() = table as FullTextIndexTable

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
   * @see [SQLite documentation: FTS4 queries](https://www.sqlite.org/fts3.html#full_text_index_queries)
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
   * @see [SQLite documentation: FTS4 queries](https://www.sqlite.org/fts3.html#full_text_index_queries)
   */
  infix fun MATCH(query: SqlExpression<String?, *, *>) = binaryPredicate(
    left = node,
    operator = MATCH,
    right = query.node
  )
}

/**
 * Column of an FTS5 full-text index.
 *
 * @see FullTextColumn
 */
class Fts5Column<R, N : SqlNullability, T, W, PN : SqlNullability, PT> @InternalSqliteMagicApi constructor(
  table: Table<Nothing>,
  name: String,
  position: Int,
  nullness: N,
  parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ValueRead<T>? = null
) : FullTextColumn<R, N, T, W, PN, PT>(
  table = table,
  name = name,
  position = position,
  nullness = nullness,
  parameterEncoding = parameterEncoding,
  selectedRead = selectedRead
)

/**
 * Column of an FTS4 full-text index.
 *
 * @see FullTextColumn
 */
class Fts4Column<R, N : SqlNullability, T, W, PN : SqlNullability, PT> @InternalSqliteMagicApi constructor(
  table: Table<Nothing>,
  name: String,
  position: Int,
  nullness: N,
  parameterEncoding: ParameterEncoding<W, R, PN, PT>,
  selectedRead: ValueRead<T>? = null
) : FullTextColumn<R, N, T, W, PN, PT>(
  table = table,
  name = name,
  position = position,
  nullness = nullness,
  parameterEncoding = parameterEncoding,
  selectedRead = selectedRead
)

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

  val operand = ExpressionNode.Field(
    table = this,
    name = name
  )

  fun command(
    command: String,
    rank: Int? = null
  ) = ExpressionNode.Raw(
    sql = buildString {
      append("INSERT INTO ")
      appendQuotedSqlIdentifier(objectIdentity.schema.qualifier)
      append('.')
      appendQuotedSqlIdentifier(name)
      append('(')
      appendQuotedSqlIdentifier(name)
      if (rank != null) {
        append(""", "rank"""")
      }
      append(") VALUES ('")
      append(command)
      append('\'')
      if (rank != null) {
        append(", ")
        append(rank)
      }
      append(')')
    },
    arguments = emptyList(),
    dependencies = QueryDependencies.NONE
  )

  override fun addDependencies(destination: QueryDependencies.Builder) = contentTable.addDependencies(destination)

  override fun AS(alias: String) = FullTextIndexTable(
    name = name,
    alias = alias,
    contentTable = contentTable,
    contentRowIdColumnName = contentRowIdColumnName
  )
}

private class FullTextCommand(
  private val raw: ExpressionNode.Raw,
  private val tables: Array<String> = emptyArray(),
  private val connection: DbConnectionImpl? = null,
  private val parameterValues: List<ParameterAssignment> = emptyList()
) : MutationQuery {
  override fun usingConnection(connection: DbConnection) = FullTextCommand(
    raw = raw,
    tables = tables,
    connection = connection as DbConnectionImpl,
    parameterValues = parameterValues
  )

  override fun bind(vararg assignments: ParameterAssignment) = FullTextCommand(
    raw = raw,
    tables = tables,
    connection = connection,
    parameterValues = assignments.asList()
  )

  override fun compile() = compileMutation(
    renderable = raw,
    connection = connection,
    tables = tables,
    parameterValues = parameterValues
  )
}
