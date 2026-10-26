package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.runtime.model.catalog.ConstructionModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.DeclarationShapeModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.EmbeddedModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.IdentityModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.NoIdKeyModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.RelationshipBackedIdModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.RelationshipModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.ScalarModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.catalog.TransformerModelCatalog

internal object ModelCatalog {
  val allCases: List<RuntimeModelCase<*>> = listOf(
    ScalarModelCatalog.cases,
    EmbeddedModelCatalog.cases,
    ConstructionModelCatalog.cases,
    DeclarationShapeModelCatalog.cases,
    IdentityModelCatalog.cases,
    NoIdKeyModelCatalog.cases,
    RelationshipBackedIdModelCatalog.cases,
    RelationshipModelCatalog.cases,
    TransformerModelCatalog.cases
  ).flatten()

  val insertCases = allCases
    .filterIsInstance<InsertModelCase<*>>()

  val directModelQueryCases = allCases
    .filterIsInstance<InsertModelCase<*>>()
    .filterNot { it is RecursiveModelCase<*> }

  val relationshipModelQueryCases = allCases
    .filterIsInstance<RelationshipQueryModelCase<*>>()

  val transitiveRelationshipCases = allCases
    .filterIsInstance<TransitiveRelationshipModelCase<*>>()

  val successfulModelProjectionCases = allCases
    .filterIsInstance<SuccessfulModelProjectionCase<*>>()

  val rawCursorCases = allCases
    .filterIsInstance<RawCursorModelCase<*>>()

  val updateCases = allCases
    .filterIsInstance<UpdateModelCase<*>>()

  val persistCases = allCases
    .filterIsInstance<PersistModelCase<*>>()

  val persistConflictCases = allCases
    .filterIsInstance<PersistConflictModelCase<*>>()
    .filterNot { it is RecursivePersistConflictModelCase<*> }

  val recursivePersistConflictCases = allCases
    .filterIsInstance<RecursivePersistConflictModelCase<*>>()

  val updateConflictCases = allCases
    .filterIsInstance<UpdateConflictModelCase<*>>()
    .filterNot { it is RecursiveUpdateConflictModelCase<*> }

  val bulkUpdateConflictCases = allCases
    .filterIsInstance<BulkUpdateConflictModelCase<*>>()
    .filterNot { it is RecursiveUpdateConflictModelCase<*> }

  val recursiveUpdateConflictCases = allCases
    .filterIsInstance<RecursiveUpdateConflictModelCase<*>>()

  val bulkInsertCases = allCases
    .filterIsInstance<BulkInsertModelCase<*>>()

  val bulkUpdateCases = allCases
    .filterIsInstance<BulkUpdateModelCase<*>>()

  val bulkPersistCases = allCases
    .filterIsInstance<BulkPersistModelCase<*>>()

  val deleteCases = allCases
    .filterIsInstance<DeleteModelCase<*>>()

  val bulkDeleteCases = allCases
    .filterIsInstance<BulkDeleteModelCase<*>>()

  val tableDeleteCases = allCases
    .filterIsInstance<TableDeleteModelCase<*>>()

  val referencedDeleteCases = allCases
    .filterIsInstance<ReferencedDeleteModelCase<*, *>>()

  val nullOmittingPersistCases = allCases
    .filterIsInstance<NullOmittingPersistModelCase<*>>()

  val nullOmittingAllNullPersistCases = allCases
    .filterIsInstance<NullOmittingAllNullPersistModelCase<*>>()

  val nullOmittingPersistConflictCases = allCases
    .filterIsInstance<NullOmittingPersistConflictModelCase<*>>()
    .filterNot { it is RecursiveNullOmittingPersistConflictModelCase<*> }

  val recursiveNullOmittingPersistConflictCases = allCases
    .filterIsInstance<RecursiveNullOmittingPersistConflictModelCase<*>>()

  val emptyBulkPersistCases: List<BulkPersistModelCase<*>> = allCases
    .filterIsInstance<RepresentativeEmptyBulkModelCase<*>>()

  val emptyBulkUpdateCases: List<BulkUpdateModelCase<*>> = allCases
    .filterIsInstance<RepresentativeEmptyBulkModelCase<*>>()

  val uniqueInsertCases = allCases
    .filterIsInstance<UniqueInsertModelCase<*>>()

  val recursiveInsertConflictCases = allCases
    .filterIsInstance<RecursiveInsertConflictModelCase<*>>()

  val triggerCases = allCases
    .filterIsInstance<TriggerModelCase<*>>()

  val recursiveTriggerCases = allCases
    .filterIsInstance<RecursiveTriggerModelCase<*>>()

  val triggerConflictCases = allCases
    .filterIsInstance<TriggerConflictModelCase<*>>()

  val recursiveTriggerConflictCases = allCases
    .filterIsInstance<RecursiveTriggerConflictModelCase<*>>()
}
