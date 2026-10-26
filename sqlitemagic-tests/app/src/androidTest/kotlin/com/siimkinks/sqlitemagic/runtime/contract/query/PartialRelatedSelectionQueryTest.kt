package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
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

internal class PartialRelatedSelectionQueryTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun reorderedPartialRelatedSelectionMapsExpectedDeepModel() {
    val expected = EntityWithRelationship().apply {
      value = "partial-relationship-value"
      relatedEntity = SimpleMutableEntity(
        id = null,
        value = "partial-related-value",
        boxedBoolean = false,
        primitiveBoolean = true
      )
      count = 77
    }
    insertedResult(
      result = expected
        .insert()
        .execute(),
      modelName = "EntityWithRelationship"
    )
    expected.relatedEntity = checkNotNull(expected.relatedEntity).copy(boxedBoolean = null)

    assertThat(
      SELECT
        .COLUMNS(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN,
          ENTITY_WITH_RELATIONSHIP.all()
        )
        .FROM(ENTITY_WITH_RELATIONSHIP)
        .mapTo(ENTITY_WITH_RELATIONSHIP)
        .execute()
    ).containsExactly(expected)
  }
}
