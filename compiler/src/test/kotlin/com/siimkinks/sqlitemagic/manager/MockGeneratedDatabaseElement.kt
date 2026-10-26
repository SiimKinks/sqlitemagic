package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.dbconfig.DatabaseMetadata
import com.squareup.kotlinpoet.ClassName

internal fun mockGeneratedDatabaseElement() = GeneratedDatabaseElement(
  className = ClassName(PACKAGE_ROOT, "SqliteMagicDatabase"),
  submoduleName = null,
  databaseMetadata = DatabaseMetadata(
    dbName = null,
    dbVersion = null
  ),
  isDebug = false,
  tables = emptyList(),
  views = emptyList(),
  indices = emptyList(),
  submodules = emptyList()
)
