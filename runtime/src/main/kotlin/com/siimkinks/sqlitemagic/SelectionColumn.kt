package com.siimkinks.sqlitemagic

import android.database.SQLException
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
internal class SelectionColumn<T, R, ET, P, N> private constructor(
  table: Table<P>,
  name: String,
  allFromTable: Boolean,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?,
  nameInQuery: String,
  private val selectBuilder: SelectBuilder<*>
) : NumericColumn<T, R, ET, P, N>(
  table,
  name,
  allFromTable,
  valueParser,
  nullable,
  alias,
  nameInQuery
) {
  private var parentObservedTables: ArrayList<String>? = null
  private var isCompiledToSelection = false

  companion object {
    @JvmStatic
    fun <T, N> from(
      selectBuilder: SelectBuilder<*>,
      alias: String
    ): SelectionColumn<T, T, T, *, N> {
      val columnNode = selectBuilder.columnNode
      if (selectBuilder.columnsNode != null || columnNode == null) {
        throw SQLException("Only a single result allowed for a SELECT that is part of a column")
      }
      val table = checkNotNull(selectBuilder.from).table
      val column = columnNode.column
      return SelectionColumn(
        table = table,
        name = alias,
        allFromTable = false,
        valueParser = column.valueParser,
        nullable = column.nullable,
        alias = alias,
        nameInQuery = alias,
        selectBuilder = selectBuilder
      )
    }
  }

  override fun appendSql(sb: StringBuilder) {
    if (isCompiledToSelection) {
      sb.append(getAppendableAlias())
      return
    }
    sb.append('(')
    selectBuilder.appendCompiledQuery(sb, parentObservedTables)
    sb.append(')')
  }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ) = appendSql(sb)

  override fun addSelectedTables(result: StringArraySet) {
    // selection column does not add any tables to outer selection as it is returning
    // an autonomous column
  }

  override fun addArgs(args: ArrayList<String>) {
    args.addAll(selectBuilder.args)
  }

  override fun addObservedTables(tables: ArrayList<String>) {
    // defer observed tables adding until SQL building, where we might add missing joins,
    // so there will be more tables to add/observe.
    parentObservedTables = tables
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

  override fun `as`(alias: String): NumericColumn<T, R, ET, P, N> = SelectionColumn(
    table = table,
    name = name,
    allFromTable = allFromTable,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias,
    nameInQuery = nameInQuery,
    selectBuilder = selectBuilder
  )

  override fun <NewTableType> inTable(table: Table<NewTableType>): NumericColumn<T, R, ET, NewTableType, N> =
    SelectionColumn(
      table = table,
      name = name,
      allFromTable = allFromTable,
      valueParser = valueParser,
      nullable = nullable,
      alias = alias,
      nameInQuery = nameInQuery,
      selectBuilder = selectBuilder
    )
}
