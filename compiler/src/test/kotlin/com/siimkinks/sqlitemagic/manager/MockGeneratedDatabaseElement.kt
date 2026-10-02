package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.dbconfig.DatabaseMetadata
import com.siimkinks.sqlitemagic.dbconfig.SubmoduleDatabaseMetadata
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.squareup.kotlinpoet.ClassName

internal fun mockGeneratedDatabaseElement(
  className: ClassName = ClassName(PACKAGE_ROOT, "SqliteMagicDatabase"),
  submoduleName: String? = null,
  databaseMetadata: DatabaseMetadata = DatabaseMetadata(
    dbName = null,
    dbVersion = null
  ),
  isDebug: Boolean = false,
  tables: List<TableElement> = emptyList(),
  transformers: List<TransformerElement> = emptyList(),
  submodules: List<SubmoduleDatabaseMetadata> = emptyList()
) = GeneratedDatabaseElement(
  className = className,
  submoduleName = submoduleName,
  databaseMetadata = databaseMetadata,
  isDebug = isDebug,
  tables = tables,
  transformers = transformers,
  submodules = submodules
)
