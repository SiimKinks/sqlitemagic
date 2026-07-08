package com.siimkinks.sqlitemagic

data class DatabaseMetadata(
  val dbName: String?,
  val dbVersion: Int?,
)

data class SubmoduleDatabaseMetadata(
  val moduleName: String,
  val managerQualifiedName: String
)