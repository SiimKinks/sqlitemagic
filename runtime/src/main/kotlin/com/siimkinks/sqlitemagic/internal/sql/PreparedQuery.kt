package com.siimkinks.sqlitemagic.internal.sql

import android.database.Cursor
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.ParameterAssignment
import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterSet

internal sealed interface QueryBinding

internal class ParameterIdentity<W>(
  val name: String,
  val encode: (W) -> NativeValue
) : QueryBinding

internal class PreparedQuery(
  val sql: String,
  val bindings: List<QueryBinding>,
  val parameters: ScatterSet<ParameterIdentity<*>>,
  val dependencies: QueryDependencies,
  val connection: DbConnectionImpl?,
  private val readWidth: Int
) {
  @Suppress("UNCHECKED_CAST")
  private val immediateArguments = when {
    parameters.isEmpty() -> bindings as List<NativeValue>
    else -> null
  }

  fun arguments(assignments: List<ParameterAssignment>) = when {
    assignments.isEmpty() && immediateArguments != null -> immediateArguments
    else -> bind(assignments)
  }

  fun bind(assignments: List<ParameterAssignment>): List<NativeValue> {
    val values = MutableScatterMap<ParameterIdentity<*>, NativeValue>(initialCapacity = assignments.size)
    assignments.forEach { assignment ->
      val parameter = assignment.parameter
      require(parameter in parameters) { "Unexpected parameter '${parameter.name}'" }
      require(parameter !in values) { "Duplicate parameter '${parameter.name}'" }
      values[parameter] = assignment.argument
    }
    parameters.forEach { parameter ->
      require(parameter in values) { "Missing parameter '${parameter.name}'" }
    }
    return bindings.map { binding ->
      when (binding) {
        is NativeValue -> binding
        is ParameterIdentity<*> -> values.getExisting(binding)
      }
    }
  }

  fun checkReadWidth(cursor: Cursor) {
    if (readWidth != 0 && cursor.columnCount != readWidth) {
      val columnCount = cursor.columnCount
      cursor.close()
      error("raw query returned $columnCount columns; readAs reads $readWidth")
    }
  }
}

internal fun prepareQuery(
  renderable: SqlRenderable,
  connection: DbConnectionImpl?,
  context: SqlRenderingContext = SqlRenderingContext(),
  readWidth: Int = 0
): PreparedQuery {
  renderable.render(context)
  return PreparedQuery(
    sql = context.sql.toString(),
    bindings = context.bindings,
    parameters = context.parameters,
    dependencies = context.dependencies.build(),
    connection = connection,
    readWidth = readWidth
  )
}
