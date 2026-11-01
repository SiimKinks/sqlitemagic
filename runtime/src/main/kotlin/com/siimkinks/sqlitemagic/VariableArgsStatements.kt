package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.ConflictAlgorithm.Companion.CONFLICT_VALUES
import com.siimkinks.sqlitemagic.internal.MutationResults
import com.siimkinks.sqlitemagic.internal.ScatterMap

internal inline fun <T> OperationContext.withBoundInsertStatement(
  tableName: String,
  block: (SQLiteStatement, MutationResults) -> T
) = withBoundStatement(
  sql = buildString {
    append("INSERT")
    append(CONFLICT_VALUES[statementConflictAlgorithm])
    append(" INTO ")
    append(tableName)
    when {
      bindValues.isEmpty() -> append(" DEFAULT VALUES")
      else -> {
        append('(')
        appendColumns(
          values = bindValues,
          suffix = ""
        )
        append(") VALUES (")
        appendPlaceholders(bindValues.size)
        append(')')
      }
    }
  },
  block = block
)

internal inline fun <T> OperationContext.withBoundUpdateStatement(
  tableName: String,
  resolutionColumn: String,
  resolutionValue: Any,
  block: (SQLiteStatement, MutationResults) -> T
) = withBoundStatement(
  sql = buildString {
    append("UPDATE")
    append(CONFLICT_VALUES[statementConflictAlgorithm])
    append(' ')
    append(tableName)
    append(" SET ")
    when {
      bindValues.isEmpty() -> {
        append(resolutionColumn)
        append('=')
        append(resolutionColumn)
      }
      else -> appendColumns(
        values = bindValues,
        suffix = "=?"
      )
    }
    append(" WHERE ")
    append(resolutionColumn)
    append("=?")
  }
) { statement, results ->
  statement.bindStorageValue(
    position = bindValues.size + 1,
    value = resolutionValue
  )
  block(statement, results)
}

internal fun StringBuilder.appendPlaceholders(count: Int) = repeat(count) { index ->
  if (index > 0) {
    append(',')
  }
  append('?')
}

internal fun SQLiteStatement.bindStorageValue(position: Int, value: Any) {
  when (value) {
    is String -> bindText(position, value)
    is Float -> bindDouble(position, value.toDouble())
    is Double -> bindDouble(position, value)
    is Number -> bindLong(position, value.toLong())
    is ByteArray -> bindBlob(position, value)
    is Boolean -> bindBoolean(position, value)
  }
}

internal fun StringBuilder.appendColumns(values: ScatterMap<String, Any>, suffix: String) {
  var first = true
  values.forEachKey { column ->
    if (!first) {
      append(',')
    }
    first = false
    append(column)
    append(suffix)
  }
}

internal inline fun <T> OperationContext.withBoundStatement(
  sql: String,
  block: (SQLiteStatement, MutationResults) -> T
) = withStatement(sql = { sql }) { statement, results ->
  var position = 1
  bindValues.forEachValue { value ->
    statement.bindStorageValue(
      position = position++,
      value = value
    )
  }
  block(statement, results)
}
