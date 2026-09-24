package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

/** Internal utility functions. */
object SqlUtil {
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

  fun query(
    db: SupportSQLiteDatabase,
    sql: String,
    args: Array<out String?>?
  ): Cursor = when (args) {
    null -> db.query(sql)
    else -> db.query(sql, args)
  }

  fun createView(
    db: SupportSQLiteDatabase,
    query: CompiledSelect<*, *>,
    viewName: String
  ) {
    val details = (query as? CompiledSelectDetails)
      ?: throw IllegalArgumentException(
        "Cannot create view '$viewName': defining query uses unsupported implementation ${query.javaClass.name}"
      )
    require(details.args.isNullOrEmpty()) {
      "Cannot create view '$viewName': defining query has bound arguments"
    }
    db.execSQL("CREATE VIEW IF NOT EXISTS $viewName AS ${details.sql}")
  }

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

  fun firstColumnForTable(
    tableName: String,
    columns: List<*>
  ): Column<*, *, *, *, *>? = columns
    .filterIsInstance<Column<*, *, *, *, *>>()
    .firstOrNull { it.table.name == tableName }

  fun bindAllArgsAsStrings(
    statement: SupportSQLiteStatement,
    args: Array<out String?>?
  ) = args?.forEachIndexed { index, arg ->
    when (arg) {
      null -> statement.bindNull(index + 1)
      else -> statement.bindString(index + 1, arg)
    }
  }
}
