package com.siimkinks.sqlitemagic.dsl

import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.asScalar
import com.siimkinks.sqlitemagic.plus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UpdateTest {
  @Test
  fun `SET values render SQL`() {
    assertThat(
      mapOf(
        "value" to (UPDATE TABLE ENTITY_WITH_RELATIONSHIP SET (ENTITY_WITH_RELATIONSHIP.COUNT to 1)),
        "nullable value" to (UPDATE TABLE SIMPLE_MUTABLE_ENTITY SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")),
        "null value" to (UPDATE TABLE SIMPLE_MUTABLE_ENTITY SET (SIMPLE_MUTABLE_ENTITY.VALUE to null)),
        "primitive boolean" to
            (UPDATE TABLE SIMPLE_MUTABLE_ENTITY SET (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN to true)),
        "boxed boolean" to (UPDATE TABLE SIMPLE_MUTABLE_ENTITY SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to true))
      ).mapValues { (_, query) -> query.compile().sql }
    ).isEqualTo(
      mapOf(
        "value" to """UPDATE "main"."entity_with_relationship" SET "count" = ?""",
        "nullable value" to """UPDATE "main"."simple_mutable_entity" SET "value" = ?""",
        "null value" to """UPDATE "main"."simple_mutable_entity" SET "value" = ?""",
        "primitive boolean" to """UPDATE "main"."simple_mutable_entity" SET "primitive_boolean" = ?""",
        "boxed boolean" to """UPDATE "main"."simple_mutable_entity" SET "boxed_boolean" = ?"""
      )
    )
  }

  @Test
  fun `SET SQL NULL renders SQL`() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET_NULL SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
      .assertSql("""UPDATE "main"."simple_mutable_entity" SET "primitive_boolean" = NULL""")
  }

  @Test
  fun `UPDATE OR IGNORE renders SQL`() {
    (UPDATE
        OR CONFLICT_IGNORE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd"))
      .assertSql("""UPDATE OR IGNORE "main"."simple_mutable_entity" SET "value" = ?""")
  }

  @Test
  fun `UPDATE OR FAIL renders SQL`() {
    (UPDATE
        OR CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd"))
      .assertSql("""UPDATE OR FAIL "main"."simple_mutable_entity" SET "value" = ?""")
  }

  @Test
  fun `chained SET values render SQL`() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to 1))
      .assertSql("""UPDATE "main"."immutable_value_with_fields" SET "a_boolean" = ?, "integer" = ?""")
  }

  @Test
  fun `chained SET nullable values render SQL`() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to null)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING to "foo"))
      .assertSql(
        """UPDATE "main"."immutable_value_with_nullable_fields" SET "a_boolean" = ?, "integer" = ?, """ +
            """"string" = ?"""
      )
  }

  @Test
  fun `chained SET values with conflict algorithm render SQL`() {
    (UPDATE
        OR CONFLICT_ROLLBACK
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to true)
        SET (SIMPLE_MUTABLE_ENTITY.ID to 2L)
        SET (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN to false))
      .assertSql(
        """UPDATE OR ROLLBACK "main"."simple_mutable_entity" SET "value" = ?, "boxed_boolean" = ?, """ +
            """"id" = ?, "primitive_boolean" = ?"""
      )
  }

  @Test
  fun `UPDATE WHERE builder renders SQL`() {
    (UPDATE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE (SIMPLE_MUTABLE_ENTITY.ID IS 2))
      .assertSql(
        """UPDATE "main"."simple_mutable_entity" SET "value" = ? WHERE "simple_mutable_entity"."id" = ?"""
      )
    (UPDATE
        OR CONFLICT_IGNORE
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        SET (SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN to false)
        WHERE ((SIMPLE_MUTABLE_ENTITY.ID IS 2) AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")))
      .assertSql(
        """UPDATE OR IGNORE "main"."simple_mutable_entity" SET "value" = ?, "boxed_boolean" = ? WHERE """ +
            """("simple_mutable_entity"."id" = ?) AND ("simple_mutable_entity"."value" != ?)"""
      )
    (UPDATE
        OR CONFLICT_FAIL
        TABLE SIMPLE_MUTABLE_ENTITY
        SET (SIMPLE_MUTABLE_ENTITY.VALUE to "asd")
        WHERE (
        ((((SIMPLE_MUTABLE_ENTITY.ID IS 2)
            AND SIMPLE_MUTABLE_ENTITY.VALUE.IS_NOT_NULL())
            OR (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd"))
            OR ((SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS false)
            AND SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN.IS_NOT_NULL()))
        ))
      .assertSql(
        """UPDATE OR FAIL "main"."simple_mutable_entity" SET "value" = ? WHERE """ +
            """((("simple_mutable_entity"."id" = ?) AND ("simple_mutable_entity"."value" IS NOT NULL)) OR """ +
            """("simple_mutable_entity"."value" != ?)) OR (("simple_mutable_entity"."primitive_boolean" = ?) """ +
            """AND ("simple_mutable_entity"."boxed_boolean" IS NOT NULL))"""
      )
  }

  @Test
  fun `duplicate assignments are kept`() {
    (UPDATE
        TABLE COMPLEX_OBJECT_WITH_SAME_LEAFS
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP to 42L)
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP to 42L))
      .assertSql(
        """UPDATE "main"."complex_object_with_same_leafs" SET "entity_with_relationship" = ?, """ +
            """"entity_with_relationship" = ?"""
      )
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to null)
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to null))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "related_entity" = ?, "related_entity" = ?"""
      )
  }

  @Test
  fun `SET column renders SQL`() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 6))
      .assertSql(
        """UPDATE "main"."immutable_value_with_fields" SET "integer" = """ +
            """"immutable_value_with_fields"."integer" + ?"""
      )
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "related_entity" = """ +
            """"entity_with_relationship"."related_entity""""
      )
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to ENTITY_WITH_RELATIONSHIP.ID))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "related_entity" = "entity_with_relationship"."id""""
      )
  }

  @Test
  fun `UPDATE with SELECT renders SQL`() {
    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.COUNT to
        (SELECT.COLUMN(ENTITY_WITH_RELATIONSHIP.COUNT) FROM ENTITY_WITH_RELATIONSHIP).asScalar()))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "count" = (SELECT """ +
            """"entity_with_relationship"."count" FROM "main"."entity_with_relationship")"""
      )

    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY to
        (SELECT COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY FROM ENTITY_WITH_RELATIONSHIP).asScalar()))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "related_entity" = (SELECT """ +
            """"entity_with_relationship"."related_entity" FROM "main"."entity_with_relationship")"""
      )

    (UPDATE
        TABLE ENTITY_WITH_RELATIONSHIP
        SET (ENTITY_WITH_RELATIONSHIP.COUNT to
        (SELECT.COLUMN(ENTITY_WITH_RELATIONSHIP.COUNT) FROM COMPLEX_OBJECT_WITH_SAME_LEAFS).asScalar()))
      .assertSql(
        """UPDATE "main"."entity_with_relationship" SET "count" = (SELECT """ +
            """"entity_with_relationship"."count" FROM "main"."complex_object_with_same_leafs")"""
      )
  }

  @Test
  fun `values of another table reject before SQL`() {
    listOf(
      UPDATE TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS SET
          (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to ENTITY_WITH_RELATIONSHIP.COUNT + 6),
      UPDATE TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS SET
          (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
    ).forEach { query ->
      assertThat(assertThrows<IllegalArgumentException> { query.compile() })
        .hasMessageThat()
        .isEqualTo("A field requires the exact table occurrence of its FROM, UPDATE or DELETE")
    }
  }

  @Test
  fun `raw statement keeps its SQL`() {
    val sql = "UPDATE OR ROLLBACK simple_mutable_entity SET value=?,boxed_boolean=?,id=?,primitive_boolean=?"

    assertThat(
      UPDATE
        .RAW(sql, "asd", true, 2L, false)
        .compile()
        .sql
    ).isEqualTo(sql)
  }
}
