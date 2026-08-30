package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithRelationships
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationshipTable.Companion.ENTITY_WITH_STRING_ID_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationships
import com.siimkinks.sqlitemagic.EntityWithUniqueRelationshipsTable.Companion.ENTITY_WITH_UNIQUE_RELATIONSHIPS
import com.siimkinks.sqlitemagic.EntityWithUniqueRelationshipss
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SimpleMutableEntitys
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.UniqueRelatedEntityTable.Companion.UNIQUE_RELATED_ENTITY
import com.siimkinks.sqlitemagic.UniqueRelatedEntitys
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.EntityWithStringIdRelationship
import com.siimkinks.sqlitemagic.fixture.model.EntityWithUniqueRelationships
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.UniqueRelatedEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.MissingRequiredProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.NullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveTriggerModelCase
import com.siimkinks.sqlitemagic.runtime.model.ReferencedDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardNullOmittingPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.SuccessfulModelProjectionCase
import com.siimkinks.sqlitemagic.runtime.model.TriggerConflictModelCase
import com.siimkinks.sqlitemagic.update

internal object RelationshipModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    EntityWithRelationshipCase,
    EntityWithNullRelationshipCase,
    EntityWithStringIdRelationshipCase,
    EntityWithUniqueRelationshipsCase,
    UniqueRelatedEntityCase,
  )

  internal val representativeEmptyBulkCase: BulkPersistModelCase<EntityWithRelationship> = EntityWithRelationshipCase

  private object UniqueRelatedEntityCase :
    TriggerConflictModelCase<UniqueRelatedEntity>,
    NullOmittingPersistConflictModelCase<UniqueRelatedEntity>,
    BulkUpdateConflictModelCase<UniqueRelatedEntity>,
    StandardBulkDeleteModelCase<UniqueRelatedEntity> {
    override val name = "UniqueRelatedEntity"
    override val table = UNIQUE_RELATED_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = UniqueRelatedEntity(
      id = null,
      uniqueValue = "unique-related-$sequence",
      value = "unique-related-value-$sequence"
    )

    override fun insert(value: UniqueRelatedEntity) = value.insert()

    override fun update(value: UniqueRelatedEntity) = value.update()

    override fun updatedValue(value: UniqueRelatedEntity, sequence: Int) = value.copy(
      id = value.id,
      uniqueValue = value.uniqueValue,
      value = "unique-related-updated-value-$sequence"
    )

    override fun bulkUpdate(values: Iterable<UniqueRelatedEntity>) = UniqueRelatedEntitys.update(values)

    override fun expectedAfterInsert(
      value: UniqueRelatedEntity,
      result: EntityInsertResult.Inserted
    ) = value

    override fun persist(value: UniqueRelatedEntity): EntityPersistBuilder = value.persist()

    override fun bulkPersist(values: Iterable<UniqueRelatedEntity>) = UniqueRelatedEntitys.persist(values)

    override val deleteBuilders = StandardDeleteBuilders(
      delete = UniqueRelatedEntity::delete,
      bulkDelete = UniqueRelatedEntitys::delete,
      deleteTable = UniqueRelatedEntitys::deleteTable
    )

    override fun withNullOmittingValues(value: UniqueRelatedEntity) = value.copy(value = null)

    override fun valueWithInsertConflict(
      existing: UniqueRelatedEntity,
      sequence: Int
    ) = UniqueRelatedEntity(
      id = null,
      uniqueValue = existing.uniqueValue,
      value = "unique-related-insert-conflict-value-$sequence"
    )

    override fun valueWithUpdateConflict(
      existing: UniqueRelatedEntity,
      conflicting: UniqueRelatedEntity,
      sequence: Int
    ) = UniqueRelatedEntity(
      id = existing.id,
      uniqueValue = conflicting.uniqueValue,
      value = "unique-related-update-conflict-value-$sequence"
    )

    override fun valueWithConflict(
      existing: UniqueRelatedEntity,
      conflicting: UniqueRelatedEntity,
      sequence: Int
    ) = UniqueRelatedEntity(
      id = existing.id,
      uniqueValue = conflicting.uniqueValue,
      value = "unique-related-update-conflict-value-$sequence"
    )

    override fun toString() = name
  }

  private object EntityWithRelationshipCase :
    RelationshipQueryModelCase<EntityWithRelationship>,
    SuccessfulModelProjectionCase<EntityWithRelationship>,
    RecursiveTriggerModelCase<EntityWithRelationship>,
    RecursiveBulkPersistModelCase<EntityWithRelationship>,
    RecursiveNullOmittingPersistModelCase<EntityWithRelationship>,
    StandardNullOmittingPersistModelCase<EntityWithRelationship>,
    ReferencedDeleteModelCase<EntityWithRelationship, SimpleMutableEntity> {
    override val name = "EntityWithRelationship"
    override val table = ENTITY_WITH_RELATIONSHIP
    override val relatedTable = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override val projectionColumns = listOf(
      ENTITY_WITH_RELATIONSHIP.ID,
      ENTITY_WITH_RELATIONSHIP.VALUE
    )

    override fun newValue(sequence: Int) = EntityWithRelationship().apply {
      value = "relationship-entity-with-relationship-$sequence"
      relatedEntity = SimpleMutableEntity(
        id = null,
        value = "relationship-simple-mutable-entity-$sequence",
        boxedBoolean = false,
        primitiveBoolean = true
      )
      count = 7 + sequence
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = EntityWithRelationship::insert,
      bulkInsert = EntityWithRelationships::insert,
      update = EntityWithRelationship::update,
      bulkUpdate = EntityWithRelationships::update,
      persist = EntityWithRelationship::persist,
      bulkPersist = EntityWithRelationships::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EntityWithRelationship::delete,
      bulkDelete = EntityWithRelationships::delete,
      deleteTable = EntityWithRelationships::deleteTable
    )

    override fun relatedDeleteValues(value: EntityWithRelationship) = listOfNotNull(value.relatedEntity)

    override fun executeRelatedDelete(value: SimpleMutableEntity) = value
      .delete()
      .execute()

    override fun observeRelatedDelete(value: SimpleMutableEntity) = value
      .delete()
      .observe()

    override fun executeRelatedBulkDelete(values: Collection<SimpleMutableEntity>) = SimpleMutableEntitys
      .delete(o = values)
      .execute()

    override fun observeRelatedBulkDelete(values: Collection<SimpleMutableEntity>) = SimpleMutableEntitys
      .delete(o = values)
      .observe()

    override fun executeRelatedTableDelete() = SimpleMutableEntitys
      .deleteTable()
      .execute()

    override fun observeRelatedTableDelete() = SimpleMutableEntitys
      .deleteTable()
      .observe()

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun expectedAfterProjection(value: EntityWithRelationship) = EntityWithRelationship().also {
      it.id = value.id
      it.value = value.value
    }

    override fun expectedAfterShallowQuery(deepExpected: EntityWithRelationship) = EntityWithRelationship().also {
      it.id = deepExpected.id
      it.value = deepExpected.value
      it.relatedEntity = deepExpected.relatedEntity?.let { related ->
        SimpleMutableEntity(id = related.id)
      }
      it.count = deepExpected.count
    }

    override fun updatedValue(value: EntityWithRelationship, sequence: Int) = value.apply {
      this.value = "relationship-entity-with-relationship-updated-$sequence"
      count = 17 + sequence
      relatedEntity?.let { related ->
        related.value = "relationship-simple-mutable-entity-updated-$sequence"
        related.boxedBoolean = sequence % 2 == 0
        related.primitiveBoolean = sequence % 2 != 0
      }
    }

    override fun partialNullValue(sequence: Int) = newValue(sequence = sequence)
      .copyForNullOmittingPersist(
        sequence = sequence,
        id = null
      )

    override fun partialNullUpdatedValue(value: EntityWithRelationship, sequence: Int) = value
      .copyForNullOmittingPersist(
        sequence = sequence,
        id = value.id
      )

    override fun expectedAfterNullOmittingUpdate(
      existing: EntityWithRelationship,
      value: EntityWithRelationship
    ) = EntityWithRelationship().apply {
      id = existing.id
      this.value = existing.value
      count = value.count
      relatedEntity = checkNotNull(existing.relatedEntity).copy(
        id = checkNotNull(value.relatedEntity).id,
        value = existing.relatedEntity?.value,
        boxedBoolean = existing.relatedEntity?.boxedBoolean,
        primitiveBoolean = checkNotNull(value.relatedEntity).primitiveBoolean
      )
    }

    override fun toString() = name
  }

  private object EntityWithNullRelationshipCase :
    RelationshipQueryModelCase<EntityWithRelationship>,
    RecursiveBulkPersistModelCase<EntityWithRelationship>,
    StandardBulkDeleteModelCase<EntityWithRelationship> {
    override val name = "EntityWithNullRelationship"
    override val table = ENTITY_WITH_RELATIONSHIP
    override val relatedTable = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithRelationship().apply {
      value = "relationship-null-$sequence"
      relatedEntity = null
      count = 7 + sequence
    }

    override val operationBuilders = StandardOperationBuilders(
      insert = EntityWithRelationship::insert,
      bulkInsert = EntityWithRelationships::insert,
      update = EntityWithRelationship::update,
      bulkUpdate = EntityWithRelationships::update,
      persist = EntityWithRelationship::persist,
      bulkPersist = EntityWithRelationships::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EntityWithRelationship::delete,
      bulkDelete = EntityWithRelationships::delete,
      deleteTable = EntityWithRelationships::deleteTable
    )

    override fun relatedValues(value: EntityWithRelationship): List<*> = listOfNotNull(value.relatedEntity)

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun expectedAfterShallowQuery(deepExpected: EntityWithRelationship) = EntityWithRelationship().also {
      it.id = deepExpected.id
      it.value = deepExpected.value
      it.relatedEntity = null
      it.count = deepExpected.count
    }

    override fun updatedValue(value: EntityWithRelationship, sequence: Int) = value.apply {
      this.value = "relationship-null-updated-$sequence"
      count = 17 + sequence
    }

    override fun toString() = name
  }

  private object EntityWithStringIdRelationshipCase :
    RelationshipQueryModelCase<EntityWithStringIdRelationship>,
    MissingRequiredProjectionCase<EntityWithStringIdRelationship>,
    RecursiveBulkPersistModelCase<EntityWithStringIdRelationship>,
    StandardBulkDeleteModelCase<EntityWithStringIdRelationship> {
    override val name = "EntityWithStringIdRelationship"
    override val table = ENTITY_WITH_STRING_ID_RELATIONSHIP
    override val relatedTable = STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override val missingRequiredProjectionColumns = listOf(
      ENTITY_WITH_STRING_ID_RELATIONSHIP.ID,
      ENTITY_WITH_STRING_ID_RELATIONSHIP.VALUE
    )
    override val expectedSQLExceptionMessage =
      "Column related_entity is not nullable and was not part of selected columns"

    override fun newValue(sequence: Int) = EntityWithStringIdRelationship(
      id = null,
      value = "string-id-relationship-$sequence",
      relatedEntity = StringIdEntity(
        id = "string-id-related-$sequence",
        value = "string-id-related-value-$sequence"
      )
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = EntityWithStringIdRelationship::insert,
      bulkInsert = EntityWithStringIdRelationships::insert,
      update = EntityWithStringIdRelationship::update,
      bulkUpdate = EntityWithStringIdRelationships::update,
      persist = EntityWithStringIdRelationship::persist,
      bulkPersist = EntityWithStringIdRelationships::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EntityWithStringIdRelationship::delete,
      bulkDelete = EntityWithStringIdRelationships::delete,
      deleteTable = EntityWithStringIdRelationships::deleteTable
    )

    override fun relatedValues(value: EntityWithStringIdRelationship): List<*> = listOf(value.relatedEntity)

    override fun expectedAfterInsert(
      value: EntityWithStringIdRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun expectedAfterShallowQuery(deepExpected: EntityWithStringIdRelationship) = deepExpected

    override fun expectedAfterBulkInsert(
      values: List<EntityWithStringIdRelationship>,
      actual: List<EntityWithStringIdRelationship>
    ) = actual.map { persisted ->
      values
        .single { it.value == persisted.value }
        .copy(id = persisted.id)
    }

    override fun updatedValue(value: EntityWithStringIdRelationship, sequence: Int) = value.copy(
      id = value.id,
      value = "string-id-relationship-updated-$sequence",
      relatedEntity = value.relatedEntity.copy(
        id = value.relatedEntity.id,
        value = "string-id-related-value-updated-$sequence"
      )
    )

    override fun toString() = name
  }

  private object EntityWithUniqueRelationshipsCase :
    RelationshipQueryModelCase<EntityWithUniqueRelationships>,
    RecursiveTriggerConflictModelCase<EntityWithUniqueRelationships>,
    RecursiveNullOmittingPersistConflictModelCase<EntityWithUniqueRelationships>,
    RecursiveBulkPersistModelCase<EntityWithUniqueRelationships>,
    StandardBulkDeleteModelCase<EntityWithUniqueRelationships> {
    override val name = "EntityWithUniqueRelationships"
    override val table = ENTITY_WITH_UNIQUE_RELATIONSHIPS
    override val relatedTable = UNIQUE_RELATED_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithUniqueRelationships(
      id = null,
      uniqueValue = "unique-parent-$sequence",
      firstRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = "unique-first-child-$sequence",
        value = "unique-first-child-value-$sequence"
      ),
      secondRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = "unique-second-child-$sequence",
        value = "unique-second-child-value-$sequence"
      )
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = EntityWithUniqueRelationships::insert,
      bulkInsert = EntityWithUniqueRelationshipss::insert,
      update = EntityWithUniqueRelationships::update,
      bulkUpdate = EntityWithUniqueRelationshipss::update,
      persist = EntityWithUniqueRelationships::persist,
      bulkPersist = EntityWithUniqueRelationshipss::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = EntityWithUniqueRelationships::delete,
      bulkDelete = EntityWithUniqueRelationshipss::delete,
      deleteTable = EntityWithUniqueRelationshipss::deleteTable
    )

    override fun withNullOmittingValues(value: EntityWithUniqueRelationships) = value.copy(
      firstRelatedEntity = value.firstRelatedEntity.copy(value = null),
      secondRelatedEntity = value.secondRelatedEntity.copy(value = null)
    )

    override fun expectedAfterInsert(
      value: EntityWithUniqueRelationships,
      result: EntityInsertResult.Inserted
    ) = value

    override fun expectedAfterShallowQuery(deepExpected: EntityWithUniqueRelationships) = deepExpected

    override fun relatedValues(value: EntityWithUniqueRelationships): List<*> = listOf(
      value.firstRelatedEntity,
      value.secondRelatedEntity
    )

    override fun valueWithParentConflict(
      existing: EntityWithUniqueRelationships,
      sequence: Int
    ) = EntityWithUniqueRelationships(
      id = null,
      uniqueValue = existing.uniqueValue,
      firstRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = "unique-parent-conflict-first-child-$sequence",
        value = "unique-parent-conflict-first-child-value-$sequence"
      ),
      secondRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = "unique-parent-conflict-second-child-$sequence",
        value = "unique-parent-conflict-second-child-value-$sequence"
      )
    )

    override fun valueWithChildConflict(
      existing: EntityWithUniqueRelationships,
      sequence: Int
    ) = EntityWithUniqueRelationships(
      id = null,
      uniqueValue = "unique-child-conflict-parent-$sequence",
      firstRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = "unique-child-conflict-first-child-$sequence",
        value = "unique-child-conflict-first-child-value-$sequence"
      ),
      secondRelatedEntity = UniqueRelatedEntity(
        id = null,
        uniqueValue = existing.secondRelatedEntity.uniqueValue,
        value = "unique-child-conflict-second-child-value-$sequence"
      )
    )

    override fun valueWithConflict(
      existing: EntityWithUniqueRelationships,
      conflicting: EntityWithUniqueRelationships,
      sequence: Int
    ) = valueWithParentConflict(
      existing = existing,
      conflicting = conflicting,
      sequence = sequence
    )

    override fun valueWithParentConflict(
      existing: EntityWithUniqueRelationships,
      conflicting: EntityWithUniqueRelationships,
      sequence: Int
    ) = existing.copy(
      id = existing.id,
      uniqueValue = conflicting.uniqueValue,
      firstRelatedEntity = existing.firstRelatedEntity.copy(
        id = existing.firstRelatedEntity.id,
        uniqueValue = existing.firstRelatedEntity.uniqueValue,
        value = "unique-parent-conflict-first-child-updated-$sequence"
      ),
      secondRelatedEntity = existing.secondRelatedEntity.copy(
        id = existing.secondRelatedEntity.id,
        uniqueValue = existing.secondRelatedEntity.uniqueValue,
        value = "unique-parent-conflict-second-child-updated-$sequence"
      )
    )

    override fun valueWithChildConflict(
      existing: EntityWithUniqueRelationships,
      conflicting: EntityWithUniqueRelationships,
      sequence: Int
    ) = existing.copy(
      id = existing.id,
      uniqueValue = existing.uniqueValue,
      firstRelatedEntity = existing.firstRelatedEntity.copy(
        id = existing.firstRelatedEntity.id,
        uniqueValue = existing.firstRelatedEntity.uniqueValue,
        value = "unique-child-conflict-first-child-updated-$sequence"
      ),
      secondRelatedEntity = existing.secondRelatedEntity.copy(
        id = existing.secondRelatedEntity.id,
        uniqueValue = conflicting.secondRelatedEntity.uniqueValue,
        value = "unique-child-conflict-second-child-updated-$sequence"
      )
    )

    override fun valueWithInsertConflict(
      existing: EntityWithUniqueRelationships,
      sequence: Int
    ) = valueWithParentConflict(
      existing = existing,
      sequence = sequence
    )

    override fun valueWithUpdateConflict(
      existing: EntityWithUniqueRelationships,
      conflicting: EntityWithUniqueRelationships,
      sequence: Int
    ) = updatedValue(
      value = existing,
      sequence = sequence
    ).copy(uniqueValue = conflicting.uniqueValue)

    override fun updatedValue(value: EntityWithUniqueRelationships, sequence: Int) = value.copy(
      id = value.id,
      uniqueValue = value.uniqueValue,
      firstRelatedEntity = value.firstRelatedEntity.copy(
        id = value.firstRelatedEntity.id,
        uniqueValue = value.firstRelatedEntity.uniqueValue,
        value = "unique-first-child-updated-$sequence"
      ),
      secondRelatedEntity = value.secondRelatedEntity.copy(
        id = value.secondRelatedEntity.id,
        uniqueValue = value.secondRelatedEntity.uniqueValue,
        value = "unique-second-child-updated-$sequence"
      )
    )


    override fun toString() = name
  }

  private fun EntityWithRelationship.copyWithGeneratedId(result: EntityInsertResult.Inserted) =
    EntityWithRelationship().also { entity ->
      entity.id = checkNotNull(result.rowId)
      entity.value = value
      entity.relatedEntity = relatedEntity
      entity.count = count
    }

  private fun EntityWithRelationship.copyForNullOmittingPersist(
    sequence: Int,
    id: Long?
  ) = EntityWithRelationship().also { entity ->
    entity.id = id
    entity.value = null
    entity.count = 100 + sequence
    entity.relatedEntity = checkNotNull(relatedEntity).copy(
      id = relatedEntity?.id,
      value = null,
      boxedBoolean = null,
      primitiveBoolean = sequence % 2 == 0
    )
  }
}
