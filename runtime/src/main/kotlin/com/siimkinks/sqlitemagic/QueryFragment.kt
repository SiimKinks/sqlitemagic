package com.siimkinks.sqlitemagic

/** Immutable SQL and dependency snapshot for a select embedded in another query. */
internal class QueryFragment(
  val sql: String,
  args: List<String?>,
  val dependencies: QueryDependencies
) {
  val args: List<String?> = args.toList()

  fun appendSql(sb: StringBuilder) {
    sb.append(sql)
  }

  fun addArgs(destination: ArrayList<String?>) {
    destination.addAll(args)
  }

  fun addDependencies(destination: QueryDependencies.Builder) {
    destination.merge(dependencies)
  }
}
