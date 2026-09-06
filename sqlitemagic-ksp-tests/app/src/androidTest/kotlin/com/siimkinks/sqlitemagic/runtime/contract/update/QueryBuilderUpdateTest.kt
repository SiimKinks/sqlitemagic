package com.siimkinks.sqlitemagic.runtime.contract.update

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledUpdate
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.Update
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal
import com.siimkinks.sqlitemagic.runtime.support.OperationTerminal.*
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

class QueryBuilderUpdateTest : RuntimeDatabaseTest() {
  @Test
  fun executeRawAssignmentWithPredicate() {
    assertRawAssignmentWithPredicate(terminal = EXECUTE)
  }

  @Test
  fun observeRawAssignmentWithPredicate() {
    assertRawAssignmentWithPredicate(terminal = OBSERVE)
  }

  @Test
  fun executeTypedWholeTableAssignments() {
    assertTypedWholeTableAssignments(terminal = EXECUTE)
  }

  @Test
  fun observeTypedWholeTableAssignments() {
    assertTypedWholeTableAssignments(terminal = OBSERVE)
  }

  @Test
  fun executeExplicitNullPredicatePublishesOnlyForChangedRows() {
    assertExplicitNullPredicatePublishesOnlyForChangedRows(terminal = EXECUTE)
  }

  @Test
  fun observeExplicitNullPredicatePublishesOnlyForChangedRows() {
    assertExplicitNullPredicatePublishesOnlyForChangedRows(terminal = OBSERVE)
  }

  @Test
  fun executeTransformedAssignment() {
    assertTransformedAssignment(terminal = EXECUTE)
  }

  @Test
  fun observeTransformedAssignment() {
    assertTransformedAssignment(terminal = OBSERVE)
  }

  @Test
  fun executeRelationshipIdAssignment() {
    assertRelationshipIdAssignment(terminal = EXECUTE)
  }

  @Test
  fun observeRelationshipIdAssignment() {
    assertRelationshipIdAssignment(terminal = OBSERVE)
  }

  @Test
  fun executeAliasedTableAssignment() {
    assertAliasedTableAssignment(terminal = EXECUTE)
  }

  @Test
  fun observeAliasedTableAssignment() {
    assertAliasedTableAssignment(terminal = OBSERVE)
  }

  private fun assertRawAssignmentWithPredicate(terminal: OperationTerminal) {
    val first = simpleRow(
      id = 101L,
      value = "first"
    )
    val second = simpleRow(
      id = 102L,
      value = "second"
    )
    insert(first)
    insert(second)

    val affectedRows = execute(
      statement = Update
        .table("simple_mutable_entity")
        .set("value", "raw-updated")
        .where("simple_mutable_entity.id=?", checkNotNull(second.id).toString())
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    assertRows(
      first,
      second.copy(value = "raw-updated")
    )
  }

  private fun assertTypedWholeTableAssignments(terminal: OperationTerminal) {
    val first = simpleRow(
      id = 201L,
      value = "first"
    )
    val second = simpleRow(
      id = 202L,
      value = null
    )
    insert(first)
    insert(second)

    val affectedRows = execute(
      statement = Update
        .table(SIMPLE_MUTABLE_ENTITY)
        .set(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN, true)
        .setNullable(SIMPLE_MUTABLE_ENTITY.VALUE, "typed-updated")
        .setNullable(SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN, false)
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(2)
    assertRows(
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

  private fun assertExplicitNullPredicatePublishesOnlyForChangedRows(terminal: OperationTerminal) {
    val first = simpleRow(
      id = 301L,
      value = "first"
    )
    val second = simpleRow(
      id = 302L,
      value = "second"
    )
    insert(first)
    insert(second)
    val expected = listOf(first.copy(value = null), second)
    val observer = Select
      .from(SIMPLE_MUTABLE_ENTITY)
      .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
      .observe()
      .runQuery()
      .test()
      .assertValuesOnly(listOf(first, second))
    try {
      val affectedRows = execute(
        statement = Update
          .table(SIMPLE_MUTABLE_ENTITY)
          .setNullable(SIMPLE_MUTABLE_ENTITY.VALUE, null)
          .where(SIMPLE_MUTABLE_ENTITY.ID.`is`(checkNotNull(first.id)))
          .compile(),
        terminal = terminal
      )

      assertThat(affectedRows).isEqualTo(1)
      observer.assertValuesOnly(
        listOf(first, second),
        expected
      )
      assertThat(
        execute(
          statement = Update
            .table(SIMPLE_MUTABLE_ENTITY)
            .setNullable(SIMPLE_MUTABLE_ENTITY.VALUE, null)
            .where(SIMPLE_MUTABLE_ENTITY.ID.`is`(999L))
            .compile(),
          terminal = terminal
        )
      ).isEqualTo(0)
      observer.assertValuesOnly(
        listOf(first, second),
        expected
      )
      assertRows(*expected.toTypedArray())
    } finally {
      observer.dispose()
    }
  }

  private fun assertTransformedAssignment(terminal: OperationTerminal) {
    val row = immutableRow(
      id = 401L,
      transformableObject = TransformableObject(41)
    )
    insert(row)
    val replacement = TransformableObject(42)

    val affectedRows = execute(
      statement = Update
        .table(IMMUTABLE_VALUE_WITH_FIELDS)
        .set(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT, replacement)
        .where(IMMUTABLE_VALUE_WITH_FIELDS.ID.`is`(checkNotNull(row.id)))
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    assertThat(
      Select
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .execute()
    ).containsExactly(row.copy(transformableObject = replacement))
  }

  private fun assertRelationshipIdAssignment(terminal: OperationTerminal) {
    val firstRelated = simpleRow(
      id = 501L,
      value = "first-related"
    )
    val secondRelated = simpleRow(
      id = 502L,
      value = "second-related"
    )
    insert(firstRelated)
    insert(secondRelated)
    val owner = EntityWithRelationship().apply {
      id = 503L
      value = "owner"
      relatedEntity = firstRelated
      count = 1
    }
    insert(owner)

    val affectedRows = execute(
      statement = Update
        .table(ENTITY_WITH_RELATIONSHIP)
        .setNullable(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, checkNotNull(secondRelated.id))
        .where(ENTITY_WITH_RELATIONSHIP.ID.`is`(checkNotNull(owner.id)))
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    val expected = EntityWithRelationship().apply {
      id = owner.id
      value = owner.value
      relatedEntity = SimpleMutableEntity(id = secondRelated.id)
      count = owner.count
    }
    assertThat(
      Select
        .from(ENTITY_WITH_RELATIONSHIP)
        .execute()
    ).containsExactly(expected)
  }

  private fun assertAliasedTableAssignment(terminal: OperationTerminal) {
    val row = simpleRow(
      id = 601L,
      value = "before-alias"
    )
    insert(row)
    val alias = SIMPLE_MUTABLE_ENTITY.`as`("target")

    val affectedRows = execute(
      statement = Update
        .table(alias)
        .setNullable(alias.VALUE, "after-alias")
        .compile(),
      terminal = terminal
    )

    assertThat(affectedRows).isEqualTo(1)
    assertRows(row.copy(value = "after-alias"))
  }

  private fun execute(
    statement: CompiledUpdate,
    terminal: OperationTerminal
  ) = when (terminal) {
    EXECUTE -> statement.execute()
    OBSERVE -> statement.observe().blockingGet()
  }

  private fun assertRows(vararg expected: SimpleMutableEntity) {
    assertThat(
      Select
        .from(SIMPLE_MUTABLE_ENTITY)
        .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
        .execute()
    ).isEqualTo(expected.toList())
  }

  private fun simpleRow(
    id: Long,
    value: String?
  ) = SimpleMutableEntity(
    id = id,
    value = value,
    boxedBoolean = null,
    primitiveBoolean = false
  )

  private fun immutableRow(
    id: Long,
    transformableObject: TransformableObject
  ) = ImmutableValueWithFields(
    id = id,
    stringValue = "immutable-$id",
    aBoolean = false,
    integer = id.toInt(),
    aDouble = id.toDouble(),
    aShort = id.toShort(),
    transformableObject = transformableObject
  )

  private fun insert(value: SimpleMutableEntity) {
    assertInserted(result = value.insert().execute())
  }

  private fun insert(value: ImmutableValueWithFields) {
    assertInserted(result = value.insert().execute())
  }

  private fun insert(value: EntityWithRelationship) {
    assertInserted(result = value.insert().execute())
  }

  private fun assertInserted(result: EntityInsertResult) {
    assertThat(result).isInstanceOf(EntityInsertResult.Inserted::class.java)
  }
}
