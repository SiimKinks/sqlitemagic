package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.not
import org.junit.jupiter.api.Test

internal class SelectComparisonSqlTest {
  @Test
  fun `simple expressions render SQL`() {
    assertEachSql(
      query = { SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { "$SIMPLE_MUTABLE_ENTITY_SELECT WHERE $it" },
      cases = listOf(
        (SIMPLE_MUTABLE_ENTITY.VALUE IS "asd") to """"simple_mutable_entity"."value" = ?""",
        (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd") to """"simple_mutable_entity"."value" != ?""",
        (SIMPLE_MUTABLE_ENTITY.VALUE IN arrayOf("asd")) to """"simple_mutable_entity"."value" IN (?)""",
        (SIMPLE_MUTABLE_ENTITY.VALUE NOT_IN arrayOf("asd")) to """"simple_mutable_entity"."value" NOT IN (?)"""
      )
    )
    assertEachSql(
      query = { SELECT FROM SIMPLE_MUTABLE_ENTITY INNER_JOIN ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { """$SIMPLE_MUTABLE_ENTITY_SELECT INNER JOIN "main"."entity_with_relationship" WHERE $it""" },
      cases = listOf(
        (SIMPLE_MUTABLE_ENTITY.VALUE IS ENTITY_WITH_RELATIONSHIP.VALUE) to
            """"simple_mutable_entity"."value" = "entity_with_relationship"."value"""",
        (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT ENTITY_WITH_RELATIONSHIP.VALUE) to
            """"simple_mutable_entity"."value" != "entity_with_relationship"."value""""
      )
    )
  }

  @Test
  fun `unary expressions render SQL`() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN.IS_NOT_NULL()))
      .assertSql(
        """$SIMPLE_MUTABLE_ENTITY_SELECT WHERE NOT ("simple_mutable_entity"."primitive_boolean" IS NOT NULL)"""
      )

    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !((SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN GREATER_THAN SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN) AND
        SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN.IS_NOT_NULL()))
      .assertSql(
        """$SIMPLE_MUTABLE_ENTITY_SELECT WHERE NOT (("simple_mutable_entity"."primitive_boolean" > """ +
            """"simple_mutable_entity"."boxed_boolean") AND ("simple_mutable_entity"."boxed_boolean" IS NOT NULL))"""
      )
  }

  @Test
  fun `numeric expressions with the same type render SQL`() {
    val select = selectAll("entity_with_relationship")
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { operator ->
        when (operator) {
          "IN", "NOT IN" -> """$select WHERE "entity_with_relationship"."count" $operator (?)"""
          else -> """$select WHERE "entity_with_relationship"."count" $operator ?"""
        }
      },
      cases = listOf(
        (ENTITY_WITH_RELATIONSHIP.COUNT IS 4) to "=",
        (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT 4) to "!=",
        (ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(4)) to "IN",
        (ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(4)) to "NOT IN",
        (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 4) to ">",
        (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL 4) to ">=",
        (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 4) to "<",
        (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL 4) to "<="
      )
    )
  }

  @Test
  fun `numeric expressions across joined tables render SQL`() {
    val count = ENTITY_WITH_RELATIONSHIP.COUNT
    val select = """${selectAll("entity_with_relationship")} INNER JOIN "main"."immutable_value_with_fields""""
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP INNER_JOIN IMMUTABLE_VALUE_WITH_FIELDS WHERE it },
      expected = { """$select WHERE "entity_with_relationship"."count" $it""" },
      cases = listOf(
        (count IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) to """= "immutable_value_with_fields"."integer"""",
        (count IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) to """!= "immutable_value_with_fields"."integer"""",
        (count GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) to """> "immutable_value_with_fields"."integer"""",
        (count LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) to """<= "immutable_value_with_fields"."integer"""",
        (count IS IMMUTABLE_VALUE_WITH_FIELDS.ID) to """= "immutable_value_with_fields"."id"""",
        (count IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.ID) to """!= "immutable_value_with_fields"."id"""",
        (count GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID) to """>= "immutable_value_with_fields"."id"""",
        (count LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID) to """< "immutable_value_with_fields"."id""""
      )
    )
  }

  @Test
  fun `relationship fields compare with referenced IDs`() {
    val related = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    val relatedId = 101L
    val select = selectAll("entity_with_relationship")
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { """$select WHERE "entity_with_relationship"."related_entity" $it""" },
      cases = listOf(
        (related IS relatedId) to "= ?",
        (related IS_NOT relatedId) to "!= ?",
        (related IN arrayOf(relatedId)) to "IN (?)",
        (related NOT_IN arrayOf(relatedId)) to "NOT IN (?)",
        (related GREATER_THAN relatedId) to "> ?",
        (related GREATER_OR_EQUAL relatedId) to ">= ?",
        (related LESS_THAN relatedId) to "< ?",
        (related LESS_OR_EQUAL relatedId) to "<= ?",
        (related IS related) to """= "entity_with_relationship"."related_entity"""",
        (related GREATER_THAN related) to """> "entity_with_relationship"."related_entity"""",
        (related IS ENTITY_WITH_RELATIONSHIP.ID) to """= "entity_with_relationship"."id"""",
        (related LESS_OR_EQUAL ENTITY_WITH_RELATIONSHIP.ID) to """<= "entity_with_relationship"."id""""
      )
    )
  }

  @Test
  fun `complex expressions render SQL when aliased`() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val select = """${selectAll("m")} AS "m""""
    val joined = """$select INNER JOIN "main"."entity_with_relationship""""

    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS 101L))
      .assertSql("""$select WHERE "m"."related_entity" = ?""")
    (SELECT
        FROM m
        INNER_JOIN ENTITY_WITH_RELATIONSHIP
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY))
      .assertSql("""$joined WHERE "m"."related_entity" = "entity_with_relationship"."related_entity"""")
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.RELATED_ENTITY))
      .assertSql("""$select WHERE "m"."related_entity" = "m"."related_entity"""")
    (SELECT
        FROM m
        INNER_JOIN ENTITY_WITH_RELATIONSHIP
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.ID))
      .assertSql("""$joined WHERE "m"."related_entity" = "entity_with_relationship"."id"""")
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.ID))
      .assertSql("""$select WHERE "m"."related_entity" = "m"."id"""")
  }
}
