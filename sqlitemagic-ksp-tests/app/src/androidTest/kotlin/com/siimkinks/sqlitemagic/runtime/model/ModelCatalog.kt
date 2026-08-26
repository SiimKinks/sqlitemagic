package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.model.catalog.ConstructionModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.IdentityModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.RelationshipModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarModelCatalog

object ModelCatalog {
  val allCases: List<RuntimeModelCase<*>> = listOf(
    ScalarModelCatalog.cases,
    ConstructionModelCatalog.cases,
    IdentityModelCatalog.cases,
    RelationshipModelCatalog.cases
  ).flatten()

  val insertCases: List<InsertModelCase<*>> = allCases.filterIsInstance<InsertModelCase<*>>()
}
