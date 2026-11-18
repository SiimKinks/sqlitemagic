package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationshipTable.Companion.ENTITY_WITH_STRING_ID_RELATIONSHIP
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.Row3
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.UNION_ALL
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.EntityWithStringIdRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.queryDeep
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
      val live: List<Pair<Int?, String?>>,
      val always: List<Row2<EntityWithStringIdRelationship, String>>,
      val deep: List<Row2<EntityWithRelationship, Int?>>,
      val outer: List<Row2<SimpleMutableEntity, EntityWithRelationship?>>,
      val derived: List<Row2<EntityWithRelationship, Int?>>,
      val compound: List<Row2<EntityWithStringIdRelationship, String>>
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
    val withStringId = EntityWithStringIdRelationship(
      id = null,
      value = "child",
      relatedEntity = StringIdEntity(
        id = "key",
        value = "parent"
      )
    )
    val stringIdRow = insertedResult(
      result = withStringId
        .insert()
        .execute(),
      modelName = "EntityWithStringIdRelationship"
    )
    val related = checkNotNull(withRelationship.relatedEntity)
    val joinedOn = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS SIMPLE_MUTABLE_ENTITY.ID
    val shelfCount = ENTITY_WITH_RELATIONSHIP.COUNT AS "shelf_count"
    val shelf = SELECT
      .COLUMNS(ENTITY_WITH_RELATIONSHIP, shelfCount)
      .FROM(ENTITY_WITH_RELATIONSHIP)
      .queryDeep() AS "shelf"
    val withStringIds = SELECT
      .COLUMNS(ENTITY_WITH_STRING_ID_RELATIONSHIP, STRING_ID_ENTITY.VALUE)
      .FROM(ENTITY_WITH_STRING_ID_RELATIONSHIP)

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
        .execute(),
      always = SELECT
        .COLUMNS(ENTITY_WITH_STRING_ID_RELATIONSHIP, STRING_ID_ENTITY.VALUE)
        .FROM(ENTITY_WITH_STRING_ID_RELATIONSHIP)
        .execute(),
      deep = SELECT
        .COLUMNS(ENTITY_WITH_RELATIONSHIP, ENTITY_WITH_RELATIONSHIP.COUNT)
        .FROM(ENTITY_WITH_RELATIONSHIP)
        .queryDeep()
        .execute(),
      outer = SELECT
        .COLUMNS(SIMPLE_MUTABLE_ENTITY, ENTITY_WITH_RELATIONSHIP.nullable())
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .LEFT_JOIN(ENTITY_WITH_RELATIONSHIP)
        .ON(joinedOn)
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .queryDeep()
        .execute(),
      derived = SELECT
        .COLUMNS(shelf[ENTITY_WITH_RELATIONSHIP], shelf[shelfCount])
        .FROM(shelf)
        .execute(),
      compound = (withStringIds UNION_ALL withStringIds).execute()
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
        live = listOf(7 to "related"),
        always = listOf(Row2(withStringId.copy(id = stringIdRow.rowId), "parent")),
        deep = listOf(Row2(withRelationship, 7)),
        outer = listOf(Row2(related, withRelationship), Row2(unrelated, null)),
        derived = listOf(Row2(withRelationship, 7)),
        compound = List(2) { Row2(withStringId.copy(id = stringIdRow.rowId), "parent") }
      )
    )
  }
}
