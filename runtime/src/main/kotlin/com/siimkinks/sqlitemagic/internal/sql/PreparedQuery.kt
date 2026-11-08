package com.siimkinks.sqlitemagic.internal.sql

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.ParameterAssignment
import com.siimkinks.sqlitemagic.QueryDependencies
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.ScatterSet

private const val NOT_A_PARAMETER = -1

internal sealed interface QueryBinding

internal class ParameterIdentity<W>(
  val name: String,
  val encode: (W) -> NativeValue
) : QueryBinding

internal class PreparedQuery(
  val sql: String,
  val bindings: List<QueryBinding>,
  parameters: ScatterSet<ParameterIdentity<*>>?,
  val dependencies: QueryDependencies,
  val connection: DbConnectionImpl?,
  private val readWidth: Int
) {
  val requiresWriter = dependencies.readsTemporaryObjects || !dependencies.declaresSources

  private val parameterSlots = parameters?.let {
    ParameterSlots(
      bindings = bindings,
      parameterCount = it.size
    )
  }

  @Suppress("UNCHECKED_CAST")
  fun bind(assignments: List<ParameterAssignment>) = when (parameterSlots) {
    null -> {
      require(assignments.isEmpty()) { "Unexpected parameter '${assignments[0].parameter.name}'" }
      bindings as List<NativeValue>
    }
    else -> parameterSlots.bind(assignments)
  }

  inline fun <R> withStatement(
    dbConnection: DbConnectionImpl,
    values: List<NativeValue>,
    rows: Boolean = false,
    block: (SQLiteStatement) -> R
  ): R = dbConnection.read(
    requiresWriter = requiresWriter,
    rows = rows
  ) { pooled ->
    pooled.withStatement(sql) { statement ->
      bindNativeValues(
        statement = statement,
        values = values
      )
      checkReadWidth(statement)
      block(statement)
    }
  }

  fun checkReadWidth(statement: SQLiteStatement) {
    if (readWidth != 0) {
      val columnCount = statement.getColumnCount()
      check(columnCount == readWidth) { "raw query returned $columnCount columns; readAs reads $readWidth" }
    }
  }
}

private class ParameterSlots(
  private val bindings: List<QueryBinding>,
  parameterCount: Int
) {
  private val parameters = arrayOfNulls<ParameterIdentity<*>>(parameterCount)
  private val ordinals = MutableObjectIntMap<ParameterIdentity<*>>(initialCapacity = parameterCount)
  private val slotOrdinals = IntArray(bindings.size) { slot ->
    when (val binding = bindings[slot]) {
      is NativeValue -> NOT_A_PARAMETER
      is ParameterIdentity<*> -> ordinalOf(binding)
    }
  }

  private fun ordinalOf(parameter: ParameterIdentity<*>): Int {
    val ordinal = ordinals.getOrDefault(
      key = parameter,
      defaultValue = ordinals.size
    )
    if (ordinal == ordinals.size) {
      ordinals[parameter] = ordinal
      parameters[ordinal] = parameter
    }
    return ordinal
  }

  @Suppress("UNCHECKED_CAST")
  fun bind(assignments: List<ParameterAssignment>): List<NativeValue> {
    val values = arrayOfNulls<NativeValue>(parameters.size)
    for (index in assignments.indices) {
      val assignment = assignments[index]
      val parameter = assignment.parameter
      val ordinal = ordinals.getOrDefault(
        key = parameter,
        defaultValue = NOT_A_PARAMETER
      )
      require(ordinal != NOT_A_PARAMETER) { "Unexpected parameter '${parameter.name}'" }
      require(values[ordinal] == null) { "Duplicate parameter '${parameter.name}'" }
      values[ordinal] = assignment.argument
    }
    require(assignments.size == parameters.size) {
      "Missing parameter '${parameters[values.indexOf(null)]?.name}'"
    }
    val arguments = arrayOfNulls<NativeValue>(bindings.size)
    for (slot in arguments.indices) {
      arguments[slot] = when (val ordinal = slotOrdinals[slot]) {
        NOT_A_PARAMETER -> bindings[slot] as NativeValue
        else -> values[ordinal]
      }
    }
    return arguments.asList() as List<NativeValue>
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
