package com.siimkinks.sqlitemagic.dsl

import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK
import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.TABLE
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.WITH_CONFLICT_ALGORITHM
import com.siimkinks.sqlitemagic.isEqualTo
import com.siimkinks.sqlitemagic.plus
import org.junit.jupiter.api.Test

class UpdateTest : DSLTests {
  @Test
  fun setRawValue() {
    (UPDATE
        TABLE "book"
        SET ("count" to "1"))
      .isEqualTo(
        sql = "UPDATE book SET count=? ",
        nodeCount = 3,
        args = arrayOf("1")
      )
  }

  @Test
  fun setValue() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.COUNT to 1))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET count=? ",
        nodeCount = 3,
        args = arrayOf("1")
      )
  }

  @Test
  fun setNullableValue() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd"))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET value=? ",
        nodeCount = 3,
        args = arrayOf("asd")
      )
  }

  @Test
  fun setNullValue() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to null))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET value=? ",
        nodeCount = 3,
        args = arrayOf(null)
      )
  }

  @Test
  fun setPrimitiveBoolean() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN to true))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET primitive_boolean=? ",
        nodeCount = 3,
        args = arrayOf("1")
      )
  }

  @Test
  fun setBoxedBoolean() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to true))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET boxed_boolean=? ",
        nodeCount = 3,
        args = arrayOf("1")
      )
  }

  @Test
  fun rawWithDefaultConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_IGNORE
        TABLE "simple_mutable_entity"
        SET ("value" to "asd"))
      .isEqualTo(
        sql = "UPDATE  OR IGNORE simple_mutable_entity SET value=? ",
        nodeCount = 4,
        args = arrayOf("asd")
      )
  }

  @Test
  fun withDefaultConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_IGNORE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd"))
      .isEqualTo(
        sql = "UPDATE  OR IGNORE simple_mutable_entity SET value=? ",
        nodeCount = 4,
        args = arrayOf("asd")
      )
  }

  @Test
  fun withCustomConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd"))
      .isEqualTo(
        sql = "UPDATE  OR FAIL simple_mutable_entity SET value=? ",
        nodeCount = 4,
        args = arrayOf("asd")
      )
  }

  @Test
  fun setRawChainedValue() {
    (UPDATE
        TABLE "immutable_value_with_fields"
        SET ("a_boolean" to "1")
        SET ("integer" to "1"))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_fields SET a_boolean=?,integer=? ",
        nodeCount = 3,
        args = arrayOf("1", "1")
      )
  }

  @Test
  fun setChainedValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to 1))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_fields SET a_boolean=?,integer=? ",
        nodeCount = 3,
        args = arrayOf("1", "1")
      )
  }

  @Test
  fun setChainedNullableValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to 1))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=? ",
        nodeCount = 3,
        args = arrayOf("1", "1")
      )
  }

  @Test
  fun setChainedNullableValues() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to 1)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING to "asd"))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=?,string=? ",
        nodeCount = 3,
        args = arrayOf("1", "1", "asd")
      )
  }

  @Test
  fun setChainedNullValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to null))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=? ",
        nodeCount = 3,
        args = arrayOf("1", null)
      )
  }

  @Test
  fun setChainedMiddleNullValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to null)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING to "foo"))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=?,string=? ",
        nodeCount = 3,
        args = arrayOf("1", null, "foo")
      )
  }

  @Test
  fun setRawChainedValuesWithConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_ROLLBACK
        TABLE "simple_mutable_entity"
        SET ("value" to "asd")
        SET ("boxed_boolean" to "1")
        SET ("id" to "2")
        SET ("primitive_boolean" to "0"))
      .isEqualTo(
        sql = "UPDATE  OR ROLLBACK simple_mutable_entity SET value=?,boxed_boolean=?,id=?,primitive_boolean=? ",
        nodeCount = 4,
        args = arrayOf("asd", "1", "2", "0")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_ROLLBACK
        TABLE "simple_mutable_entity"
        SET ("value" to "asd")
        SET ("boxed_boolean" to "1"))
      .isEqualTo(
        sql = "UPDATE  OR ROLLBACK simple_mutable_entity SET value=?,boxed_boolean=? ",
        nodeCount = 4,
        args = arrayOf("asd", "1")
      )
  }

  @Test
  fun setChainedValuesWithConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_ROLLBACK
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to true)
        SET (SIMPLE_MUTABLE_ENTITY.ID to 2L)
        SET (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN to false))
      .isEqualTo(
        sql = "UPDATE  OR ROLLBACK simple_mutable_entity SET value=?,boxed_boolean=?,id=?,primitive_boolean=? ",
        nodeCount = 4,
        args = arrayOf("asd", "1", "2", "0")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_ROLLBACK
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to true))
      .isEqualTo(
        sql = "UPDATE  OR ROLLBACK simple_mutable_entity SET value=?,boxed_boolean=? ",
        nodeCount = 4,
        args = arrayOf("asd", "1")
      )
  }

  @Test
  fun rawUpdateWithWhereClause() {
    (UPDATE
        TABLE "simple_mutable_entity"
        SET ("value" to "asd")
        WHERE ("simple_mutable_entity.id=?" to arrayOf("2")))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET value=? WHERE simple_mutable_entity.id=? ",
        nodeCount = 4,
        args = arrayOf("asd", "2")
      )
  }

  @Test
  fun updateWhereBuilder() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE (SIMPLE_MUTABLE_ENTITY.ID IS 2))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET value=? WHERE simple_mutable_entity.id=? ",
        nodeCount = 4,
        args = arrayOf("asd", "2")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_IGNORE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to false)
        WHERE ((SIMPLE_MUTABLE_ENTITY.ID IS 2) AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")))
      .isEqualTo(
        sql = "UPDATE  OR IGNORE simple_mutable_entity SET value=?,boxed_boolean=? WHERE " +
            "(simple_mutable_entity.id=? AND simple_mutable_entity.value!=?) ",
        nodeCount = 5,
        args = arrayOf("asd", "0", "2", "asd")
      )

    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to false)
        WHERE ((SIMPLE_MUTABLE_ENTITY.ID IS 2) OR (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")))
      .isEqualTo(
        sql = "UPDATE simple_mutable_entity SET value=?,boxed_boolean=? WHERE " +
            "(simple_mutable_entity.id=? OR simple_mutable_entity.value!=?) ",
        nodeCount = 4,
        args = arrayOf("asd", "0", "2", "asd")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE ((SIMPLE_MUTABLE_ENTITY.ID IS 2) OR (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")))
      .isEqualTo(
        sql = "UPDATE  OR FAIL simple_mutable_entity SET value=? WHERE " +
            "(simple_mutable_entity.id=? OR simple_mutable_entity.value!=?) ",
        nodeCount = 5,
        args = arrayOf("asd", "2", "asd")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE (
        (((SIMPLE_MUTABLE_ENTITY.ID IS 2)
            AND SIMPLE_MUTABLE_ENTITY.VALUE.isNotNull)
            AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd"))
            AND (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS false)
        ))
      .isEqualTo(
        sql = "UPDATE  OR FAIL simple_mutable_entity SET value=? WHERE " +
            "(((simple_mutable_entity.id=? AND simple_mutable_entity.value IS NOT NULL) " +
            "AND simple_mutable_entity.value!=?) AND simple_mutable_entity.primitive_boolean=?) ",
        nodeCount = 5,
        args = arrayOf("asd", "2", "asd", "0")
      )

    (UPDATE
        WITH_CONFLICT_ALGORITHM CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE (
        ((((SIMPLE_MUTABLE_ENTITY.ID IS 2)
            AND SIMPLE_MUTABLE_ENTITY.VALUE.isNotNull)
            OR (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd"))
            OR ((SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS false) AND SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN.isNotNull))
        ))
      .isEqualTo(
        sql = "UPDATE  OR FAIL simple_mutable_entity SET value=? WHERE " +
            "(((simple_mutable_entity.id=? AND simple_mutable_entity.value IS NOT NULL) " +
            "OR simple_mutable_entity.value!=?) OR " +
            "(simple_mutable_entity.primitive_boolean=? AND simple_mutable_entity.boxed_boolean IS NOT NULL)) ",
        nodeCount = 5,
        args = arrayOf("asd", "2", "asd", "0")
      )
  }

  @Test
  fun updateComplexColumn() {
    val id = 42L
    val idStr = id.toString()
    (UPDATE
        TABLE COMPLEX_OBJECT_WITH_SAME_LEAFS
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP to id)
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP to id))
      .isEqualTo(
        sql = "UPDATE complex_object_with_same_leafs SET entity_with_relationship=?,entity_with_relationship=? ",
        nodeCount = 3,
        args = arrayOf(idStr, idStr)
      )
  }

  @Test
  fun updateNullableComplexColumn() {
    val id = 42L
    val idStr = id.toString()

    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to id)
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to id))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET related_entity=?,related_entity=? ",
        nodeCount = 3,
        args = arrayOf(idStr, idStr)
      )
  }

  @Test
  fun setNullComplexColumn() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to null)
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to null))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET related_entity=?,related_entity=? ",
        nodeCount = 3,
        args = arrayOf(null, null)
      )
  }

  @Test
  fun setColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 6))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_fields SET integer=(immutable_value_with_fields.integer+6) ",
        nodeCount = 3
      )
  }

  @Test
  fun setNotNullableColumnWithNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to ENTITY_WITH_RELATIONSHIP.ID.toNotNullable()))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_fields SET integer=entity_with_relationship.id ",
        nodeCount = 3
      )

    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER + 6).toNotNullable()))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_fields SET integer=(immutable_value_with_nullable_fields.integer+6) ",
        nodeCount = 3
      )
  }

  @Test
  fun setNullableColumnWithNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to ENTITY_WITH_RELATIONSHIP.COUNT + 6))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET integer=(entity_with_relationship.count+6) ",
        nodeCount = 3
      )
  }

  @Test
  fun setNullableColumnWithNotNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET integer=immutable_value_with_fields.integer ",
        nodeCount = 3
      )

    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 6))
      .isEqualTo(
        sql = "UPDATE immutable_value_with_nullable_fields SET integer=(immutable_value_with_fields.integer+6) ",
        nodeCount = 3
      )
  }

  @Test
  fun setComplexColumnToComplexColumn() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET related_entity=entity_with_relationship.related_entity ",
        nodeCount = 3
      )
  }

  @Test
  fun setColumnToComplexColumnId() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to ENTITY_WITH_RELATIONSHIP.ID))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET related_entity=entity_with_relationship.id ",
        nodeCount = 3
      )
  }

  @Test
  fun updateWithSelect() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.COUNT to (
          SELECT COLUMN ENTITY_WITH_RELATIONSHIP.COUNT FROM ENTITY_WITH_RELATIONSHIP
        )))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET count=(SELECT " +
            "entity_with_relationship.count FROM entity_with_relationship ) ",
        nodeCount = 3
      )

    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to (
          SELECT COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY FROM ENTITY_WITH_RELATIONSHIP
        )))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET related_entity=(SELECT " +
            "entity_with_relationship.related_entity FROM entity_with_relationship ) ",
        nodeCount = 3
      )

    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.COUNT to (
          SELECT COLUMN ENTITY_WITH_RELATIONSHIP.COUNT FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        )))
      .isEqualTo(
        sql = "UPDATE entity_with_relationship SET count=(SELECT entity_with_relationship.count FROM " +
            "complex_object_with_same_leafs LEFT JOIN entity_with_relationship ON " +
            "complex_object_with_same_leafs.entity_with_relationship=entity_with_relationship.id ) ",
        nodeCount = 3
      )
  }
}
