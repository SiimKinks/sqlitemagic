package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

/** Internal utility functions. */
object SqlUtil {
  fun viewDefinition(
    query: CompiledSelect<*, *>,
    viewName: String
  ) = when (query) {
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
    else -> error("Cannot create view '$viewName': defining query uses unsupported implementation ${query.javaClass.name}")
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
  ) = createView(
    db = db,
    definition = viewDefinition(
      query = query,
      viewName = viewName
    ),
    viewName = viewName
  )

  fun createView(
    db: SupportSQLiteDatabase,
    definition: ViewDefinition,
    viewName: String
  ) = createViewSql(
    db = db,
    sql = definition.sql,
    args = definition.args,
    viewName = viewName
  )

  private fun createViewSql(
    db: SupportSQLiteDatabase,
    sql: String,
    args: Array<String?>?,
    viewName: String
  ) {
    require(args.isNullOrEmpty()) {
      "Cannot create view '$viewName': defining query has bound arguments"
    }
    val quotedViewName = viewName.replace(
      oldValue = "\"",
      newValue = "\"\""
    )
    db.execSQL("CREATE VIEW IF NOT EXISTS \"$quotedViewName\" AS $sql")
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
