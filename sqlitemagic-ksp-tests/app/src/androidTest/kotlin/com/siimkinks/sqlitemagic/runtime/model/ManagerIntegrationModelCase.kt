package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder

internal enum class ManagerTableModule {
  MAIN,
  SUBMODULE
}

internal enum class ManagerTableStorage {
  PERSISTENT,
  TEMPORARY
}

internal data class ManagerIntegrationModelCase<T>(
  val name: String,
  val tableName: String,
  val table: Table<T>,
  val module: ManagerTableModule,
  val storage: ManagerTableStorage,
  val newValue: () -> T,
  val insert: (T) -> EntityInsertBuilder
) {
  val isSubmodule get() = module == ManagerTableModule.SUBMODULE
  val isTemporary get() = storage == ManagerTableStorage.TEMPORARY

  override fun toString() = name
}
