package com.siimkinks.sqlitemagic

internal class SelectionTable<T> private constructor(
  private val compiledSql: String,
  alias: String?,
  private val args: Array<String?>?,
  private val dependencies: QueryDependencies,
  private val rowMapper: Query.Mapper<T>
) : Table<T>(
  name = "",
  alias = alias,
  nrOfColumns = 0,
  mapper = { _, _, _ -> rowMapper }
) {
  internal fun linkWithOuterSelect(outerSelectBuilder: SelectBuilder<*>) {
    args?.toList()?.let(outerSelectBuilder.args::addAll)
  }

  override fun addDependencies(destination: QueryDependencies.Builder) {
    destination.merge(dependencies)
  }

  override fun appendToSqlFromClause(sb: StringBuilder) {
    sb.append('(')
      .append(compiledSql)
      .append(')')
    if (hasAlias) {
      sb.append(" AS ")
        .append(alias)
    }
  }

  override fun `as`(alias: String): Table<T> = SelectionTable(
    compiledSql = compiledSql,
    alias = alias,
    args = args,
    dependencies = dependencies,
    rowMapper = rowMapper
  )

  companion object {
    @Suppress("UNCHECKED_CAST")
    internal fun <T> from(
      selectTableBuilder: SelectBuilder<*>,
      alias: String?
    ): SelectionTable<T> {
      val compiledSelect = selectTableBuilder.build<T>()
      val details = (compiledSelect as? CompiledSelectDetails)
        ?: error("Unknown compiled select")
      val mapper = (compiledSelect as? Query.DatabaseQuery<List<T>, T>)?.mapper
      return SelectionTable(
        compiledSql = details.sql,
        alias = alias,
        args = details.args,
        dependencies = details.queryDependencies,
        rowMapper = checkNotNull(mapper)
      )
    }
  }
}
