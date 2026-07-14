package com.siimkinks.sqlitemagic.dbconfig

data class DatabaseMetadata(
  val dbName: String?,
  val dbVersion: Int?,
)

data class SubmoduleDatabaseMetadata(
  val moduleName: String,
  val managerQualifiedName: String
)