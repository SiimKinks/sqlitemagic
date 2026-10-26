package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode

/**
 * Declare a scalar SQL function.
 *
 * The function can be one of SQLite's built-in functions or a function registered on the database connection.
 * Declare the type of its results with `readAs`.
 *
 * Example:
 * ```
 * val NORMALIZE = scalarFunction("normalize_name")
 *     .readAs(SqlType.STRING.nullable)
 *
 * SELECT COLUMN NORMALIZE(AUTHOR.NAME) FROM AUTHOR
 * ```
 *
 * @param name The function name
 * @return Scalar function declaration builder
 * @see [SQLite documentation: Core Functions](https://www.sqlite.org/lang_corefunc.html)
 */
fun scalarFunction(name: String) = ScalarFunctionName(quoteSqlIdentifier(name))

/**
 * Builder for scalar SQL function declaration.
 */
class ScalarFunctionName internal constructor(private val quotedName: String) {
  /**
   * Define the type of this function's results.
   *
   * The results can be compared with values of this type.
   *
   * @param descriptor Type of the results
   * @return Scalar function
   */
  fun <W, R, N : SqlNullability, T> readAs(descriptor: EncodableReadType<W, R, N, T>) = EncodableScalarFunction(
    quotedName = quotedName,
    encodableDescriptor = descriptor,
    dependencies = QueryDependencies.NONE
  )

  /**
   * Define the type of this function's results.
   *
   * @param descriptor Type of the results
   * @return Scalar function
   */
  fun <R, N : SqlNullability, T> readAs(descriptor: ReadType<R, N, T>) = ScalarFunction(
    quotedName = quotedName,
    descriptor = descriptor,
    dependencies = QueryDependencies.NONE
  )
}

/**
 * Declared scalar SQL function.
 *
 * Call it with arguments to create an expression. The nullability of its results is defined by the declared type
 * alone.
 *
 * @param R Kotlin type of the function results
 * @param N Result nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the result is selected)
 */
open class ScalarFunction<out R, out N : SqlNullability, out T> internal constructor(
  internal val quotedName: String,
  private val descriptor: ReadType<R, N, T>,
  private val dependencies: QueryDependencies
) {
  /**
   * Call this function.
   *
   * @param arguments Function arguments: expressions, `null` or values of type `String`, `Long`, `Int`, `Short`,
   *     `Double`, `Float`, `Boolean`, `Byte` or `ByteArray`
   * @return Expression representing the result of this function
   * @throws IllegalArgumentException if an argument is a query or a value of another type
   */
  open operator fun invoke(vararg arguments: Any?): SqlExpression<R, N, T> = SqlExpression(
    node = call(arguments),
    nonNullRead = descriptor.nonNullRead,
    selectRead = descriptor.selectRead,
    nullness = descriptor.readNullness
  )

  /**
   * Define tables that this function reads.
   *
   * Observed queries that call this function are re-run when these tables change. Calling this method again replaces
   * the previously defined tables.
   *
   * @param table Table that this function reads
   * @param tables Additional tables that this function reads
   * @return Scalar function with the defined tables
   */
  open fun observes(table: Table<*>, vararg tables: Table<*>) = ScalarFunction(
    quotedName = quotedName,
    descriptor = descriptor,
    dependencies = QueryDependencies.declared(
      table = table,
      tables = tables
    )
  )

  internal fun call(arguments: Array<out Any?>) = ExpressionNode.Call(
    function = quotedName,
    arguments = arguments.map(::operandNode),
    requiredBy = -1,
    dependencies = dependencies
  )
}

/**
 * Declared scalar SQL function whose results can be compared with values.
 *
 * @param W Kotlin type of the values the results can be compared with
 * @param R Kotlin type of the function results
 * @param N Result nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the result is selected)
 */
class EncodableScalarFunction<W, out R, out N : SqlNullability, out T> internal constructor(
  quotedName: String,
  private val encodableDescriptor: EncodableReadType<W, R, N, T>,
  dependencies: QueryDependencies
) : ScalarFunction<R, N, T>(
  quotedName = quotedName,
  descriptor = encodableDescriptor,
  dependencies = dependencies
) {
  override fun invoke(vararg arguments: Any?) = EncodableSqlExpression(
    node = call(arguments),
    nullness = encodableDescriptor.readNullness,
    nonNullRead = encodableDescriptor.nonNullRead,
    selectRead = encodableDescriptor.selectRead,
    encode = encodableDescriptor.encode
  )

  override fun observes(table: Table<*>, vararg tables: Table<*>) = EncodableScalarFunction(
    quotedName = quotedName,
    encodableDescriptor = encodableDescriptor,
    dependencies = QueryDependencies.declared(
      table = table,
      tables = tables
    )
  )
}
