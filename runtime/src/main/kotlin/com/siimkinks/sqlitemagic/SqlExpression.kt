@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.OrderingDirection.ASC
import com.siimkinks.sqlitemagic.OrderingDirection.DESC
import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext
import com.siimkinks.sqlitemagic.internal.sql.nativeValue

/** An SQL expression or query. It is never bound as a plain value. */
sealed interface SqlOperand

/**
 * An SQL expression used in queries and conditions.
 *
 * Expressions are immutable and can be reused in any number of queries.
 *
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
open class SqlExpression<out R, out N : SqlNullability, out T> internal constructor(
  internal val node: ExpressionNode,
  internal val nullness: N,
  internal val nonNullRead: (Cursor, Int) -> R,
  internal val selectRead: (Cursor, Int) -> T,
  internal val outputAlias: String? = null
) : SqlOperand, SqlOutput {
  internal constructor(
    source: SqlExpression<R, N, T>,
    node: ExpressionNode = source.node,
    outputAlias: String? = source.outputAlias
  ) : this(
    node = node,
    nullness = source.nullness,
    nonNullRead = source.nonNullRead,
    selectRead = source.selectRead,
    outputAlias = outputAlias
  )

  internal val quotedOutputAlias = outputAlias?.let(::quoteSqlIdentifier)

  /**
   * Quoted and qualified name of the column when this expression is a column or a subquery's column, for example
   * `"author"."id"`, which is used when it is interpolated into raw SQL.
   */
  override fun toString() = node.toString()

  /**
   * Create an alias for this expression.
   *
   * Example:
   * ```
   * (BOOK.ID + 1L) AS "next_id"
   * ```
   *
   * @param alias The alias name
   * @return New expression with provided alias
   */
  open infix fun AS(alias: String) = SqlExpression(
    source = this,
    outputAlias = alias
  )

  /**
   * Convert this expression to nullable expression.
   *
   * The SQL stays the same and SQL NULL values are read as `null`. Use it for selecting values that an outer join
   * can leave NULL, for example `BOOK.TITLE.nullable()` after `LEFT_JOIN(BOOK)`, or for matching the type of
   * a nullable column in another compound SELECT.
   *
   * @return Nullable expression
   */
  open fun nullable(): SqlExpression<R, SqlNullable, T?> = SqlExpression(
    node = node,
    nullness = SqlNullable,
    nonNullRead = nonNullRead,
    selectRead = contextualRead(selectRead),
    outputAlias = outputAlias
  )

  /**
   * Compare and order this expression with a collating sequence.
   *
   * Example:
   * ```
   * AUTHOR.NAME COLLATE NOCASE
   * ```
   *
   * @param collation The collating sequence
   * @return Expression with the collating sequence
   * @see [SQLite documentation: Collating Sequences](https://www.sqlite.org/datatype3.html#collation)
   */
  open infix fun COLLATE(collation: Collation) = SqlExpression(
    source = this,
    node = ExpressionNode.Collate(
      operand = node,
      collation = collation.sql
    ),
    outputAlias = null
  )

  /**
   * Create a CAST target for this expression.
   *
   * Example:
   * ```
   * CAST(BOOK.ID AS TEXT)
   * ```
   *
   * @param type The type to convert this expression to
   * @return CAST target to be used in [CAST] function
   */
  infix fun <C> AS(type: CastType<C>) = CastTarget(
    operand = this,
    type = type
  )

  /**
   * This expression = expression.
   *
   * If either operand is NULL then the result is NULL. Use [IS_NULL] to test for NULL.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun IS(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.EQUAL,
    right = other.node
  )

  /**
   * This expression != expression.
   *
   * If either operand is NULL then the result is NULL. Use [IS_NOT_NULL] to test for not NULL.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun IS_NOT(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.NOT_EQUAL,
    right = other.node
  )

  /**
   * This expression IS NULL.
   *
   * @return Expression
   */
  fun IS_NULL() = nullTest(
    operand = node,
    negated = false
  )

  /**
   * This expression IS NOT NULL.
   *
   * @return Expression
   */
  fun IS_NOT_NULL() = nullTest(
    operand = node,
    negated = true
  )

  /**
   * This expression > expression.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun GREATER_THAN(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.GREATER,
    right = other.node
  )

  /**
   * This expression >= expression.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun GREATER_OR_EQUAL(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.GREATER_OR_EQUAL,
    right = other.node
  )

  /**
   * This expression < expression.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun LESS_THAN(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.LESS,
    right = other.node
  )

  /**
   * This expression <= expression.
   *
   * @param other The expression to test against this expression
   * @return Expression
   */
  infix fun LESS_OR_EQUAL(other: SqlExpression<@UnsafeVariance R?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.LESS_OR_EQUAL,
    right = other.node
  )

  /**
   * Create an expression to check this expression against several expressions.
   *
   * SQL: this IN (values...)
   *
   * @param values The expressions to test against this expression
   * @return Expression
   */
  fun IN(vararg values: SqlExpression<@UnsafeVariance R?, *, *>) = membership(
    operand = node,
    values = values.map(SqlExpression<*, *, *>::node),
    negated = false
  )

  /**
   * Create an expression to check this expression against several expressions.
   *
   * SQL: this NOT IN (values...)
   *
   * @param values The expressions to test against this expression
   * @return Expression
   */
  fun NOT_IN(vararg values: SqlExpression<@UnsafeVariance R?, *, *>) = membership(
    operand = node,
    values = values.map(SqlExpression<*, *, *>::node),
    negated = true
  )

  /**
   * Create an expression to check this expression against a subquery.
   * Note that the subquery must select exactly one column.
   *
   * SQL: this IN (SELECT...)
   *
   * @param query The subquery to test against this expression
   * @return Expression
   */
  infix fun IN(query: SelectQuery<*, out ScalarSelection<out @UnsafeVariance R?, *, *>>) = queryMembership(
    operand = node,
    query = query,
    negated = false
  )

  /**
   * Create an expression to check this expression against a subquery.
   * Note that the subquery must select exactly one column.
   *
   * SQL: this NOT IN (SELECT...)
   *
   * @param query The subquery to test against this expression
   * @return Expression
   */
  infix fun NOT_IN(query: SelectQuery<*, out ScalarSelection<out @UnsafeVariance R?, *, *>>) = queryMembership(
    operand = node,
    query = query,
    negated = true
  )

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * Example:
   * ```
   * BOOK.PAGES BETWEEN BOOK.MIN_PAGES AND BOOK.MAX_PAGES
   * ```
   *
   * @param lower The lower bound of the BETWEEN operator
   * @return Between operator builder
   */
  open infix fun BETWEEN(lower: SqlExpression<@UnsafeVariance R?, *, *>) = SqlRangeStart<R>(
    operand = node,
    lower = lower.node,
    negated = false
  )

  /**
   * Create a new builder for SQL NOT BETWEEN operator.
   *
   * Example:
   * ```
   * BOOK.PAGES NOT_BETWEEN BOOK.MIN_PAGES AND BOOK.MAX_PAGES
   * ```
   *
   * @param lower The lower bound of the NOT BETWEEN operator
   * @return Between operator builder
   */
  open infix fun NOT_BETWEEN(lower: SqlExpression<@UnsafeVariance R?, *, *>) = SqlRangeStart<R>(
    operand = node,
    lower = lower.node,
    negated = true
  )

  /**
   * Uses the LIKE operation. Case insensitive comparisons for ASCII characters.
   *
   * Values are matched as text. Follow it with `ESCAPE` to define an escape character.
   *
   * @param pattern Uses sqlite LIKE pattern to match rows.
   *                There are two wildcards: % and _
   *                % represents [0,many) numbers or characters.
   *                The _ represents a single number or character.
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun LIKE(pattern: String) = like(
    operator = SqlOperator.LIKE,
    pattern = textValue(pattern)
  )

  /**
   * Uses the LIKE operation. Case insensitive comparisons for ASCII characters.
   *
   * Values are matched as text. Follow it with `ESCAPE` to define an escape character.
   *
   * @param pattern Uses sqlite LIKE pattern to match rows.
   *                There are two wildcards: % and _
   *                % represents [0,many) numbers or characters.
   *                The _ represents a single number or character.
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun LIKE(pattern: SqlExpression<String?, *, *>) = like(
    operator = SqlOperator.LIKE,
    pattern = pattern.node
  )

  /**
   * Uses the NOT LIKE operation. Case insensitive comparisons for ASCII characters.
   *
   * Values are matched as text. Follow it with `ESCAPE` to define an escape character.
   *
   * @param pattern Uses sqlite LIKE pattern to match rows.
   *                There are two wildcards: % and _
   *                % represents [0,many) numbers or characters.
   *                The _ represents a single number or character.
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun NOT_LIKE(pattern: String) = like(
    operator = SqlOperator.NOT_LIKE,
    pattern = textValue(pattern)
  )

  /**
   * Uses the NOT LIKE operation. Case insensitive comparisons for ASCII characters.
   *
   * Values are matched as text. Follow it with `ESCAPE` to define an escape character.
   *
   * @param pattern Uses sqlite LIKE pattern to match rows.
   *                There are two wildcards: % and _
   *                % represents [0,many) numbers or characters.
   *                The _ represents a single number or character.
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun NOT_LIKE(pattern: SqlExpression<String?, *, *>) = like(
    operator = SqlOperator.NOT_LIKE,
    pattern = pattern.node
  )

  /**
   * Uses the GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * Values are matched as text.
   *
   * @param pattern Uses sqlite GLOB pattern to match rows.
   *                There are two wildcards: * and ?
   *                * represents [0,many) numbers or characters.
   *                The ? represents a single number or character
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun GLOB(pattern: String) = binaryPredicate(
    left = node,
    operator = SqlOperator.GLOB,
    right = textValue(pattern)
  )

  /**
   * Uses the GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * Values are matched as text.
   *
   * @param pattern Uses sqlite GLOB pattern to match rows.
   *                There are two wildcards: * and ?
   *                * represents [0,many) numbers or characters.
   *                The ? represents a single number or character
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun GLOB(pattern: SqlExpression<String?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.GLOB,
    right = pattern.node
  )

  /**
   * Uses the NOT GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * Values are matched as text.
   *
   * @param pattern Uses sqlite GLOB pattern to match rows.
   *                There are two wildcards: * and ?
   *                * represents [0,many) numbers or characters.
   *                The ? represents a single number or character
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun NOT_GLOB(pattern: String) = binaryPredicate(
    left = node,
    operator = SqlOperator.NOT_GLOB,
    right = textValue(pattern)
  )

  /**
   * Uses the NOT GLOB operation. Similar to LIKE except it uses case sensitive comparisons.
   *
   * Values are matched as text.
   *
   * @param pattern Uses sqlite GLOB pattern to match rows.
   *                There are two wildcards: * and ?
   *                * represents [0,many) numbers or characters.
   *                The ? represents a single number or character
   * @return Expression
   * @see [SQLite documentation: Expression](https://www.sqlite.org/lang_expr.html#like)
   */
  infix fun NOT_GLOB(pattern: SqlExpression<String?, *, *>) = binaryPredicate(
    left = node,
    operator = SqlOperator.NOT_GLOB,
    right = pattern.node
  )

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in ascending order.
   *
   * @return Ordering term to be used in `ORDER_BY` method.
   */
  fun ASC() = Ordering(
    node = node,
    direction = ASC
  )

  /**
   * Create ORDER BY ordering term.
   *
   * Orders rows in descending order.
   *
   * @return Ordering term to be used in `ORDER_BY` method.
   */
  fun DESC() = Ordering(
    node = node,
    direction = DESC
  )

  private fun like(operator: SqlOperator, pattern: ExpressionNode) = LikeSqlExpression(
    ExpressionNode.Binary(
      left = node,
      operator = operator,
      right = pattern
    )
  )
}

internal fun <C> requiredNativeResult(
  node: ExpressionNode,
  type: CastType<C>
) = EncodableSqlExpression(
  node = node,
  nullness = Required,
  nonNullRead = type.read,
  selectRead = type.read,
  encode = type.encode
)

internal fun <C> nullableNativeResult(
  node: ExpressionNode,
  type: CastType<C>
) = EncodableSqlExpression(
  node = node,
  nullness = SqlNullable,
  nonNullRead = type.read,
  selectRead = type.nullableRead,
  encode = type.encode
)

/**
 * An SQL expression that can be compared with Kotlin values.
 *
 * Values are bound as query arguments and converted to SQL in the same way as this expression's own values,
 * including a column's transformer. Values cannot be `null`; use [IS_NULL] and [IS_NOT_NULL] to test for NULL.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
open class EncodableSqlExpression<W, out R, out N : SqlNullability, out T> internal constructor(
  node: ExpressionNode,
  nullness: N,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  internal val encode: (W) -> NativeValue,
  outputAlias: String? = null
) : SqlExpression<R, N, T>(
  node = node,
  nullness = nullness,
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  outputAlias = outputAlias
) {
  internal constructor(
    source: EncodableSqlExpression<W, R, N, T>,
    node: ExpressionNode = source.node,
    outputAlias: String? = source.outputAlias
  ) : this(
    node = node,
    nullness = source.nullness,
    nonNullRead = source.nonNullRead,
    selectRead = source.selectRead,
    encode = source.encode,
    outputAlias = outputAlias
  )

  override infix fun AS(alias: String) = EncodableSqlExpression(
    source = this,
    outputAlias = alias
  )

  override fun nullable() = NullableEncodableExpression(this)

  override infix fun COLLATE(collation: Collation) = EncodableSqlExpression(
    source = this,
    node = ExpressionNode.Collate(
      operand = node,
      collation = collation.sql
    ),
    outputAlias = null
  )

  /**
   * This expression = value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun IS(value: W & Any) = hostComparison(
    operator = SqlOperator.EQUAL,
    value = value
  )

  /**
   * This expression != value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun IS_NOT(value: W & Any) = hostComparison(
    operator = SqlOperator.NOT_EQUAL,
    value = value
  )

  /**
   * This expression > value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun GREATER_THAN(value: W & Any) = hostComparison(
    operator = SqlOperator.GREATER,
    value = value
  )

  /**
   * This expression >= value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun GREATER_OR_EQUAL(value: W & Any) = hostComparison(
    operator = SqlOperator.GREATER_OR_EQUAL,
    value = value
  )

  /**
   * This expression < value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun LESS_THAN(value: W & Any) = hostComparison(
    operator = SqlOperator.LESS,
    value = value
  )

  /**
   * This expression <= value.
   *
   * @param value The value to test against this expression
   * @return Expression
   */
  infix fun LESS_OR_EQUAL(value: W & Any) = hostComparison(
    operator = SqlOperator.LESS_OR_EQUAL,
    value = value
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this IN (values...)
   *
   * An empty collection matches no rows.
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  infix fun IN(values: Collection<W & Any>) = hostIn(
    values = values.map(::hostArgument),
    negated = false
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this IN (values...)
   *
   * An empty array matches no rows.
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  @JvmName("inArray")
  infix fun IN(values: Array<out W & Any>) = hostIn(
    values = values.map(::hostArgument),
    negated = false
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this IN (values...)
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  fun IN(vararg values: W & Any) = hostIn(
    values = values.map(::hostArgument),
    negated = false
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this NOT IN (values...)
   *
   * An empty collection matches all rows.
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  infix fun NOT_IN(values: Collection<W & Any>) = hostIn(
    values = values.map(::hostArgument),
    negated = true
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this NOT IN (values...)
   *
   * An empty array matches all rows.
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  @JvmName("notInArray")
  infix fun NOT_IN(values: Array<out W & Any>) = hostIn(
    values = values.map(::hostArgument),
    negated = true
  )

  /**
   * Create an expression to check this expression against several values.
   *
   * SQL: this NOT IN (values...)
   *
   * @param values The values to test against this expression
   * @return Expression
   */
  fun NOT_IN(vararg values: W & Any) = hostIn(
    values = values.map(::hostArgument),
    negated = true
  )

  /**
   * Create a new builder for SQL BETWEEN operator.
   *
   * Example:
   * ```
   * BOOK.PAGES BETWEEN 100L AND 200L
   * ```
   *
   * @param lower The lower bound of the BETWEEN operator
   * @return Between operator builder
   */
  infix fun BETWEEN(lower: W & Any) = EncodableSqlRangeStart(
    receiver = this,
    lower = hostValue(lower),
    negated = false
  )

  override infix fun BETWEEN(lower: SqlExpression<@UnsafeVariance R?, *, *>) = EncodableSqlRangeStart(
    receiver = this,
    lower = lower.node,
    negated = false
  )

  /**
   * Create a new builder for SQL NOT BETWEEN operator.
   *
   * Example:
   * ```
   * BOOK.PAGES NOT_BETWEEN 100L AND 200L
   * ```
   *
   * @param lower The lower bound of the NOT BETWEEN operator
   * @return Between operator builder
   */
  infix fun NOT_BETWEEN(lower: W & Any) = EncodableSqlRangeStart(
    receiver = this,
    lower = hostValue(lower),
    negated = true
  )

  override infix fun NOT_BETWEEN(lower: SqlExpression<@UnsafeVariance R?, *, *>) = EncodableSqlRangeStart(
    receiver = this,
    lower = lower.node,
    negated = true
  )

  private fun hostComparison(operator: SqlOperator, value: W & Any) = binaryPredicate(
    left = node,
    operator = operator,
    right = hostValue(value)
  )

  internal fun hostValue(value: W & Any) = ExpressionNode.Bound(hostArgument(value))

  private fun hostArgument(value: W & Any): NativeValue {
    requireHostValue(value)
    return encode(value)
  }

  private fun hostIn(values: List<NativeValue>, negated: Boolean) = hostMembership(
    operand = node,
    arguments = values,
    negated = negated
  )
}

/**
 * An SQL expression of a bound value or literal.
 *
 * Create one with `asValue` or `asLiteral`.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class EncodedSqlExpression<W, R, N : SqlNullability, T> internal constructor(
  node: ExpressionNode,
  private val parameterEncoding: ParameterEncoding<W, R, N, T>
) : EncodableSqlExpression<W, R, N, T>(
  node = node,
  nullness = parameterEncoding.nullness,
  nonNullRead = parameterEncoding.nonNullRead,
  selectRead = parameterEncoding.selectRead,
  encode = parameterEncoding.encode
), ParameterSource<W, R, N, T> {
  override fun asParameter(name: String) = SqlParameter(
    name = name,
    encoding = parameterEncoding
  )
}

/**
 * Raw SQL expression.
 *
 * Create one with [expr].
 *
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class RawSqlExpression<out R, out N : SqlNullability, out T> internal constructor(
  private val raw: ExpressionNode.Raw,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  nullness: N
) : SqlExpression<R, N, T>(
  node = raw,
  nullness = nullness,
  nonNullRead = nonNullRead,
  selectRead = selectRead
) {
  /**
   * Define tables that this raw SQL reads.
   *
   * Observed queries are re-run when these tables change. Tables that the query selects from are observed anyway,
   * so only the tables that appear in the raw SQL alone, such as in a raw subquery, need to be defined here.
   * Calling this method again replaces the previously defined tables.
   *
   * @param table Table that this SQL reads
   * @param tables Additional tables that this SQL reads
   * @return Raw SQL expression with the defined tables
   */
  fun observes(table: Table<*>, vararg tables: Table<*>) = RawSqlExpression(
    raw = raw.observing(
      table = table,
      tables = tables
    ),
    nonNullRead = nonNullRead,
    selectRead = selectRead,
    nullness = nullness
  )
}

/**
 * Raw SQL expression that can be compared with Kotlin values.
 *
 * Create one with [expr] and a type that converts values to SQL.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param N Expression nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class EncodableRawSqlExpression<W, out R, out N : SqlNullability, out T> internal constructor(
  private val raw: ExpressionNode.Raw,
  nonNullRead: (Cursor, Int) -> R,
  selectRead: (Cursor, Int) -> T,
  nullness: N,
  encode: (W) -> NativeValue
) : EncodableSqlExpression<W, R, N, T>(
  node = raw,
  nullness = nullness,
  nonNullRead = nonNullRead,
  selectRead = selectRead,
  encode = encode
) {
  /**
   * Define tables that this raw SQL reads.
   *
   * Observed queries are re-run when these tables change. Tables that the query selects from are observed anyway,
   * so only the tables that appear in the raw SQL alone, such as in a raw subquery, need to be defined here.
   * Calling this method again replaces the previously defined tables.
   *
   * @param table Table that this SQL reads
   * @param tables Additional tables that this SQL reads
   * @return Raw SQL expression with the defined tables
   */
  fun observes(table: Table<*>, vararg tables: Table<*>) = EncodableRawSqlExpression(
    raw = raw.observing(
      table = table,
      tables = tables
    ),
    nonNullRead = nonNullRead,
    selectRead = selectRead,
    nullness = nullness,
    encode = encode
  )
}

/** ORDER BY ordering term. */
class Ordering internal constructor(
  internal val node: ExpressionNode,
  private val direction: OrderingDirection
) {
  internal fun render(context: SqlRenderingContext) {
    node.render(context)
    context.sql
      .append(' ')
      .append(direction.name)
  }
}

internal enum class OrderingDirection {
  ASC,
  DESC
}

internal fun requireHostValue(value: Any?) {
  require(value !is SqlOperand) { "SQL expressions and queries are not host values" }
}

internal fun operandNode(value: Any?): ExpressionNode = when (value) {
  is SqlExpression<*, *, *> -> value.node
  else -> {
    requireHostValue(value)
    ExpressionNode.Bound(nativeValue(value))
  }
}

/**
 * Create SQL expression from raw SQL string.
 *
 * Columns render as their quoted, qualified names when interpolated into the string.
 *
 * Example:
 * ```
 * "abs(${AUTHOR.ID})".expr(SqlType.NUMBER)
 * ```
 *
 * @param type Type of the expression values
 * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
 *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
 * @return Raw SQL expression
 * @throws IllegalArgumentException if an argument is a value of another type
 */
fun <R, N : SqlNullability, T> String.expr(
  type: ReadType<R, N, T>,
  vararg arguments: Any?
) = RawSqlExpression(
  raw = rawNode(arguments),
  nonNullRead = type.nonNullRead,
  selectRead = type.selectRead,
  nullness = type.readNullness
)

/**
 * Create SQL expression from raw SQL string.
 *
 * Columns render as their quoted, qualified names when interpolated into the string. The expression can be
 * compared with Kotlin values, which are converted to SQL with [type].
 *
 * Example:
 * ```
 * "${AUTHOR.NAME} LIKE ?".expr(SqlType.BOOLEAN, "A%")
 * "lower(${AUTHOR.NAME})".expr(SqlType.STRING) IS "ada"
 * ```
 *
 * @param type Type of the expression values
 * @param arguments Values bound to the `?` placeholders of the SQL: `null` or values of type `String`, `Long`,
 *     `Int`, `Short`, `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
 * @return Raw SQL expression
 * @throws IllegalArgumentException if an argument is a value of another type
 */
fun <W, R, N : SqlNullability, T> String.expr(
  type: EncodableReadType<W, R, N, T>,
  vararg arguments: Any?
) = EncodableRawSqlExpression(
  raw = rawNode(arguments),
  nonNullRead = type.nonNullRead,
  selectRead = type.selectRead,
  nullness = type.readNullness,
  encode = type.encode
)

private fun String.rawNode(arguments: Array<out Any?>) = ExpressionNode.Raw(
  sql = this,
  arguments = arguments.map(::nativeValue),
  dependencies = QueryDependencies.NONE
)

/**
 * An expression that can be compared with Kotlin values, read as nullable.
 *
 * Create one with `nullable()`. SQL NULL values are read as `null`.
 *
 * @param W Kotlin type of the values this expression can be compared with
 * @param R Kotlin type of the expression values
 * @param T Return type (when this expression is selected)
 */
class NullableEncodableExpression<W, out R, out T> internal constructor(
  expression: EncodableSqlExpression<W, R, *, T>
) : EncodableSqlExpression<W, R, SqlNullable, T?>(
  node = expression.node,
  nullness = SqlNullable,
  nonNullRead = expression.nonNullRead,
  selectRead = contextualRead(expression.selectRead),
  encode = expression.encode,
  outputAlias = expression.outputAlias
)
