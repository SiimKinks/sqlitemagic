@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue

/**
 * Rank of a matching row with every column of [index] weighing the same: lower is a better match.
 *
 * It equals [Fts5Table.RANK] in a query that searches [index] with `MATCH`. In a query that does not, it is 0 for every
 * row, while RANK is NULL.
 *
 * Example:
 * ```
 * SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite") ORDER_BY BM25(ARTICLE_FTS).ASC()
 * ```
 *
 * @param index Full-text index that the query searches
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The bm25() function](https://www.sqlite.org/fts5.html#the_bm25_function)
 */
fun BM25(index: Fts5Table) = requiredNativeResult(
  node = ExpressionNode.Call(
    function = "bm25",
    arguments = listOf(index.table.operand),
    requiredBy = 0
  ),
  type = REAL
)

/**
 * Rank of a matching row with weighted columns: lower is a better match.
 *
 * A match in a column with a higher weight ranks the row better. Columns that no weight names weigh 1.0. All the
 * columns belong to the same index. In a query that does not search it with `MATCH`, it is 0 for every row.
 *
 * Example:
 * ```
 * SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite") ORDER_BY BM25(ARTICLE_FTS.TITLE to 10.0).ASC()
 * ```
 *
 * @param weight Column of the index and its weight
 * @param weights Other columns of the same index and their weights
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the columns belong to different indexes, a column is weighed twice or a weight is
 *     NaN
 * @see [SQLite documentation: The bm25() function](https://www.sqlite.org/fts5.html#the_bm25_function)
 */
fun BM25(
  weight: Pair<Fts5Column<*, *, *, *, *, *>, Double>,
  vararg weights: Pair<Fts5Column<*, *, *, *, *, *>, Double>
): EncodableSqlExpression<Double, Double, Required, Double> {
  val columns = arrayOf(weight, *weights)
  val index = weight.first.index
  val byPosition = arrayOfNulls<Double>(columns.maxOf { it.first.position } + 1)
  columns.forEach { (column, columnWeight) ->
    require(column.index === index) {
      "BM25 weighs the columns of one full-text index, not of both ${index.referenceName} and " +
          column.index.referenceName
    }
    require(byPosition[column.position] == null) { "BM25 weighs ${index.referenceName}.${column.name} twice" }
    byPosition[column.position] = columnWeight
  }
  return requiredNativeResult(
    node = ExpressionNode.Call(
      function = "bm25",
      arguments = buildList(capacity = byPosition.size + 1) {
        add(index.operand)
        byPosition.mapTo(this) { columnWeight ->
          when (columnWeight) {
            null -> DEFAULT_WEIGHT
            else -> ExpressionNode.Literal(SqlStorage.DOUBLE.encode(columnWeight))
          }
        }
      },
      requiredBy = 0
    ),
    type = REAL
  )
}

/**
 * Text of [column] with every match of the search between [open] and [close].
 *
 * In a query that does not search the index of [column] with `MATCH`, it is the text of [column] without marks.
 *
 * Example:
 * ```
 * SELECT COLUMN HIGHLIGHT(ARTICLE_FTS.TITLE, "<b>", "</b>") FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite")
 * ```
 *
 * @param column Column of the searched index
 * @param open Text inserted before each match
 * @param close Text inserted after each match
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The highlight() function](https://www.sqlite.org/fts5.html#the_highlight_function)
 */
@JvmName("requiredHighlight")
fun HIGHLIGHT(column: Fts5Column<*, Required, *, *, *, *>, open: String, close: String) = requiredNativeResult(
  node = highlight(
    column = column,
    open = open,
    close = close
  ),
  type = TEXT
)

/**
 * Text of [column] with every match of the search between [open] and [close], or NULL when the column is NULL.
 *
 * In a query that does not search the index of [column] with `MATCH`, it is the text of [column] without marks.
 *
 * Example:
 * ```
 * SELECT COLUMN HIGHLIGHT(ARTICLE_FTS.SUMMARY, "<b>", "</b>") FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite")
 * ```
 *
 * @param column Column of the searched index
 * @param open Text inserted before each match
 * @param close Text inserted after each match
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The highlight() function](https://www.sqlite.org/fts5.html#the_highlight_function)
 */
fun HIGHLIGHT(column: Fts5Column<*, *, *, *, *, *>, open: String, close: String) = nullableNativeResult(
  node = highlight(
    column = column,
    open = open,
    close = close
  ),
  type = TEXT
)

/**
 * Fragment of [column] around the matches of the search, with every match between [open] and [close].
 *
 * The fragment has at most [tokens] tokens (up to 64) and [ellipsis] where it cuts the text. In a query that does
 * not search the index of [column] with `MATCH`, it is the start of the column text without marks in FTS5 and empty
 * text in FTS4.
 *
 * Example:
 * ```
 * SELECT COLUMN SNIPPET(ARTICLE_FTS.BODY, "<b>", "</b>", "…", 10) FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite")
 * ```
 *
 * @param column Column of the searched index
 * @param open Text inserted before each match
 * @param close Text inserted after each match
 * @param ellipsis Text inserted where the fragment cuts the column text
 * @param tokens Maximum number of tokens in the fragment
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The snippet() function](https://www.sqlite.org/fts5.html#the_snippet_function)
 * @see [SQLite documentation: The snippet() function of FTS4](https://www.sqlite.org/fts3.html#snippet)
 */
@JvmName("requiredSnippet")
fun SNIPPET(
  column: FullTextColumn<*, Required, *, *, *, *>,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = requiredNativeResult(
  node = snippet(
    column = column,
    open = open,
    close = close,
    ellipsis = ellipsis,
    tokens = tokens
  ),
  type = TEXT
)

/**
 * Fragment of [column] around the matches of the search, with every match between [open] and [close], or NULL when
 * the column is NULL.
 *
 * The fragment has at most [tokens] tokens (up to 64) and [ellipsis] where it cuts the text. In a query that does
 * not search the index of [column] with `MATCH`, it is the start of the column text without marks in FTS5 and empty
 * text in FTS4.
 *
 * Example:
 * ```
 * SELECT COLUMN SNIPPET(ARTICLE_FTS.SUMMARY, "<b>", "</b>", "…", 10) FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite")
 * ```
 *
 * @param column Column of the searched index
 * @param open Text inserted before each match
 * @param close Text inserted after each match
 * @param ellipsis Text inserted where the fragment cuts the column text
 * @param tokens Maximum number of tokens in the fragment
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The snippet() function](https://www.sqlite.org/fts5.html#the_snippet_function)
 * @see [SQLite documentation: The snippet() function of FTS4](https://www.sqlite.org/fts3.html#snippet)
 */
fun SNIPPET(
  column: FullTextColumn<*, *, *, *, *, *>,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = nullableNativeResult(
  node = snippet(
    column = column,
    open = open,
    close = close,
    ellipsis = ellipsis,
    tokens = tokens
  ),
  type = TEXT
)

/**
 * Fragment of the column that best matches the search, with every match between [open] and [close].
 *
 * The fragment has at most [tokens] tokens (up to 64) and [ellipsis] where it cuts the text. In a query that does
 * not search [index] with `MATCH`, it is the start of the first column of [index] without marks, or NULL when that
 * column is NULL, in FTS5 and empty text in FTS4.
 *
 * Example:
 * ```
 * SELECT COLUMN SNIPPET(ARTICLE_FTS, "<b>", "</b>", "…", 10) FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite")
 * ```
 *
 * @param index Full-text index that the query searches
 * @param open Text inserted before each match
 * @param close Text inserted after each match
 * @param ellipsis Text inserted where the fragment cuts the column text
 * @param tokens Maximum number of tokens in the fragment
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The snippet() function](https://www.sqlite.org/fts5.html#the_snippet_function)
 * @see [SQLite documentation: The snippet() function of FTS4](https://www.sqlite.org/fts3.html#snippet)
 */
fun SNIPPET(
  index: FullTextTable,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = nullableNativeResult(
  node = when (index) {
    is Fts5Table -> fts5Snippet(
      index = index.table,
      position = ANY_COLUMN,
      open = open,
      close = close,
      ellipsis = ellipsis,
      tokens = tokens
    )
    is Fts4Table -> fts4Snippet(
      index = index.table,
      position = ANY_COLUMN,
      open = open,
      close = close,
      ellipsis = ellipsis,
      tokens = tokens
    )
  },
  type = TEXT
)

/**
 * Positions of the matches of the search in the columns of [index].
 *
 * The text holds four integers per match, separated by spaces: the column, the matched term of the query, and the
 * byte offset and byte size of the match in the column text. In a query that does not search [index] with `MATCH`,
 * it is empty.
 *
 * Example:
 * ```
 * SELECT COLUMN OFFSETS(MEMO_FTS) FROM MEMO WHERE (MEMO_FTS MATCH "sqlite")
 * ```
 *
 * @param index FTS4 full-text index that the query searches
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The offsets() function](https://www.sqlite.org/fts3.html#offsets)
 */
fun OFFSETS(index: Fts4Table) = requiredNativeResult(
  node = ExpressionNode.Call(
    function = "offsets",
    arguments = listOf(index.table.operand),
    requiredBy = 0
  ),
  type = TEXT
)

/**
 * Statistics of the matches of the search in [index], for ranking matches in application code.
 *
 * The value is an array of 32-bit unsigned integers in the byte order of the device, whose content [format]
 * selects. An index declared with `matchinfo = FTS3` supports only the formats `p`, `c` and `x`. In a query that does
 * not search [index] with `MATCH`, it is empty.
 *
 * Example:
 * ```
 * SELECT COLUMN MATCHINFO(MEMO_FTS) FROM MEMO WHERE (MEMO_FTS MATCH "sqlite")
 * ```
 *
 * @param index FTS4 full-text index that the query searches
 * @param format Characters selecting the statistics, `pcx` by default
 * @return Expression representing the result of this function
 * @see [SQLite documentation: The matchinfo() function](https://www.sqlite.org/fts3.html#matchinfo)
 */
fun MATCHINFO(index: Fts4Table, format: String = "pcx") = requiredNativeResult(
  node = ExpressionNode.Call(
    function = "matchinfo",
    arguments = listOf(
      index.table.operand,
      textValue(format)
    ),
    requiredBy = 0
  ),
  type = BLOB
)

private const val ANY_COLUMN = -1L

private val DEFAULT_WEIGHT = ExpressionNode.Literal(NativeValue.Real(1.0))

private fun highlight(
  column: Fts5Column<*, *, *, *, *, *>,
  open: String,
  close: String
) = ExpressionNode.Call(
  function = "highlight",
  arguments = listOf(
    column.index.operand,
    integerLiteral(column.position.toLong()),
    textValue(open),
    textValue(close)
  ),
  requiredBy = 0
)

private fun snippet(
  column: FullTextColumn<*, *, *, *, *, *>,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = when (column) {
  is Fts5Column -> fts5Snippet(
    index = column.index,
    position = column.position.toLong(),
    open = open,
    close = close,
    ellipsis = ellipsis,
    tokens = tokens
  )
  is Fts4Column -> fts4Snippet(
    index = column.index,
    position = column.position.toLong(),
    open = open,
    close = close,
    ellipsis = ellipsis,
    tokens = tokens
  )
}

private fun fts5Snippet(
  index: FullTextIndexTable,
  position: Long,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = ExpressionNode.Call(
  function = "snippet",
  arguments = listOf(
    index.operand,
    integerLiteral(position),
    textValue(open),
    textValue(close),
    textValue(ellipsis),
    integerLiteral(tokens.toLong())
  ),
  requiredBy = 0
)

private fun fts4Snippet(
  index: FullTextIndexTable,
  position: Long,
  open: String,
  close: String,
  ellipsis: String,
  tokens: Int
) = ExpressionNode.Call(
  function = "snippet",
  arguments = listOf(
    index.operand,
    textValue(open),
    textValue(close),
    textValue(ellipsis),
    integerLiteral(position),
    integerLiteral(tokens.toLong())
  ),
  requiredBy = 0
)

private fun integerLiteral(value: Long) = ExpressionNode.Literal(NativeValue.Integer(value))
