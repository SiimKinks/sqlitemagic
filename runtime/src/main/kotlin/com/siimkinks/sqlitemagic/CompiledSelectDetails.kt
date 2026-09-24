package com.siimkinks.sqlitemagic

internal interface CompiledSelectDetails {
  val sql: String
  val args: Array<String?>?
  val observedTables: Array<String>
}
