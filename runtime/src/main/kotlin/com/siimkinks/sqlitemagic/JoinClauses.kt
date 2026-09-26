package com.siimkinks.sqlitemagic

/** Join clauses in SQL order, with a single mutation path that links each new clause. */
internal class JoinClauses(
  private val selectBuilder: SelectBuilder<*>
) : AbstractList<JoinClause>() {
  private val clauses = ArrayList<JoinClause>()

  override val size get() = clauses.size

  override fun get(index: Int) = clauses[index]

  internal fun append(join: JoinClause) {
    selectBuilder.ensureMutable()
    clauses.add(join)
    (join.table as? SelectionTable<*>)?.linkWithOuterSelect(selectBuilder)
    join.addArgs(selectBuilder.args)
    join.addDependencies(selectBuilder.dependencies)
  }

  internal operator fun plusAssign(join: JoinClause) = append(join)
}
