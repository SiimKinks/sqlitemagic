package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithUniqueRelationshipsTable
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationshipTable
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable
import com.siimkinks.sqlitemagic.UniqueRelatedEntityTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.EntityWithStringIdRelationship
import com.siimkinks.sqlitemagic.fixture.model.EntityWithUniqueRelationships
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.UniqueRelatedEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RecursiveInsertConflictModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object RelationshipModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    EntityWithRelationshipCase,
    EntityWithNullRelationshipCase,
    EntityWithStringIdRelationshipCase,
    EntityWithUniqueRelationshipsCase,
  )

  private object EntityWithRelationshipCase : InsertModelCase<EntityWithRelationship> {
    override val name = "EntityWithRelationship"
    override val table = EntityWithRelationshipTable.ENTITY_WITH_RELATIONSHIP
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithRelationship().apply {
      value = "relationship-entity-with-relationship"
      relatedEntity = SimpleMutableEntity(
        id = null,
        value = "relationship-simple-mutable-entity",
        boxedBoolean = false,
        primitiveBoolean = true
      )
      count = 7
    }

    override fun insert(value: EntityWithRelationship) = value.insert()

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun toString() = name
  }

  private object EntityWithNullRelationshipCase : InsertModelCase<EntityWithRelationship> {
    override val name = "EntityWithNullRelationship"
    override val table = EntityWithRelationshipTable.ENTITY_WITH_RELATIONSHIP
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = EntityWithRelationship().apply {
      value = "relationship-null-$sequence"
      relatedEntity = null
      count = 7 + sequence
    }

    override fun insert(value: EntityWithRelationship) = value.insert()

    override fun expectedAfterInsert(
      value: EntityWithRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copyWithGeneratedId(result = result)

    override fun toString() = name
  }

  private object EntityWithStringIdRelationshipCase : InsertModelCase<EntityWithStringIdRelationship> {
    override val name = "EntityWithStringIdRelationship"
    override val table = EntityWithStringIdRelationshipTable.ENTITY_WITH_STRING_ID_RELATIONSHIP
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

    override fun expectedAfterInsert(
      value: EntityWithStringIdRelationship,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = checkNotNull(result.rowId))

    override fun toString() = name
  }

  private object EntityWithUniqueRelationshipsCase :
    RecursiveInsertConflictModelCase<EntityWithUniqueRelationships> {
    override val name = "EntityWithUniqueRelationships"
    override val table = EntityWithUniqueRelationshipsTable.ENTITY_WITH_UNIQUE_RELATIONSHIPS
    override val relatedTable = UniqueRelatedEntityTable.UNIQUE_RELATED_ENTITY
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

    override fun expectedAfterInsert(
      value: EntityWithUniqueRelationships,
      result: EntityInsertResult.Inserted
    ) = value

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
