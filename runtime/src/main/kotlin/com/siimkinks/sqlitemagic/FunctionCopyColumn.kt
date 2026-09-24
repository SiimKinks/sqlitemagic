package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import java.util.LinkedList

/**
 * A column used in queries and conditions.
 *
 * @param T Exact type
 * @param R Return type (when this column is queried)
 * @param ET Equivalent type
 * @param P Parent table type
 * @param N Column nullability
 */
internal class FunctionCopyColumn<T, R, ET, P, N>(
  table: Table<P>,
  private val wrappedColumn: Column<T, R, ET, P, N>,
  private val prefix: String,
  private val suffix: Char,
  nullable: Boolean,
  alias: String?
) : NumericColumn<T, R, ET, P, N>(
  table = table,
  name = prefix + wrappedColumn.nameInQuery + suffix,
  allFromTable = wrappedColumn.allFromTable,
  valueParser = wrappedColumn.valueParser,
  nullable = nullable,
  alias = alias ?: wrappedColumn.alias
) {
  private var isCompiledToSelection = false

  override fun toSqlArg(value: T & Any) = wrappedColumn.toSqlArg(value)

  override fun <V> getFromCursor(cursor: Cursor): V? = wrappedColumn.getFromCursor(cursor)

  override fun <V> getFromStatement(statement: SupportSQLiteStatement): V? =
    wrappedColumn.getFromStatement(statement)

  override fun appendSql(sb: StringBuilder) {
    if (isCompiledToSelection && hasAlias()) {
      sb.append(getAppendableAlias())
      return
    }
    sb.appendFunction {
      wrappedColumn.appendSql(sb)
    }
  }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    if (isCompiledToSelection && hasAlias()) {
      sb.append(getAppendableAlias())
      return
    }
    sb.appendFunction {
      wrappedColumn.appendSql(sb, systemRenamedTables)
    }
  }

  private inline fun StringBuilder.appendFunction(
    appendWrapped: () -> Unit
  ) {
    append(prefix)
    appendWrapped()
    append(suffix)
  }

  override fun addSelectedTables(result: StringArraySet) = wrappedColumn.addSelectedTables(result)

  override fun addArgs(args: ArrayList<String?>) = wrappedColumn.addArgs(args)

  override fun addObservedTables(tables: ArrayList<String>) = wrappedColumn.addObservedTables(tables)

  override fun compile(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    columnOffset: Int
  ): Int {
    appendSql(compiledCols)
    appendAliasDeclarationIfNeeded(compiledCols)
    putColumnPosition(
      columnPositions = columnPositions,
      columnId = name,
      pos = columnOffset,
      column = this
    )
    isCompiledToSelection = true
    return columnOffset + 1
  }

  override fun compile(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>,
    columnOffset: Int
  ): Int {
    appendSql(compiledCols, systemRenamedTables)
    appendAliasDeclarationIfNeeded(compiledCols)
    putColumnPosition(
      columnPositions = columnPositions,
      columnId = name,
      pos = columnOffset,
      column = this
    )
    isCompiledToSelection = true
    return columnOffset + 1
  }

  override fun `as`(alias: String) = FunctionCopyColumn(
    table = table,
    wrappedColumn = wrappedColumn,
    prefix = prefix,
    suffix = suffix,
    nullable = nullable,
    alias = alias
  )

  override fun <NewTableType> inTable(table: Table<NewTableType>) = FunctionCopyColumn(
    table = table,
    wrappedColumn = wrappedColumn.inTable(table),
    prefix = prefix,
    suffix = suffix,
    nullable = nullable,
    alias = alias
  )
}
