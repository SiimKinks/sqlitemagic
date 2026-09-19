package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

/** Internal utility functions. */
object SqlUtil {
  @JvmStatic
  fun viewDefinition(
    query: CompiledSelect<*, *>
  ): ViewDefinition = when (query) {
    is CompiledSelectImpl<*, *> -> ViewDefinition(
      sql = query.sql,
      args = query.args,
      observedTables = query.observedTables,
      columns = query.columns,
      tableGraphNodeNames = query.tableGraphNodeNames,
      queryDeep = query.queryDeep
    )
    is CompiledSelect1Impl<*, *> -> ViewDefinition(
      sql = query.sql,
      args = query.args,
      observedTables = query.observedTables,
      columns = SimpleArrayMap<String, Int>().apply {
        val selectedColumn = query.selectedColumn
        put(selectedColumn.nameInQuery, 0)
        query.selectedColumn.alias?.let { alias ->
          put(alias, 0)
        }
      },
      tableGraphNodeNames = SimpleArrayMap(),
      queryDeep = false
    )
    else -> error("Unreachable supported query implementation")
  }

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
    val (sql, args) = when (query) {
      is CompiledSelectImpl<*, *> -> query.sql to query.args
      is CompiledSelect1Impl<*, *> -> query.sql to query.args
      else -> throw IllegalArgumentException(
        "Cannot create view '$viewName': defining query uses unsupported implementation ${query.javaClass.name}"
      )
    }
    require(args.isNullOrEmpty()) {
      "Cannot create view '$viewName': defining query has bound arguments"
    }
    db.execSQL("CREATE VIEW IF NOT EXISTS $viewName AS $sql")
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
