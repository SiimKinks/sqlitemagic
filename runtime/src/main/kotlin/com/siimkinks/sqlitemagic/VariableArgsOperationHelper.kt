package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.EntityOperation.INSERT
import com.siimkinks.sqlitemagic.EntityOperation.UPDATE
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal class VariableArgsOperationHelper(conflictAlgorithm: Int) {
  private val conflictSql = ConflictAlgorithm.CONFLICT_VALUES[conflictAlgorithm]
  private var sqlBuilder: StringBuilder? = null
  private var lastCompiledOperation: EntityOperation? = null

  fun compileInsertStatement(
    tableName: String,
    maxColumns: Int,
    values: MutableScatterMap<String, Any>,
    manager: EntityDbManager
  ): SupportSQLiteStatement {
    val builder = prepareSqlBuilder(
      operation = INSERT,
      maxColumns = maxColumns
    )
    val valuesSize = values.size
    builder
      .append(tableName)
      .append('(')
    values.forEachKey { column ->
      builder
        .append(column)
        .append(',')
    }
    if (valuesSize > 0) {
      builder.setLength(builder.length - 1)
    }
    builder.append(") VALUES (")
    for (index in 0 until valuesSize) {
      builder.append("?,")
    }
    if (valuesSize > 0) {
      builder.setLength(builder.length - 1)
    }
    builder.append(')')
    return compileStatement(
      sql = builder.toString(),
      values = values,
      manager = manager
    )
  }

  fun compileUpdateStatement(
    tableName: String,
    maxColumns: Int,
    values: MutableScatterMap<String, Any>,
    resolutionColumn: String,
    resolutionValue: Any,
    manager: EntityDbManager
  ): SupportSQLiteStatement {
    val builder = prepareSqlBuilder(
      operation = UPDATE,
      maxColumns = maxColumns
    )
    builder
      .append(tableName)
      .append(" SET ")
    values.forEachKey { column ->
      builder
        .append(column)
        .append("=?,")
    }
    if (values.isNotEmpty()) {
      builder.setLength(builder.length - 1)
    }
    builder
      .append(" WHERE ")
      .append(resolutionColumn)
      .append("=?")
    return compileStatement(
      sql = builder.toString(),
      values = values,
      manager = manager
    ).also { statement ->
      statement.bindValue(
        position = values.size + 1,
        value = resolutionValue
      )
    }
  }

  private fun prepareSqlBuilder(
    operation: EntityOperation,
    maxColumns: Int
  ): StringBuilder {
    val builder = sqlBuilder
      ?: StringBuilder(7 + conflictSql.length + maxColumns * 22)
        .also { sqlBuilder = it }
    when {
      operation == lastCompiledOperation -> {
        val prefixLength = when (operation) {
          INSERT -> 12 // "INSERT INTO " without the conflict clause
          UPDATE -> 7 // "UPDATE " without the conflict clause
        }
        builder.setLength(prefixLength + conflictSql.length)
      }
      else -> {
        builder.setLength(0)
        builder
          .append(operation.name)
          .append(conflictSql)
          .append(
            when (operation) {
              INSERT -> " INTO "
              UPDATE -> " "
            }
          )
      }
    }
    lastCompiledOperation = operation
    return builder
  }

  private fun compileStatement(
    sql: String,
    values: MutableScatterMap<String, Any>,
    manager: EntityDbManager
  ) = manager
    .compileStatement(sql)
    .also { statement ->
      var position = 1
      values.forEachValue { value ->
        statement.bindValue(
          position = position++,
          value = value
        )
      }
    }

  @Suppress("UNCHECKED_CAST")
  private fun SupportSQLiteStatement.bindValue(
    position: Int,
    value: Any
  ) = when (value) {
    is String -> bindString(position, value)
    is Float -> bindDouble(position, value.toDouble())
    is Double -> bindDouble(position, value)
    is Number -> bindLong(position, value.toLong())
    is ByteArray -> bindBlob(position, value)
    is Array<*> if value.isArrayOf<Byte>() -> bindBlob(position, (value as Array<Byte>).toByteArray())
    else -> bindString(position, value.toString())
  }
}
