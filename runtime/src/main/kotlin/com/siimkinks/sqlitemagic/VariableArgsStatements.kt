package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal fun OperationContext.compileInsertStatement(tableName: String) = compileBoundStatement(
  buildString {
    append("INSERT")
    append(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
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
  }
)

internal fun OperationContext.compileUpdateStatement(
  tableName: String,
  resolutionColumn: String,
  resolutionValue: Any
) = compileBoundStatement(
  buildString {
    append("UPDATE")
    append(ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm])
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
)
  .bindSafely {
    bindStorageValue(
      position = bindValues.size + 1,
      value = resolutionValue
    )
  }

internal fun StringBuilder.appendPlaceholders(count: Int) = repeat(count) { index ->
  if (index > 0) {
    append(',')
  }
  append('?')
}

internal fun SupportSQLiteStatement.bindStorageValue(position: Int, value: Any) {
  when (value) {
    is String -> bindString(position, value)
    is Float -> bindDouble(position, value.toDouble())
    is Double -> bindDouble(position, value)
    is Number -> bindLong(position, value.toLong())
    is ByteArray -> bindBlob(position, value)
  }
}

private fun StringBuilder.appendColumns(values: MutableScatterMap<String, Any>, suffix: String) {
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

private fun OperationContext.compileBoundStatement(sql: String) = entityDbManager()
  .compileStatement(sql)
  .bindSafely {
    var position = 1
    bindValues.forEachValue { value ->
      bindStorageValue(
        position = position++,
        value = value
      )
    }
  }

private inline fun SupportSQLiteStatement.bindSafely(
  bind: SupportSQLiteStatement.() -> Unit
) = apply {
  try {
    bind()
  } catch (failure: Throwable) {
    try {
      close()
    } catch (closeFailure: Throwable) {
      failure.addSuppressed(closeFailure)
    }
    throw failure
  }
}
