package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.model.catalog.ConstructionModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.EmbeddedModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.IdentityModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.RelationshipModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarModelCatalog

object ModelCatalog {
  val allCases: List<RuntimeModelCase<*>> = listOf(
    ScalarModelCatalog.cases,
    EmbeddedModelCatalog.cases,
    ConstructionModelCatalog.cases,
    IdentityModelCatalog.cases,
    RelationshipModelCatalog.cases
  ).flatten()

  val insertCases: List<InsertModelCase<*>> = allCases
    .filterIsInstance<InsertModelCase<*>>()

  val updateCases: List<UpdateModelCase<*>> = allCases
    .filterIsInstance<UpdateModelCase<*>>()

  val persistCases: List<PersistModelCase<*>> = allCases
    .filterIsInstance<PersistModelCase<*>>()

  val persistConflictCases = RelationshipModelCatalog.persistConflictCases

  val recursivePersistConflictCases = RelationshipModelCatalog.recursivePersistConflictCases

  val bulkInsertCases: List<BulkInsertModelCase<*>> = allCases
    .filterIsInstance<BulkInsertModelCase<*>>()

  val uniqueInsertCases: List<UniqueInsertModelCase<*>> = allCases
    .filterIsInstance<UniqueInsertModelCase<*>>()

  val recursiveInsertConflictCases: List<RecursiveInsertConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveInsertConflictModelCase<*>>()
}
