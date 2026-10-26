@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.FLOAT
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.NUMBER
import com.siimkinks.sqlitemagic.SqlType.SHORT
import com.siimkinks.sqlitemagic.internal.identity
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt1")
fun <R> COALESCE(
  first: SqlExpression<R, Required, *>,
  second: SqlExpression<R, *, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 0,
  leading = arrayOf(first, second),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt2")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 1,
  leading = arrayOf(first, second),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt3")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 2,
  leading = arrayOf(first, second, third),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt4")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 3,
  leading = arrayOf(first, second, third, fourth),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param fifth The fifth alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt5")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, SqlNullable, *>,
  fifth: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 4,
  leading = arrayOf(first, second, third, fourth, fifth),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param fifth The fifth alternative
 * @param sixth The sixth alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt6")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, SqlNullable, *>,
  fifth: SqlExpression<R, SqlNullable, *>,
  sixth: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 5,
  leading = arrayOf(first, second, third, fourth, fifth, sixth),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param fifth The fifth alternative
 * @param sixth The sixth alternative
 * @param seventh The seventh alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt7")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, SqlNullable, *>,
  fifth: SqlExpression<R, SqlNullable, *>,
  sixth: SqlExpression<R, SqlNullable, *>,
  seventh: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 6,
  leading = arrayOf(first, second, third, fourth, fifth, sixth, seventh),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param fifth The fifth alternative
 * @param sixth The sixth alternative
 * @param seventh The seventh alternative
 * @param eighth The eighth alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt8")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, SqlNullable, *>,
  fifth: SqlExpression<R, SqlNullable, *>,
  sixth: SqlExpression<R, SqlNullable, *>,
  seventh: SqlExpression<R, SqlNullable, *>,
  eighth: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 7,
  leading = arrayOf(first, second, third, fourth, fifth, sixth, seventh, eighth),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param third The third alternative
 * @param fourth The fourth alternative
 * @param fifth The fifth alternative
 * @param sixth The sixth alternative
 * @param seventh The seventh alternative
 * @param eighth The eighth alternative
 * @param ninth The ninth alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
@JvmName("coalesceRequiredAt9")
fun <R> COALESCE(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, SqlNullable, *>,
  third: SqlExpression<R, SqlNullable, *>,
  fourth: SqlExpression<R, SqlNullable, *>,
  fifth: SqlExpression<R, SqlNullable, *>,
  sixth: SqlExpression<R, SqlNullable, *>,
  seventh: SqlExpression<R, SqlNullable, *>,
  eighth: SqlExpression<R, SqlNullable, *>,
  ninth: SqlExpression<R, Required, *>,
  vararg rest: SqlExpression<R, *, *>
) = requiredCall(
  function = "COALESCE",
  requiredBy = 8,
  leading = arrayOf(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth),
  rest = rest
)

/**
 * The coalesce() function returns a copy of its first non-NULL argument, or NULL if all arguments are NULL.
 * Coalesce() must have at least 2 arguments.
 *
 * The result is not nullable when one of the first nine alternatives is not nullable.
 *
 * Example:
 * ```
 * COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The second alternative
 * @param rest Additional alternatives
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#coalesce)
 */
fun <R> COALESCE(
  first: SqlExpression<R, *, *>,
  second: SqlExpression<R, *, *>,
  vararg rest: SqlExpression<R, *, *>
) = nullableCall(
  function = "COALESCE",
  leading = arrayOf(first, second),
  rest = rest
)

/**
 * The ifnull() function returns a copy of its first non-NULL argument, or NULL if both arguments are NULL.
 *
 * The result is not nullable when either alternative is not nullable.
 *
 * Example:
 * ```
 * IFNULL(AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The alternative used when [first] is NULL
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#ifnull)
 */
@JvmName("ifNullRequiredFirst")
fun <R> IFNULL(
  first: SqlExpression<R, Required, *>,
  second: SqlExpression<R, *, *>
) = requiredCall(
  function = "IFNULL",
  requiredBy = 0,
  leading = arrayOf(first, second),
  rest = noAlternatives
)

/**
 * The ifnull() function returns a copy of its first non-NULL argument, or NULL if both arguments are NULL.
 *
 * The result is not nullable when either alternative is not nullable.
 *
 * Example:
 * ```
 * IFNULL(AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The alternative used when [first] is NULL
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#ifnull)
 */
@JvmName("ifNullRequiredSecond")
fun <R> IFNULL(
  first: SqlExpression<R, SqlNullable, *>,
  second: SqlExpression<R, Required, *>
) = requiredCall(
  function = "IFNULL",
  requiredBy = 1,
  leading = arrayOf(first, second),
  rest = noAlternatives
)

/**
 * The ifnull() function returns a copy of its first non-NULL argument, or NULL if both arguments are NULL.
 *
 * The result is not nullable when either alternative is not nullable.
 *
 * Example:
 * ```
 * IFNULL(AUTHOR.NAME, "Unknown".asValue())
 * ```
 *
 * @param first The first alternative
 * @param second The alternative used when [first] is NULL
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if the alternatives are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#ifnull)
 */
fun <R> IFNULL(
  first: SqlExpression<R, *, *>,
  second: SqlExpression<R, *, *>
) = nullableCall(
  function = "IFNULL",
  leading = arrayOf(first, second),
  rest = noAlternatives
)

/**
 * The nullif(X,Y) function returns its first argument if the arguments are different and NULL if the arguments are
 * the same.
 *
 * Example:
 * ```
 * NULLIF(AUTHOR.NAME, "")
 * ```
 *
 * @param first The expression to return
 * @param second The value to compare with [first]
 * @return Expression representing the result of this function
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#nullif)
 */
fun <W, R> NULLIF(
  first: EncodableSqlExpression<W, R, *, *>,
  second: W & Any
) = encodableNullIf(
  first = first,
  second = first.hostValue(second)
)

/**
 * The nullif(X,Y) function returns its first argument if the arguments are different and NULL if the arguments are
 * the same.
 *
 * Example:
 * ```
 * NULLIF(AUTHOR.NAME, BOOK.TITLE)
 * ```
 *
 * @param first The expression to return
 * @param second The expression to compare with [first]
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if [second] is read differently than [first]. They must have the same type and
 *     transformer, or both be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#nullif)
 */
fun <W, R> NULLIF(
  first: EncodableSqlExpression<W, R, *, *>,
  second: SqlExpression<*, *, *>
) = encodableNullIf(
  first = first,
  second = first.nullIfOperand(second)
)

/**
 * The nullif(X,Y) function returns its first argument if the arguments are different and NULL if the arguments are
 * the same.
 *
 * Example:
 * ```
 * NULLIF(COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME), "".asValue())
 * ```
 *
 * @param first The expression to return
 * @param second The expression to compare with [first]
 * @return Expression representing the result of this function
 * @throws IllegalArgumentException if [second] is read differently than [first]. They must have the same type and
 *     transformer, or both be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html#nullif)
 */
fun <R> NULLIF(
  first: SqlExpression<R, *, *>,
  second: SqlExpression<*, *, *>
) = nullableResult(
  node = nullIfCall(
    first = first,
    second = first.nullIfOperand(second)
  ),
  read = first.nonNullRead
)

/** Builder for SQL CASE expression. */
object Case {
  /**
   * Define a WHEN condition of a searched CASE expression.
   *
   * @param condition The condition to test
   * @return CASE expression builder
   */
  infix fun WHEN(condition: SqlExpression<Boolean, *, *>) = SearchedCaseWhen<Nothing, Required>(
    branches = null,
    read = null,
    condition = condition.node
  )

  /**
   * Create a new builder for simple CASE expression.
   *
   * The [base] expression is compared with each WHEN value.
   *
   * @param base The expression to compare
   * @return CASE expression builder
   */
  operator fun <C> invoke(base: SqlExpression<C, *, *>) = SimpleCase<C, Nothing>(
    base = base.node,
    hostMatch = ::identity
  )

  /**
   * Create a new builder for simple CASE expression.
   *
   * The [base] expression is compared with each WHEN value.
   *
   * @param base The expression to compare
   * @return CASE expression builder
   */
  operator fun <W, C> invoke(base: EncodableSqlExpression<W, C, *, *>) = SimpleCase<C, W>(
    base = base.node,
    hostMatch = base::hostValue
  )
}

/**
 * Create a new builder for SQL CASE expression.
 *
 * The result is not nullable when ELSE is defined and all result values are not nullable.
 *
 * Example:
 * ```
 * CASE
 *     .WHEN(AUTHOR.ID GREATER_THAN 100L)
 *     .THEN("Established".asValue())
 *     .ELSE("New".asValue())
 *     .END()
 *
 * CASE(BOOK.STATUS)
 *     .WHEN("draft")
 *     .THEN("Draft".asValue())
 *     .ELSE("Other".asValue())
 *     .END()
 * ```
 *
 * @see [SQLite documentation: The CASE expression](https://www.sqlite.org/lang_expr.html#case)
 */
val CASE = Case

/**
 * Builder for SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 */
class SearchedCaseWhen<out R, out N : SqlNullability> internal constructor(
  internal val branches: CaseBranch?,
  internal val read: ((Cursor, Int) -> R)?,
  internal val condition: ExpressionNode
)

/**
 * Builder for SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 */
class SearchedCaseBranches<out R, out N : SqlNullability> internal constructor(
  branches: CaseBranch,
  read: ((Cursor, Int) -> R)?
) : CaseBranches<R, N>(
  branches = branches,
  read = read
) {
  override val base: ExpressionNode? get() = null

  /**
   * Define a WHEN condition of a searched CASE expression.
   *
   * @param condition The condition to test
   * @return CASE expression builder
   */
  infix fun WHEN(condition: SqlExpression<Boolean, *, *>) = SearchedCaseWhen<R, N>(
    branches = branches,
    read = read,
    condition = condition.node
  )
}

/**
 * Define the result value of the preceding WHEN.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
@JvmName("requiredSearchedThen")
infix fun <R> SearchedCaseWhen<R, Required>.THEN(value: SqlExpression<R, Required, *>) =
  SearchedCaseBranches<R, Required>(
    branches = CaseBranch(
      previous = branches,
      condition = condition,
      value = value.node
    ),
    read = caseRead(
      read = read,
      value = value
    )
  )

/**
 * Define the result value of the preceding WHEN.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
infix fun <R> SearchedCaseWhen<R, *>.THEN(value: SqlExpression<R, *, *>) = SearchedCaseBranches<R, SqlNullable>(
  branches = CaseBranch(
    previous = branches,
    condition = condition,
    value = value.node
  ),
  read = caseRead(
    read = read,
    value = value
  )
)

/**
 * Builder for simple SQL CASE expression.
 *
 * @param C Kotlin type of the base expression values
 * @param W Kotlin type of the values the base expression can be compared with
 */
class SimpleCase<C, W> internal constructor(
  internal val base: ExpressionNode,
  internal val hostMatch: (W & Any) -> ExpressionNode
) {
  /**
   * Define a WHEN value to compare with the base expression.
   *
   * @param match The expression to compare with the base expression
   * @return CASE expression builder
   */
  infix fun WHEN(match: SqlExpression<C?, *, *>) = SimpleCaseWhen<Nothing, Required, C, W>(
    case = this,
    branches = null,
    read = null,
    match = match.node
  )

  /**
   * Define a WHEN value to compare with the base expression.
   *
   * @param match The value to compare with the base expression
   * @return CASE expression builder
   */
  infix fun WHEN(match: W & Any) = SimpleCaseWhen<Nothing, Required, C, W>(
    case = this,
    branches = null,
    read = null,
    match = hostMatch(match)
  )
}

/**
 * Define a WHEN value of another numeric type to compare with the base expression.
 *
 * @param match The expression to compare with the base expression
 * @return CASE expression builder
 */
@JvmName("numericSimpleCaseWhen")
infix fun <C : Number?, W> SimpleCase<C, W>.WHEN(match: SqlExpression<Number?, *, *>) =
  SimpleCaseWhen<Nothing, Required, C, W>(
    case = this,
    branches = null,
    read = null,
    match = match.node
  )

/**
 * Builder for simple SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 * @param C Kotlin type of the base expression values
 * @param W Kotlin type of the values the base expression can be compared with
 */
class SimpleCaseWhen<out R, out N : SqlNullability, C, W> internal constructor(
  internal val case: SimpleCase<C, W>,
  internal val branches: CaseBranch?,
  internal val read: ((Cursor, Int) -> R)?,
  internal val match: ExpressionNode
)

/**
 * Builder for simple SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 * @param C Kotlin type of the base expression values
 * @param W Kotlin type of the values the base expression can be compared with
 */
class SimpleCaseBranches<out R, out N : SqlNullability, C, W> internal constructor(
  internal val case: SimpleCase<C, W>,
  branches: CaseBranch,
  read: ((Cursor, Int) -> R)?
) : CaseBranches<R, N>(
  branches = branches,
  read = read
) {
  override val base get() = case.base

  /**
   * Define a WHEN value to compare with the base expression.
   *
   * @param match The expression to compare with the base expression
   * @return CASE expression builder
   */
  infix fun WHEN(match: SqlExpression<C?, *, *>) = SimpleCaseWhen<R, N, C, W>(
    case = case,
    branches = branches,
    read = read,
    match = match.node
  )

  /**
   * Define a WHEN value to compare with the base expression.
   *
   * @param match The value to compare with the base expression
   * @return CASE expression builder
   */
  infix fun WHEN(match: W & Any) = SimpleCaseWhen<R, N, C, W>(
    case = case,
    branches = branches,
    read = read,
    match = case.hostMatch(match)
  )
}

/**
 * Define a WHEN value of another numeric type to compare with the base expression.
 *
 * @param match The expression to compare with the base expression
 * @return CASE expression builder
 */
@JvmName("numericSimpleCaseBranchesWhen")
infix fun <R, N : SqlNullability, C : Number?, W> SimpleCaseBranches<R, N, C, W>.WHEN(
  match: SqlExpression<Number?, *, *>
) = SimpleCaseWhen<R, N, C, W>(
  case = case,
  branches = branches,
  read = read,
  match = match.node
)

/**
 * Define the result value of the preceding WHEN.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
@JvmName("requiredSimpleThen")
infix fun <R, C, W> SimpleCaseWhen<R, Required, C, W>.THEN(value: SqlExpression<R, Required, *>) =
  SimpleCaseBranches<R, Required, C, W>(
    case = case,
    branches = CaseBranch(
      previous = branches,
      condition = match,
      value = value.node
    ),
    read = caseRead(
      read = read,
      value = value
    )
  )

/**
 * Define the result value of the preceding WHEN.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
infix fun <R, C, W> SimpleCaseWhen<R, *, C, W>.THEN(value: SqlExpression<R, *, *>) =
  SimpleCaseBranches<R, SqlNullable, C, W>(
    case = case,
    branches = CaseBranch(
      previous = branches,
      condition = match,
      value = value.node
    ),
    read = caseRead(
      read = read,
      value = value
    )
  )

/**
 * Builder for SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 */
abstract class CaseBranches<out R, out N : SqlNullability> internal constructor(
  internal val branches: CaseBranch,
  internal val read: ((Cursor, Int) -> R)?
) {
  internal abstract val base: ExpressionNode?

  /**
   * Complete the CASE expression.
   *
   * Without ELSE the result is NULL when no WHEN matches.
   *
   * @return Expression
   */
  fun END() = nullableResult(
    node = node(otherwise = null),
    read = read
  )

  internal fun node(otherwise: ExpressionNode?) = ExpressionNode.Case(
    base = base,
    branches = branches.nodes(),
    otherwise = otherwise
  )
}

/**
 * Define the result value when no WHEN matches.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
@JvmName("requiredElse")
infix fun <R> CaseBranches<R, Required>.ELSE(value: SqlExpression<R, Required, *>) = CaseElse(
  requiredResult(
    node = node(value.node),
    read = caseRead(
      read = read,
      value = value
    )
  )
)

/**
 * Define the result value when no WHEN matches.
 *
 * @param value The result value
 * @return CASE expression builder
 * @throws IllegalArgumentException if the result values are read differently. They must have the same type and
 *     transformer, or all be `Long`, `Int`, `Short`, `Double`, `Float` or `Number` values without a transformer,
 *     which are then read as `Number`
 */
infix fun <R> CaseBranches<R, *>.ELSE(value: SqlExpression<R, *, *>) = CaseElse(
  nullableResult(
    node = node(value.node),
    read = caseRead(
      read = read,
      value = value
    )
  )
)

/**
 * Builder for SQL CASE expression.
 *
 * @param R Kotlin type of the result values
 * @param N Result nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when this expression is selected)
 */
class CaseElse<out R, out N : SqlNullability, out T> internal constructor(
  private val expression: SqlExpression<R, N, T>
) {
  /**
   * Complete the CASE expression.
   *
   * @return Expression
   */
  fun END() = expression
}

internal class CaseBranch(
  private val previous: CaseBranch?,
  private val condition: ExpressionNode,
  private val value: ExpressionNode
) {
  private val count: Int = (previous?.count ?: 0) + 1

  fun nodes(): List<ExpressionNode> = ArrayList<ExpressionNode>(count * 2).also(::addTo)

  private fun addTo(nodes: MutableList<ExpressionNode>) {
    previous?.addTo(nodes)
    nodes += condition
    nodes += value
  }
}

private fun <R> requiredCall(
  function: String,
  requiredBy: Int,
  leading: Array<out SqlExpression<R, *, *>>,
  rest: Array<out SqlExpression<R, *, *>>
) = requiredResult(
  node = callNode(
    function = function,
    requiredBy = requiredBy,
    leading = leading,
    rest = rest
  ),
  read = callRead(
    function = function,
    leading = leading,
    rest = rest
  )
)

private fun <R> nullableCall(
  function: String,
  leading: Array<out SqlExpression<R, *, *>>,
  rest: Array<out SqlExpression<R, *, *>>
) = nullableResult(
  node = callNode(
    function = function,
    requiredBy = -1,
    leading = leading,
    rest = rest
  ),
  read = callRead(
    function = function,
    leading = leading,
    rest = rest
  )
)

private val noAlternatives = emptyArray<SqlExpression<Nothing, *, *>>()

private fun callNode(
  function: String,
  requiredBy: Int,
  leading: Array<out SqlExpression<*, *, *>>,
  rest: Array<out SqlExpression<*, *, *>>
) = ExpressionNode.Call(
  function = function,
  arguments = buildList(leading.size + rest.size) {
    leading.mapTo(
      destination = this,
      transform = SqlExpression<*, *, *>::node
    )
    rest.mapTo(
      destination = this,
      transform = SqlExpression<*, *, *>::node
    )
  },
  requiredBy = requiredBy
)

private fun <W, R> encodableNullIf(
  first: EncodableSqlExpression<W, R, *, *>,
  second: ExpressionNode
) = EncodableSqlExpression(
  node = nullIfCall(
    first = first,
    second = second
  ),
  nullness = SqlNullable,
  nonNullRead = first.nonNullRead,
  selectRead = contextualRead(first.nonNullRead),
  encode = first.encode
)

private fun nullIfCall(first: SqlExpression<*, *, *>, second: ExpressionNode) = ExpressionNode.Call(
  function = "NULLIF",
  arguments = listOf(first.node, second),
  requiredBy = -1
)

private fun SqlExpression<*, *, *>.nullIfOperand(second: SqlExpression<*, *, *>) = second.node.also {
  mergedRead(
    read = nonNullRead,
    other = second.nonNullRead,
    function = "NULLIF"
  )
}

private fun <R> requiredResult(
  node: ExpressionNode,
  read: ((Cursor, Int) -> R)?
) = (read ?: nullResultRead()).let { resultRead ->
  SqlExpression(
    node = node,
    nonNullRead = resultRead,
    selectRead = resultRead,
    nullness = Required
  )
}

private fun <R> nullableResult(
  node: ExpressionNode,
  read: ((Cursor, Int) -> R)?
) = (read ?: nullResultRead()).let { resultRead ->
  SqlExpression(
    node = node,
    nonNullRead = resultRead,
    selectRead = contextualRead(resultRead),
    nullness = SqlNullable
  )
}

@Suppress("UNCHECKED_CAST")
private fun <R> nullResultRead() = NULL.nonNullRead as (Cursor, Int) -> R

private fun <R> callRead(
  function: String,
  leading: Array<out SqlExpression<R, *, *>>,
  rest: Array<out SqlExpression<R, *, *>>
): ((Cursor, Int) -> R)? {
  var read: ((Cursor, Int) -> R)? = null
  for (alternatives in arrayOf(leading, rest)) {
    for (alternative in alternatives) {
      read = mergedRead(
        read = read,
        other = alternative.nonNullRead,
        function = function
      )
    }
  }
  return read
}

private fun <R> caseRead(
  read: ((Cursor, Int) -> R)?,
  value: SqlExpression<R, *, *>
) = mergedRead(
  read = read,
  other = value.nonNullRead,
  function = "CASE"
)

@Suppress("UNCHECKED_CAST")
private fun <R> mergedRead(
  read: ((Cursor, Int) -> R)?,
  other: (Cursor, Int) -> R,
  function: String
): ((Cursor, Int) -> R)? = when {
  other === NULL.nonNullRead -> read
  read == null || read === other -> other
  read.isNativeNumber() && other.isNativeNumber() -> NUMBER.nonNullRead as (Cursor, Int) -> R
  else -> throw IllegalArgumentException(
    "$function values must decode alike: one type with one transformer, or native numbers read as Number"
  )
}

private val nativeNumberReads = arrayOf(
  LONG.nonNullRead,
  INT.nonNullRead,
  SHORT.nonNullRead,
  DOUBLE.nonNullRead,
  FLOAT.nonNullRead,
  NUMBER.nonNullRead
)

private fun ((Cursor, Int) -> Any?).isNativeNumber() = nativeNumberReads.any { it === this }
