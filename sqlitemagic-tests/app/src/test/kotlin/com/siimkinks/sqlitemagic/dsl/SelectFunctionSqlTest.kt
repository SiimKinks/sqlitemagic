package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AVG
import com.siimkinks.sqlitemagic.CONCAT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.REPLACE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.asValue
import com.siimkinks.sqlitemagic.div
import com.siimkinks.sqlitemagic.minus
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.rem
import com.siimkinks.sqlitemagic.times
import com.siimkinks.sqlitemagic.unaryMinus
import org.junit.jupiter.api.Test

internal class SelectFunctionSqlTest {
  @Test
  fun `unary minus renders SQL`() {
    val count = ENTITY_WITH_RELATIONSHIP.COUNT
    assertEachSql(
      query = { SELECT COLUMN it FROM ENTITY_WITH_RELATIONSHIP },
      expected = { """SELECT -("entity_with_relationship"."count" - ?) FROM "main"."entity_with_relationship"""" },
      cases = listOf(
        -(count - 42) to "host value",
        -(count - 42.asValue()) to "value"
      )
    )
  }

  @Test
  fun `AVG function renders SQL`() {
    (SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.ID)
        FROM IMMUTABLE_VALUE_WITH_FIELDS
        WHERE ((AVG(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) GREATER_THAN 5555.0)
        AND (AVG(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE) LESS_THAN 8888.8)))
      .assertSql(
        """SELECT "immutable_value_with_fields"."id" FROM "main"."immutable_value_with_fields" WHERE """ +
            """(AVG("immutable_value_with_fields"."integer") > ?) AND """ +
            """(AVG("immutable_value_with_fields"."a_double") < ?)"""
      )
  }

  @Test
  fun `CONCAT function renders SQL`() {
    val id = SIMPLE_MUTABLE_ENTITY.ID
    val value = SIMPLE_MUTABLE_ENTITY.VALUE
    val primitiveBoolean = SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN

    (SELECT
        COLUMN (id CONCAT value)
        FROM SIMPLE_MUTABLE_ENTITY)
      .assertSql(
        """SELECT "simple_mutable_entity"."id" || "simple_mutable_entity"."value" """ +
            """FROM "main"."simple_mutable_entity""""
      )

    assertEachSql(
      query = { SELECT COLUMN it FROM SIMPLE_MUTABLE_ENTITY },
      expected = {
        """SELECT ("simple_mutable_entity"."id" || "simple_mutable_entity"."value") || """ +
            """"simple_mutable_entity"."primitive_boolean" FROM "main"."simple_mutable_entity""""
      },
      cases = listOf(
        (id CONCAT value CONCAT primitiveBoolean) to "chained",
        ((id CONCAT value) CONCAT primitiveBoolean) to "grouped"
      )
    )
  }

  @Test
  fun `REPLACE function renders SQL`() {
    val replaced = REPLACE(SIMPLE_MUTABLE_ENTITY.VALUE, "a", "____")

    (SELECT
        COLUMN replaced
        FROM SIMPLE_MUTABLE_ENTITY)
      .assertSql("""SELECT REPLACE("simple_mutable_entity"."value", ?, ?) FROM "main"."simple_mutable_entity"""")

    (SELECT
        COLUMN (replaced AS "asd")
        FROM SIMPLE_MUTABLE_ENTITY)
      .assertSql(
        """SELECT REPLACE("simple_mutable_entity"."value", ?, ?) AS "asd" FROM "main"."simple_mutable_entity""""
      )
  }

  @Test
  fun `value column renders SQL`() {
    (SELECT
        COLUMN (SIMPLE_MUTABLE_ENTITY.ID CONCAT " ".asValue() CONCAT SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .assertSql(
        """SELECT ("simple_mutable_entity"."id" || ?) || "simple_mutable_entity"."value" """ +
            """FROM "main"."simple_mutable_entity""""
      )
  }

  @Test
  fun `arithmetic operators render SQL`() {
    val integer = IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
    val aShort = IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT
    val value = 5L.asValue()
    val query = { expression: SqlExpression<*, *, *> -> SELECT COLUMN expression FROM IMMUTABLE_VALUE_WITH_FIELDS }
    val select = """SELECT "immutable_value_with_fields"."integer""""
    val from = """FROM "main"."immutable_value_with_fields""""

    assertEachSql(
      query = query,
      expected = { """$select $it "immutable_value_with_fields"."a_short" $from""" },
      cases = listOf(
        (integer + aShort) to "+",
        (integer - aShort) to "-",
        (integer * aShort) to "*",
        (integer / aShort) to "/",
        (integer % aShort) to "%"
      )
    )
    assertEachSql(
      query = query,
      expected = { "$select $it ? $from" },
      cases = listOf(
        (integer + value) to "+",
        (integer - value) to "-",
        (integer * value) to "*",
        (integer / value) to "/",
        (integer % value) to "%"
      )
    )
    assertEachSql(
      query = query,
      expected = { "$select $it ? $from" },
      cases = listOf(
        (integer + 5) to "+",
        (integer - 5) to "-",
        (integer * 5) to "*",
        (integer / 5) to "/",
        (integer % 5) to "%"
      )
    )
  }

  @Test
  fun `chained numeric arithmetic expressions render SQL`() {
    (SELECT
        COLUMN ((((1.asValue() + 2) * (5.asValue() - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .assertSql("""SELECT (((? + ?) * (? - ?)) / ?) % ? FROM "main"."immutable_value_with_fields"""")

    (SELECT
        COLUMN ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * (5.asValue() - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .assertSql(
        """SELECT ((("immutable_value_with_fields"."integer" + ?) * (? - ?)) / ?) % ? """ +
            """FROM "main"."immutable_value_with_fields""""
      )
  }
}
