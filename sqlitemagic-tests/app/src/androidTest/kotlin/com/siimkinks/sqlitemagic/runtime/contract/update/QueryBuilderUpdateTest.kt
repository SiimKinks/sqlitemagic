package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSimpleRows
import com.siimkinks.sqlitemagic.runtime.support.insertSimpleRow
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test

internal class QueryBuilderUpdateTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeRawPredicateAssignment() {
    val first = SimpleMutableEntity(
      id = 101L,
      value = "first"
    )
    val second = SimpleMutableEntity(
      id = 102L,
      value = "second"
    )
    insertSimpleRow(value = first)
    insertSimpleRow(value = second)

    val affectedRows = (UPDATE TABLE SIMPLE_MUTABLE_ENTITY SET (SIMPLE_MUTABLE_ENTITY.VALUE to "raw-updated"))
      .WHERE("simple_mutable_entity.id = ?".expr(BOOLEAN, checkNotNull(second.id)))
      .compile()
      .execute()

    assertThat(affectedRows).isEqualTo(1)
    assertSimpleRows(
      first,
      second.copy(value = "raw-updated")
    )
  }

  @Test
  fun executeTypedWholeTableAssignments() {
    val first = SimpleMutableEntity(
      id = 201L,
      value = "first"
    )
    val second = SimpleMutableEntity(
      id = 202L,
      value = null
    )
    insertSimpleRow(value = first)
    insertSimpleRow(value = second)

    val affectedRows = UPDATE
      .TABLE(SIMPLE_MUTABLE_ENTITY)
      .SET(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN, true)
      .SET(SIMPLE_MUTABLE_ENTITY.VALUE, "typed-updated")
      .SET(SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN, false)
      .compile()
      .execute()

    assertThat(affectedRows).isEqualTo(2)
    assertSimpleRows(
      first.copy(
        value = "typed-updated",
        boxedBoolean = false,
        primitiveBoolean = true
      ),
      second.copy(
        value = "typed-updated",
        boxedBoolean = false,
        primitiveBoolean = true
      )
    )
  }

  @Test
  fun executeExplicitNullPredicatePublishesOnlyForChangedRows() {
    val first = SimpleMutableEntity(
      id = 301L,
      value = "first"
    )
    val second = SimpleMutableEntity(
      id = 302L,
      value = "second"
    )
    insertSimpleRow(value = first)
    insertSimpleRow(value = second)
    val expected = listOf(first.copy(value = null), second)
    val observer = SELECT
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(listOf(first, second))
    try {
      val affectedRows = UPDATE
        .TABLE(SIMPLE_MUTABLE_ENTITY)
        .SET(SIMPLE_MUTABLE_ENTITY.VALUE, null)
        .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS checkNotNull(first.id))
        .compile()
        .execute()

      assertThat(affectedRows).isEqualTo(1)
      observer.assertValuesOnly(
        listOf(first, second),
        expected
      )
      assertThat(
        UPDATE
          .TABLE(SIMPLE_MUTABLE_ENTITY)
          .SET(SIMPLE_MUTABLE_ENTITY.VALUE, null)
          .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS 999L)
          .compile()
          .execute()
      ).isEqualTo(0)
      observer.assertValuesOnly(
        listOf(first, second),
        expected
      )
      assertSimpleRows(*expected.toTypedArray())
    } finally {
      observer.dispose()
    }
  }

  @Test
  fun executeTransformedAssignment() {
    val row = ImmutableValueWithFields(
      id = 401L,
      stringValue = "immutable-401",
      aBoolean = false,
      integer = 401,
      aDouble = 401.0,
      aShort = 401.toShort(),
      transformableObject = TransformableObject(41)
    )
    insertedResult(
      result = row
        .insert()
        .execute(),
      modelName = "immutable-value-with-fields"
    )
    val replacement = TransformableObject(42)

    val affectedRows = UPDATE
      .TABLE(IMMUTABLE_VALUE_WITH_FIELDS)
      .SET(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT, replacement)
      .WHERE(IMMUTABLE_VALUE_WITH_FIELDS.ID IS checkNotNull(row.id))
      .compile()
      .execute()

    assertThat(affectedRows).isEqualTo(1)
    assertThat(SELECT.FROM(IMMUTABLE_VALUE_WITH_FIELDS).execute())
      .containsExactly(row.copy(transformableObject = replacement))
  }

  @Test
  fun executeRelationshipIdAssignment() {
    val firstRelated = SimpleMutableEntity(
      id = 501L,
      value = "first-related"
    )
    val secondRelated = SimpleMutableEntity(
      id = 502L,
      value = "second-related"
    )
    insertSimpleRow(value = firstRelated)
    insertSimpleRow(value = secondRelated)
    val owner = EntityWithRelationship().apply {
      id = 503L
      value = "owner"
      relatedEntity = firstRelated
      count = 1
    }
    insertedResult(
      result = owner
        .insert()
        .execute(),
      modelName = "entity-with-relationship"
    )

    val affectedRows = UPDATE
      .TABLE(ENTITY_WITH_RELATIONSHIP)
      .SET(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, checkNotNull(secondRelated.id))
      .WHERE(ENTITY_WITH_RELATIONSHIP.ID IS checkNotNull(owner.id))
      .compile()
      .execute()

    assertThat(affectedRows).isEqualTo(1)
    val expected = EntityWithRelationship().apply {
      id = owner.id
      value = owner.value
      relatedEntity = SimpleMutableEntity(id = secondRelated.id)
      count = owner.count
    }
    assertThat(SELECT.FROM(ENTITY_WITH_RELATIONSHIP).execute()).containsExactly(expected)
  }
}
