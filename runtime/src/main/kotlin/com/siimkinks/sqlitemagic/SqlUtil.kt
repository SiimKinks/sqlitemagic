package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY

/** Internal utility functions. */
object SqlUtil {
  fun viewDefinition(
    query: CompiledSelect<*, *>,
    viewName: String
  ) = when (query) {
    is CompiledSelectImpl<*, *> -> ViewDefinition(
      sql = query.sql,
      args = query.args,
      queryDependencies = query.queryDependencies,
      columns = query.columns,
      tableGraphNodeNames = query.tableGraphNodeNames,
      queryDeep = query.queryDeep
    )
    is CompiledSelect1Impl<*, *> -> ViewDefinition(
      sql = query.sql,
      args = query.args,
      queryDependencies = query.queryDependencies,
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
    viewName: String,
    temporary: Boolean = false
  ) = createView(
    db = db,
    definition = viewDefinition(
      query = query,
      viewName = viewName
    ),
    viewName = viewName,
    temporary = temporary
  )

  fun createView(
    db: SupportSQLiteDatabase,
    definition: ViewDefinition,
    viewName: String,
    temporary: Boolean = false
  ) = createViewSql(
    db = db,
    definition = definition,
    viewName = viewName,
    temporary = temporary
  )

  private fun createViewSql(
    db: SupportSQLiteDatabase,
    definition: ViewDefinition,
    viewName: String,
    temporary: Boolean
  ) {
    require(definition.args.isNullOrEmpty()) {
      "Cannot create view '$viewName': defining query has bound arguments"
    }
    if (!temporary) {
      val dependencies = definition.queryDependencies
      require(dependencies.directSourcesComplete) {
        "Cannot create persistent view '$viewName': defining query has incomplete direct sources"
      }
      val temporaryTable = dependencies.directSources.firstOrNull { source ->
        source.kind == TABLE && source.schema == TEMPORARY
      }
      require(temporaryTable == null) {
        "Cannot create persistent view '$viewName': defining query references temporary table '${temporaryTable?.name}'"
      }
    }
    val quotedViewName = viewName.replace(
      oldValue = "\"",
      newValue = "\"\""
    )
    val create = when {
      temporary -> "CREATE TEMPORARY VIEW"
      else -> "CREATE VIEW"
    }
    db.execSQL("$create IF NOT EXISTS \"$quotedViewName\" AS ${definition.sql}")
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
