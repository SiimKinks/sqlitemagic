package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SubmodulePersistentValueTable.Companion.SUBMODULE_PERSISTENT_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.model.ManagerIntegrationModelCase
import com.siimkinks.sqlitemagic.runtime.model.ManagerTableModule
import com.siimkinks.sqlitemagic.runtime.model.ManagerTableStorage

internal object ManagerIntegrationModelCatalog {
  val cases: List<ManagerIntegrationModelCase<*>> = listOf(
    ManagerIntegrationModelCase(
      name = "SimpleMutableEntity",
      tableName = "simple_mutable_entity",
      table = SIMPLE_MUTABLE_ENTITY,
      module = ManagerTableModule.MAIN,
      storage = ManagerTableStorage.PERSISTENT,
      newValue = {
        SimpleMutableEntity(
          value = "main-persistent-value",
          boxedBoolean = true,
          primitiveBoolean = false
        )
      },
      insert = SimpleMutableEntity::insert
    ),
    ManagerIntegrationModelCase(
      name = "MainSessionValue",
      tableName = "main_session_value",
      table = MAIN_SESSION_VALUE,
      module = ManagerTableModule.MAIN,
      storage = ManagerTableStorage.TEMPORARY,
      newValue = {
        MainSessionValue(
          id = "main-session-id",
          value = "main-session-value"
        )
      },
      insert = MainSessionValue::insert
    ),
    ManagerIntegrationModelCase(
      name = "SubmodulePersistentValue",
      tableName = "submodule_persistent_value",
      table = SUBMODULE_PERSISTENT_VALUE,
      module = ManagerTableModule.SUBMODULE,
      storage = ManagerTableStorage.PERSISTENT,
      newValue = {
        SubmodulePersistentValue(
          id = "submodule-persistent-id",
          value = "submodule-persistent-value"
        )
      },
      insert = SubmodulePersistentValue::insert
    ),
    ManagerIntegrationModelCase(
      name = "SubmoduleSessionValue",
      tableName = "submodule_session_value",
      table = SUBMODULE_SESSION_VALUE,
      module = ManagerTableModule.SUBMODULE,
      storage = ManagerTableStorage.TEMPORARY,
      newValue = {
        SubmoduleSessionValue(
          id = "submodule-session-id",
          value = "submodule-session-value"
        )
      },
      insert = SubmoduleSessionValue::insert
    )
  )

  val submoduleCases = cases.filter(ManagerIntegrationModelCase<*>::isSubmodule)
  val temporaryCases = cases.filter(ManagerIntegrationModelCase<*>::isTemporary)
  val temporaryTableNames = temporaryCases.map(ManagerIntegrationModelCase<*>::tableName)
}
