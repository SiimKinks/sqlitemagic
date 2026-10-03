package com.siimkinks.sqlitemagic

import android.database.SQLException
import com.siimkinks.sqlitemagic.Utils.ValueParser
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
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
  allFromTable: Boolean = false,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?,
  nameInQuery: String,
  private val fragment: QueryFragment
) : NumericColumn<T, R, ET, P, N>(
  table = table,
  name = name,
  allFromTable = allFromTable,
  valueParser = valueParser,
  nullable = nullable,
  alias = alias,
  nameInQuery = nameInQuery
) {
  companion object {
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
        valueParser = column.valueParser,
        nullable = column.nullable,
        alias = alias,
        nameInQuery = alias,
        fragment = selectBuilder.freezeFragment()
      )
    }
  }

  override fun appendSql(sb: StringBuilder) {
    sb.append('(')
    fragment.appendSql(sb)
    sb.append(')')
  }

  override fun appendSql(
    sb: StringBuilder,
    systemRenamedTables: MutableScatterMap<String, LinkedList<String>>
  ) = appendSql(sb)

  override fun addSelectedTables(result: MutableScatterSet<String>) {
    // selection column does not add any tables to outer selection as it is returning
    // an autonomous column
  }

  override fun addArgs(args: ArrayList<String?>) = fragment.addArgs(args)

  override fun addDependencies(dependencies: QueryDependencies.Builder) = fragment.addDependencies(dependencies)

  override fun compile(
    columnPositions: MutableObjectIntMap<String>,
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
    return columnOffset + 1
  }

  override fun compile(
    columnPositions: MutableObjectIntMap<String>,
    compiledCols: StringBuilder,
    systemRenamedTables: MutableScatterMap<String, LinkedList<String>>,
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
    fragment = fragment
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
      fragment = fragment
    )
}
