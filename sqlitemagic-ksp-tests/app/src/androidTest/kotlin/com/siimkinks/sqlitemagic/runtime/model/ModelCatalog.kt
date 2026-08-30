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

  val directModelQueryCases: List<InsertModelCase<*>> = allCases
    .filterIsInstance<InsertModelCase<*>>()
    .filterNot { it is RecursiveModelCase<*> }

  val relationshipModelQueryCases: List<RelationshipQueryModelCase<*>> = allCases
    .filterIsInstance<RelationshipQueryModelCase<*>>()

  val successfulModelProjectionCases: List<SuccessfulModelProjectionCase<*>> = allCases
    .filterIsInstance<SuccessfulModelProjectionCase<*>>()

  val missingRequiredProjectionCases: List<MissingRequiredProjectionCase<*>> = allCases
    .filterIsInstance<MissingRequiredProjectionCase<*>>()

  val updateCases: List<UpdateModelCase<*>> = allCases
    .filterIsInstance<UpdateModelCase<*>>()

  val persistCases: List<PersistModelCase<*>> = allCases
    .filterIsInstance<PersistModelCase<*>>()

  val persistConflictCases: List<PersistConflictModelCase<*>> = allCases
    .filterIsInstance<PersistConflictModelCase<*>>()
    .filterNot { it is RecursivePersistConflictModelCase<*> }

  val recursivePersistConflictCases: List<RecursivePersistConflictModelCase<*>> = allCases
    .filterIsInstance<RecursivePersistConflictModelCase<*>>()

  val updateConflictCases: List<UpdateConflictModelCase<*>> = allCases
    .filterIsInstance<UpdateConflictModelCase<*>>()
    .filterNot { it is RecursiveUpdateConflictModelCase<*> }

  val bulkUpdateConflictCases: List<BulkUpdateConflictModelCase<*>> = allCases
    .filterIsInstance<BulkUpdateConflictModelCase<*>>()
    .filterNot { it is RecursiveUpdateConflictModelCase<*> }

  val recursiveUpdateConflictCases: List<RecursiveUpdateConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveUpdateConflictModelCase<*>>()

  val bulkInsertCases: List<BulkInsertModelCase<*>> = allCases
    .filterIsInstance<BulkInsertModelCase<*>>()

  val bulkUpdateCases: List<BulkUpdateModelCase<*>> = allCases
    .filterIsInstance<BulkUpdateModelCase<*>>()

  val bulkPersistCases: List<BulkPersistModelCase<*>> = allCases
    .filterIsInstance<BulkPersistModelCase<*>>()

  val deleteCases: List<DeleteModelCase<*>> = allCases
    .filterIsInstance<DeleteModelCase<*>>()

  val bulkDeleteCases: List<BulkDeleteModelCase<*>> = allCases
    .filterIsInstance<BulkDeleteModelCase<*>>()

  val tableDeleteCases: List<TableDeleteModelCase<*>> = allCases
    .filterIsInstance<TableDeleteModelCase<*>>()

  val referencedDeleteCases: List<ReferencedDeleteModelCase<*, *>> = allCases
    .filterIsInstance<ReferencedDeleteModelCase<*, *>>()

  val nullOmittingPersistCases: List<NullOmittingPersistModelCase<*>> = allCases
    .filterIsInstance<NullOmittingPersistModelCase<*>>()

  val nullOmittingAllNullPersistCases: List<NullOmittingAllNullPersistModelCase<*>> = allCases
    .filterIsInstance<NullOmittingAllNullPersistModelCase<*>>()

  val nullOmittingPersistConflictCases: List<NullOmittingPersistConflictModelCase<*>> = allCases
    .filterIsInstance<NullOmittingPersistConflictModelCase<*>>()
    .filterNot { it is RecursiveNullOmittingPersistConflictModelCase<*> }

  val recursiveNullOmittingPersistConflictCases: List<RecursiveNullOmittingPersistConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveNullOmittingPersistConflictModelCase<*>>()

  val emptyBulkPersistCases: List<BulkPersistModelCase<*>> = listOf(
    ScalarModelCatalog.representativeEmptyBulkCase,
    IdentityModelCatalog.representativeEmptyBulkCase,
    RelationshipModelCatalog.representativeEmptyBulkCase
  )

  val emptyBulkUpdateCases: List<BulkUpdateModelCase<*>> = listOf(
    ScalarModelCatalog.representativeEmptyBulkCase,
    IdentityModelCatalog.representativeEmptyBulkCase,
    RelationshipModelCatalog.representativeEmptyBulkCase
  )

  val uniqueInsertCases: List<UniqueInsertModelCase<*>> = allCases
    .filterIsInstance<UniqueInsertModelCase<*>>()

  val recursiveInsertConflictCases: List<RecursiveInsertConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveInsertConflictModelCase<*>>()

  val triggerCases: List<TriggerModelCase<*>> = allCases
    .filterIsInstance<TriggerModelCase<*>>()

  val recursiveTriggerCases: List<RecursiveTriggerModelCase<*>> = allCases
    .filterIsInstance<RecursiveTriggerModelCase<*>>()

  val triggerConflictCases: List<TriggerConflictModelCase<*>> = allCases
    .filterIsInstance<TriggerConflictModelCase<*>>()

  val recursiveTriggerConflictCases: List<RecursiveTriggerConflictModelCase<*>> = allCases
    .filterIsInstance<RecursiveTriggerConflictModelCase<*>>()
}
