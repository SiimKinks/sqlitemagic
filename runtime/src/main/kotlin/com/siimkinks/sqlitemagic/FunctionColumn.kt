package com.siimkinks.sqlitemagic

import androidx.annotation.Size
import com.siimkinks.sqlitemagic.Utils.ValueParser
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
internal class FunctionColumn<T, R, ET, P, N> internal constructor(
  table: Table<P>,
  @Size(min = 1) private val wrappedColumns: Array<out Column<*, *, *, *, *>>,
  private val prefix: String,
  private val separator: String?,
  private val suffix: String,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?
) : NumericColumn<T, R, ET, P, N>(
  table,
  prefix + suffix,
  false,
  valueParser,
  nullable,
  if (alias != null || wrappedColumns.size > 1) alias else wrappedColumns[0].alias
) {
  private var isCompiledToSelection = false

  internal constructor(
    table: Table<P>,
    wrappedColumn: Column<*, *, *, *, *>,
    prefix: String,
    suffix: String,
    valueParser: ValueParser<*>,
    nullable: Boolean,
    alias: String?
  ) : this(
    table = table,
    wrappedColumns = arrayOf(wrappedColumn),
    prefix = prefix,
    separator = null,
    suffix = suffix,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun appendSql(sb: StringBuilder) {
    if (isCompiledToSelection && hasAlias()) {
      sb.append(getAppendableAlias())
      return
    }
    sb.append(prefix)
    wrappedColumns.forEachIndexed { index, column ->
      if (index > 0) {
        sb.append(separator)
      }
      column.appendSql(sb)
    }
    sb.append(suffix)
  }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) {
    if (isCompiledToSelection && hasAlias()) {
      sb.append(getAppendableAlias())
      return
    }
    sb.append(prefix)
    wrappedColumns.forEachIndexed { index, column ->
      if (index > 0) {
        sb.append(separator)
      }
      column.appendSql(sb, systemRenamedTables)
    }
    sb.append(suffix)
  }

  override fun addSelectedTables(result: StringArraySet) {
    for (wrappedColumn in wrappedColumns) {
      wrappedColumn.addSelectedTables(result)
    }
  }

  override fun addArgs(args: ArrayList<String>) {
    for (wrappedColumn in wrappedColumns) {
      wrappedColumn.addArgs(args)
    }
  }

  override fun addObservedTables(tables: ArrayList<String>) {
    for (wrappedColumn in wrappedColumns) {
      wrappedColumn.addObservedTables(tables)
    }
  }

  override fun compile(
    columnPositions: SimpleArrayMap<String, Int>,
    compiledCols: StringBuilder,
    columnOffset: Int
  ): Int {
    appendSql(compiledCols)
    appendAliasDeclarationIfNeeded(compiledCols)
    putColumnPosition(
      columnPositions = columnPositions,
      columnId = null,
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
      columnId = null,
      pos = columnOffset,
      column = this
    )
    isCompiledToSelection = true
    return columnOffset + 1
  }

  override fun `as`(alias: String): FunctionColumn<T, R, ET, P, N> = FunctionColumn(
    table = table,
    wrappedColumns = wrappedColumns,
    prefix = prefix,
    separator = separator,
    suffix = suffix,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun <NewTableType> inTable(table: Table<NewTableType>) = FunctionColumn<T, R, ET, NewTableType, N>(
    table = table,
    wrappedColumns = wrappedColumns,
    prefix = prefix,
    separator = separator,
    suffix = suffix,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )
}
