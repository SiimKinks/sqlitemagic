package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test

internal class EntityValueQueryTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun entityValuesReadGeneratedEntitiesAtTheirColumns() {
    data class Reads(
      val joined: List<Row3<String?, EntityWithRelationship, SimpleMutableEntity>>,
      val counted: List<Pair<SimpleMutableEntity, Long>>,
      val live: List<Pair<Int?, String?>>
    )

    val withRelationship = EntityWithRelationship().apply {
      value = "parent"
      relatedEntity = SimpleMutableEntity(
        value = "related",
        boxedBoolean = true,
        primitiveBoolean = true
      )
      count = 7
    }
    insertedResult(
      result = withRelationship
        .insert()
        .execute(),
      modelName = "EntityWithRelationship"
    )
    val unrelated = SimpleMutableEntity(value = "alone")
    insertedResult(
      result = unrelated
        .insert()
        .execute(),
      modelName = "SimpleMutableEntity"
    )
    val related = checkNotNull(withRelationship.relatedEntity)
    val joinedOn = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS SIMPLE_MUTABLE_ENTITY.ID

    val reads = Reads(
      joined = SELECT
        .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, ENTITY_WITH_RELATIONSHIP, SIMPLE_MUTABLE_ENTITY)
        .FROM(ENTITY_WITH_RELATIONSHIP)
        .INNER_JOIN(SIMPLE_MUTABLE_ENTITY)
        .ON(joinedOn)
        .execute(),
      counted = SELECT
        .COLUMNS(SIMPLE_MUTABLE_ENTITY, COUNT(ENTITY_WITH_RELATIONSHIP.ID))
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .LEFT_JOIN(ENTITY_WITH_RELATIONSHIP)
        .ON(joinedOn)
        .GROUP_BY(SIMPLE_MUTABLE_ENTITY.ID)
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .mapTo(::Pair)
        .execute(),
      live = SELECT
        .COLUMNS(ENTITY_WITH_RELATIONSHIP, SIMPLE_MUTABLE_ENTITY.all())
        .FROM(ENTITY_WITH_RELATIONSHIP)
        .INNER_JOIN(SIMPLE_MUTABLE_ENTITY)
        .ON(joinedOn)
        .mapTo { row -> row[ENTITY_WITH_RELATIONSHIP].count to row[SIMPLE_MUTABLE_ENTITY.VALUE] }
        .execute()
    )

    val shallowParent = EntityWithRelationship().apply {
      id = withRelationship.id
      value = "parent"
      relatedEntity = SimpleMutableEntity(id = related.id)
      count = 7
    }
    assertThat(reads).isEqualTo(
      Reads(
        joined = listOf(Row3("related", shallowParent, related)),
        counted = listOf(related to 1L, unrelated to 0L),
        live = listOf(7 to "related")
      )
    )
  }
}
