package com.siimkinks.sqlitemagic

internal interface CompiledSelectDetails {
  val sql: String
  val args: Array<String?>?
  val queryDependencies: QueryDependencies
  val observedTables: Array<String>
}
