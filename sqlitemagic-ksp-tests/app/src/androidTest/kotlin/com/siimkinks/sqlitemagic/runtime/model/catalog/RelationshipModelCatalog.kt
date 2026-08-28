package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithRelationships
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationshipTable.Companion.ENTITY_WITH_STRING_ID_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationships
import com.siimkinks.sqlitemagic.EntityWithUniqueRelationshipsTable.Companion.ENTITY_WITH_UNIQUE_RELATIONSHIPS
import com.siimkinks.sqlitemagic.EntityWithUniqueRelationshipss
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.UniqueRelatedEntityTable.Companion.UNIQUE_RELATED_ENTITY
import com.siimkinks.sqlitemagic.UniqueRelatedEntitys
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
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.PersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.PersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveBulkUpdateModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursivePersistConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveUpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.UpdateConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
import com.siimkinks.sqlitemagic.update
import kotlin.collections.List
import kotlin.collections.copy
import kotlin.collections.listOf
import kotlin.collections.listOfNotNull
import kotlin.collections.map
import kotlin.collections.single

internal object RelationshipModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    EntityWithRelationshipCase,
    EntityWithNullRelationshipCase,
    EntityWithStringIdRelationshipCase,
    EntityWithUniqueRelationshipsCase,
  )

  val persistConflictCases: List<PersistConflictModelCase<*>> = listOf(
    UniqueRelatedEntityCase
  )

  val recursivePersistConflictCases: List<RecursivePersistConflictModelCase<*>> = listOf(
    EntityWithUniqueRelationshipsCase
  )

  val updateConflictCases: List<UpdateConflictModelCase<*>> = listOf(
    UniqueRelatedEntityCase
  )

  val bulkUpdateConflictCases: List<BulkUpdateConflictModelCase<*>> = listOf(
    UniqueRelatedEntityCase
  )

  val recursiveUpdateConflictCases: List<RecursiveUpdateConflictModelCase<*>> = listOf(
    EntityWithUniqueRelationshipsCase
  )

  internal val emptyBulkUpdateCase: BulkUpdateModelCase<EntityWithRelationship> = EntityWithRelationshipCase

  private object UniqueRelatedEntityCase :
    PersistConflictModelCase<UniqueRelatedEntity>,
    BulkUpdateConflictModelCase<UniqueRelatedEntity> {
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

    override fun bulkUpdate(values: List<UniqueRelatedEntity>) = UniqueRelatedEntitys.update(values)

    override fun expectedAfterInsert(
      value: UniqueRelatedEntity,
      result: EntityInsertResult.Inserted
    ) = value

    override fun persist(value: UniqueRelatedEntity): EntityPersistBuilder = value.persist()

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
    RecursiveBulkUpdateModelCase<EntityWithRelationship>,
    PersistModelCase<EntityWithRelationship> {
    override val name = "EntityWithRelationship"
    override val table = ENTITY_WITH_RELATIONSHIP
    override val relatedTable = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

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

    override fun insert(value: EntityWithRelationship) = value.insert()

    override fun bulkInsert(values: List<EntityWithRelationship>) = EntityWithRelationships.insert(values)

    override fun relatedValues(value: EntityWithRelationship): List<*> = listOfNotNull(value.relatedEntity)

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun updatedValue(value: EntityWithRelationship, sequence: Int) = value.apply {
      this.value = "relationship-entity-with-relationship-updated-$sequence"
      count = 17 + sequence
      relatedEntity?.let { related ->
        related.value = "relationship-simple-mutable-entity-updated-$sequence"
        related.boxedBoolean = sequence % 2 == 0
        related.primitiveBoolean = sequence % 2 != 0
      }
    }

    override fun executeUpdate(
      value: EntityWithRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: EntityWithRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<EntityWithRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<EntityWithRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: EntityWithRelationship) = value
      .persist()
      .execute()

    override fun observePersist(value: EntityWithRelationship) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object EntityWithNullRelationshipCase :
    RecursiveBulkUpdateModelCase<EntityWithRelationship>,
    PersistModelCase<EntityWithRelationship> {
    override val name = "EntityWithNullRelationship"
    override val table = ENTITY_WITH_RELATIONSHIP
    override val relatedTable = SIMPLE_MUTABLE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithRelationship().apply {
      value = "relationship-null-$sequence"
      relatedEntity = null
      count = 7 + sequence
    }

    override fun insert(value: EntityWithRelationship) = value.insert()

    override fun bulkInsert(values: List<EntityWithRelationship>) = EntityWithRelationships.insert(values)

    override fun relatedValues(value: EntityWithRelationship): List<*> = listOfNotNull(value.relatedEntity)

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun updatedValue(value: EntityWithRelationship, sequence: Int) = value.apply {
      this.value = "relationship-null-updated-$sequence"
      count = 17 + sequence
    }

    override fun executeUpdate(
      value: EntityWithRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: EntityWithRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<EntityWithRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<EntityWithRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: EntityWithRelationship) = value
      .persist()
      .execute()

    override fun observePersist(value: EntityWithRelationship) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object EntityWithStringIdRelationshipCase :
    RecursiveBulkUpdateModelCase<EntityWithStringIdRelationship>,
    PersistModelCase<EntityWithStringIdRelationship> {
    override val name = "EntityWithStringIdRelationship"
    override val table = ENTITY_WITH_STRING_ID_RELATIONSHIP
    override val relatedTable = STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithStringIdRelationship(
      id = null,
      value = "string-id-relationship-$sequence",
      relatedEntity = StringIdEntity(
        id = "string-id-related-$sequence",
        value = "string-id-related-value-$sequence"
      )
    )

    override fun insert(value: EntityWithStringIdRelationship) = value.insert()

    override fun bulkInsert(values: List<EntityWithStringIdRelationship>) =
      EntityWithStringIdRelationships.insert(values)

    override fun relatedValues(value: EntityWithStringIdRelationship): List<*> = listOf(value.relatedEntity)

    override fun expectedAfterInsert(
      value: EntityWithStringIdRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

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

    override fun executeUpdate(
      value: EntityWithStringIdRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: EntityWithStringIdRelationship,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<EntityWithStringIdRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithStringIdRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<EntityWithStringIdRelationship>,
      conflictAlgorithm: Int?
    ) = EntityWithStringIdRelationships
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: EntityWithStringIdRelationship) = value
      .persist()
      .execute()

    override fun observePersist(value: EntityWithStringIdRelationship) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object EntityWithUniqueRelationshipsCase :
    RecursivePersistConflictModelCase<EntityWithUniqueRelationships>,
    RecursiveUpdateConflictModelCase<EntityWithUniqueRelationships>,
    RecursiveBulkUpdateModelCase<EntityWithUniqueRelationships>,
    PersistModelCase<EntityWithUniqueRelationships> {
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

    override fun insert(value: EntityWithUniqueRelationships) = value.insert()

    override fun update(value: EntityWithUniqueRelationships) = value.update()

    override fun bulkInsert(values: List<EntityWithUniqueRelationships>) =
      EntityWithUniqueRelationshipss.insert(values)

    override fun bulkUpdate(values: List<EntityWithUniqueRelationships>) = EntityWithUniqueRelationshipss.update(values)

    override fun expectedAfterInsert(
      value: EntityWithUniqueRelationships,
      result: EntityInsertResult.Inserted
    ) = value

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

    override fun persist(value: EntityWithUniqueRelationships) = value.persist()

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

    override fun executeUpdate(
      value: EntityWithUniqueRelationships,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: EntityWithUniqueRelationships,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<EntityWithUniqueRelationships>,
      conflictAlgorithm: Int?
    ) = EntityWithUniqueRelationshipss
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<EntityWithUniqueRelationships>,
      conflictAlgorithm: Int?
    ) = EntityWithUniqueRelationshipss
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: EntityWithUniqueRelationships) = value
      .persist()
      .execute()

    override fun observePersist(value: EntityWithUniqueRelationships) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private fun EntityWithRelationship.copyWithGeneratedId(result: EntityInsertResult.Inserted) =
    EntityWithRelationship().also { entity ->
      entity.id = checkNotNull(result.rowId)
      entity.value = value
      entity.relatedEntity = relatedEntity
      entity.count = count
    }
}
