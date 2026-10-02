package com.siimkinks.sqlitemagic.dbconfig

import com.squareup.kotlinpoet.ClassName

data class DatabaseMetadata(
  val dbName: String?,
  val dbVersion: Int?,
)

data class SubmoduleDatabaseMetadata(
  val moduleName: String,
  val managerQualifiedName: String
) {
  val managerClassName = ClassName.bestGuess(managerQualifiedName)
}