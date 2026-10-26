@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.ParameterEncoding
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import com.siimkinks.sqlitemagic.internal.sql.ParameterIdentity

/**
 * Expression that can create named parameters.
 *
 * @param W Kotlin type of the parameter values
 * @param R Kotlin type of the read parameter values
 * @param N Parameter nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the parameter is selected)
 */
sealed interface ParameterSource<W, R, N : SqlNullability, T> {
  /**
   * Create a named parameter, whose value is bound before the statement is executed.
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
  fun asParameter(name: String): SqlParameter<W, R, N, T>
}

/**
 * Named SQL parameter.
 *
 * Parameters are identified by their instance; the name is only used in error messages.
 *
 * @param W Kotlin type of the parameter values
 * @param R Kotlin type of the read parameter values
 * @param N Parameter nullability -- either [Required] or [SqlNullable]
 * @param T Return type (when the parameter is selected)
 */
class SqlParameter<W, R, N : SqlNullability, T> private constructor(
  internal val identity: ParameterIdentity<W>,
  encoding: ParameterEncoding<W, R, N, T>
) : SqlExpression<R, N, T>(
  node = ExpressionNode.Parameter(identity),
  nullness = encoding.nullness,
  nonNullRead = encoding.nonNullRead,
  selectRead = encoding.selectRead
) {
  internal constructor(name: String, encoding: ParameterEncoding<W, R, N, T>) : this(
    identity = ParameterIdentity(
      name = name,
      encode = encoding.encode
    ),
    encoding = encoding
  )

  /**
   * Assign a value to this parameter.
   *
   * The value is converted to SQL in the same way as the values of the column or value that created this parameter.
   *
   * @param value The parameter value
   * @return Parameter assignment to be used in `bind` method
   */
  infix fun TO(value: W): ParameterAssignment {
    requireHostValue(value)
    return ParameterAssignment(
      parameter = identity,
      argument = identity.encode(value)
    )
  }
}

/**
 * Value of a parameter.
 *
 * Create one with [SqlParameter.TO].
 */
class ParameterAssignment internal constructor(
  internal val parameter: ParameterIdentity<*>,
  internal val argument: NativeValue
)
