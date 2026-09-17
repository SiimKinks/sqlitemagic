package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement

/** Internal utility functions. */
object SqlUtil {
  @JvmStatic
  fun quoteSqlStringLiteral(value: CharSequence) =
    buildString(value.length + 2) {
      append('\'')
      for (character in value) {
        if (character == '\'') {
          append('\'')
        }
        append(character)
      }
      append('\'')
    }

  @JvmStatic
  fun query(
    db: SupportSQLiteDatabase,
    sql: String,
    args: Array<out String>?
  ): Cursor = when (args) {
    null -> db.query(sql)
    else -> db.query(sql, args)
  }

  @JvmStatic
  fun createView(
    db: SupportSQLiteDatabase,
    query: CompiledSelect<*, *>,
    viewName: String
  ) {
    val queryImpl = query as CompiledSelectImpl
    val sql = "CREATE VIEW IF NOT EXISTS $viewName AS ${queryImpl.sql}"
    when (val args = queryImpl.args) {
      null -> db.execSQL(sql)
      else -> db.execSQL(sql, args)
    }
  }

  @JvmStatic
  @CheckResult
  fun opByColumnSql(
    sql: String,
    tableName: String,
    byColumns: List<*>?
  ): String {
    byColumns ?: return sql
    val column = firstColumnForTable(
      tableName = tableName,
      columns = byColumns
    ) ?: return sql
    val whereClauseEndIndex = sql.indexOf("WHERE") + 6
    return buildString(whereClauseEndIndex + 20) {
      append(sql, 0, whereClauseEndIndex)
      append(column.name)
      append("=?")
    }
  }

  @JvmStatic
  fun firstColumnForTable(
    tableName: String,
    columns: List<*>
  ): Column<*, *, *, *, *>? = columns
    .filterIsInstance<Column<*, *, *, *, *>>()
    .firstOrNull { it.table.name == tableName }

  @JvmStatic
  fun bindAllArgsAsStrings(
    statement: SupportSQLiteStatement,
    args: Array<out String>?
  ) = args?.forEachIndexed { index, arg ->
    statement.bindString(index + 1, arg)
  }
}
