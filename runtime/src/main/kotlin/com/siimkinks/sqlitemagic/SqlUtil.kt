package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
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
      columns = MutableObjectIntMap<String>().apply {
        val selectedColumn = query.selectedColumn
        put(selectedColumn.nameInQuery, 0)
        query.selectedColumn.alias?.let { alias ->
          put(alias, 0)
        }
      },
      tableGraphNodeNames = MutableScatterMap(initialCapacity = 0),
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

  fun createViews(
    db: SupportSQLiteDatabase,
    views: Collection<GeneratedView>,
    temporary: Boolean
  ) = GeneratedViewResolver(views)
    .resolve(temporary = temporary)
    .forEach { node ->
      createViewSql(
        db = db,
        definition = node.definition,
        viewName = node.view.viewName,
        temporary = node.view.temporary
      )
    }

  internal fun validateViewDefinition(
    definition: ViewDefinition,
    viewName: String,
    temporary: Boolean,
    requireCompleteSources: Boolean
  ) {
    require(definition.args.isNullOrEmpty()) {
      "Cannot create view '$viewName': defining query has bound arguments"
    }
    if (requireCompleteSources) {
      require(definition.queryDependencies.directSourcesComplete) {
        val subject = when {
          temporary -> "view"
          else -> "persistent view"
        }
        "Cannot create $subject '$viewName': defining query has incomplete direct sources"
      }
    }
    if (!temporary) {
      val temporarySource = definition.queryDependencies.directSources.firstOrNull { it.schema == TEMPORARY }
      require(temporarySource == null) {
        "Cannot create persistent view '$viewName': defining query references temporary " +
            "${temporarySource?.kind?.name?.lowercase()} '${temporarySource?.name}'"
      }
    }
  }

  private fun createViewSql(
    db: SupportSQLiteDatabase,
    definition: ViewDefinition,
    viewName: String,
    temporary: Boolean
  ) {
    val create = when {
      temporary -> "CREATE TEMPORARY VIEW"
      else -> "CREATE VIEW"
    }
    db.execSQL("$create IF NOT EXISTS ${quoteSqlIdentifier(viewName)} AS ${definition.sql}")
  }

  internal fun quoteSqlIdentifier(name: String) =
    """"${name.replace(oldValue = "\"", newValue = "\"\"")}""""

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
