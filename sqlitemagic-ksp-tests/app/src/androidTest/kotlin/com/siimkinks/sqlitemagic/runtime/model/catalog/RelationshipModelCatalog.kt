package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object RelationshipModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(EntityWithRelationshipCase)

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

    override fun expectedAfterInsert(value: EntityWithRelationship, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }
}
