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

  val updateConflictCases: List<UpdateConflictModelCase<*>> = RelationshipModelCatalog.updateConflictCases

  val bulkUpdateConflictCases: List<BulkUpdateConflictModelCase<*>> = RelationshipModelCatalog.bulkUpdateConflictCases

  val recursiveUpdateConflictCases: List<RecursiveUpdateConflictModelCase<*>> =
    RelationshipModelCatalog.recursiveUpdateConflictCases

  val bulkInsertCases: List<BulkInsertModelCase<*>> = allCases
    .filterIsInstance<BulkInsertModelCase<*>>()

  val bulkUpdateCases: List<BulkUpdateModelCase<*>> = allCases
    .filterIsInstance<BulkUpdateModelCase<*>>()

  val bulkPersistCases: List<BulkPersistModelCase<*>> = allCases
    .filterIsInstance<BulkPersistModelCase<*>>()

  val emptyBulkPersistCases: List<BulkPersistModelCase<*>> = listOf(
    ScalarModelCatalog.emptyBulkUpdateCase,
    IdentityModelCatalog.emptyBulkUpdateCase,
    RelationshipModelCatalog.emptyBulkUpdateCase
  )

  val emptyBulkUpdateCases: List<BulkUpdateModelCase<*>> = listOf(
    ScalarModelCatalog.emptyBulkUpdateCase,
    IdentityModelCatalog.emptyBulkUpdateCase,
    RelationshipModelCatalog.emptyBulkUpdateCase
  )

  val uniqueInsertCases: List<UniqueInsertModelCase<*>> = allCases
    .filterIsInstance<UniqueInsertModelCase<*>>()

  val recursiveInsertConflictCases: List<RecursiveInsertConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveInsertConflictModelCase<*>>()
}
