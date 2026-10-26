package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.IN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.NOT_IN
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlExpression
import com.siimkinks.sqlitemagic.asScalar
import org.junit.jupiter.api.Test

internal class SelectSubquerySqlTest {
  @Test
  fun `simple subquery renders SQL`() {
    val value = SIMPLE_MUTABLE_ENTITY.VALUE
    val subquery = (SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE) FROM IMMUTABLE_VALUE_WITH_FIELDS)
    val subquerySql = """SELECT "immutable_value_with_fields"."string_value" """ +
        """FROM "main"."immutable_value_with_fields""""
    assertEachSql(
      query = { SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { operator ->
        """$SIMPLE_MUTABLE_ENTITY_SELECT WHERE "simple_mutable_entity"."value" $operator ($subquerySql)"""
      },
      cases = listOf(
        (value IS subquery.asScalar()) to "=",
        (value IS_NOT subquery.asScalar()) to "!=",
        (value IN subquery) to "IN",
        (value NOT_IN subquery) to "NOT IN"
      )
    )
  }

  @Test
  fun `numeric subquery renders SQL`() {
    val count = ENTITY_WITH_RELATIONSHIP.COUNT
    val where = """${selectAll("entity_with_relationship")} WHERE "entity_with_relationship"."count""""
    val integers = (SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) FROM IMMUTABLE_VALUE_WITH_FIELDS)
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { operator ->
        """$where $operator (SELECT "immutable_value_with_fields"."integer" """ +
            """FROM "main"."immutable_value_with_fields")"""
      },
      cases = listOf(
        (count IS integers.asScalar()) to "=",
        (count IS_NOT integers.asScalar()) to "!=",
        (count IN integers) to "IN",
        (count NOT_IN integers) to "NOT IN",
        (count GREATER_THAN integers.asScalar()) to ">",
        (count GREATER_OR_EQUAL integers.asScalar()) to ">=",
        (count LESS_THAN integers.asScalar()) to "<",
        (count LESS_OR_EQUAL integers.asScalar()) to "<="
      )
    )

    val doubles = (SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE) FROM IMMUTABLE_VALUE_WITH_FIELDS)
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { operator ->
        """$where $operator (SELECT "immutable_value_with_fields"."a_double" """ +
            """FROM "main"."immutable_value_with_fields")"""
      },
      cases = listOf(
        (count GREATER_THAN doubles.asScalar()) to ">",
        (count GREATER_OR_EQUAL doubles.asScalar()) to ">=",
        (count LESS_THAN doubles.asScalar()) to "<",
        (count LESS_OR_EQUAL doubles.asScalar()) to "<="
      )
    )
  }

  @Test
  fun `complex subquery renders SQL`() {
    val related = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    val query = { predicate: SqlExpression<Boolean, *, *> -> SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE predicate }
    val where = """${selectAll("entity_with_relationship")} WHERE "entity_with_relationship"."related_entity""""

    val relatedEntities = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
    assertEachSql(
      query = query,
      expected = { operator ->
        """$where $operator (SELECT "entity_with_relationship"."related_entity" """ +
            """FROM "main"."entity_with_relationship")"""
      },
      cases = listOf(
        (related IS relatedEntities.asScalar()) to "=",
        (related IS_NOT relatedEntities.asScalar()) to "!=",
        (related IN relatedEntities) to "IN",
        (related NOT_IN relatedEntities) to "NOT IN",
        (related GREATER_THAN relatedEntities.asScalar()) to ">",
        (related GREATER_OR_EQUAL relatedEntities.asScalar()) to ">=",
        (related LESS_THAN relatedEntities.asScalar()) to "<",
        (related LESS_OR_EQUAL relatedEntities.asScalar()) to "<="
      )
    )

    val ids = (SELECT.COLUMN(ENTITY_WITH_RELATIONSHIP.ID) FROM ENTITY_WITH_RELATIONSHIP)
    assertEachSql(
      query = query,
      expected = { operator ->
        """$where $operator (SELECT "entity_with_relationship"."id" FROM "main"."entity_with_relationship")"""
      },
      cases = listOf(
        (related IS ids.asScalar()) to "=",
        (related IS_NOT ids.asScalar()) to "!=",
        (related IN ids) to "IN",
        (related NOT_IN ids) to "NOT IN",
        (related GREATER_THAN ids.asScalar()) to ">",
        (related GREATER_OR_EQUAL ids.asScalar()) to ">=",
        (related LESS_THAN ids.asScalar()) to "<",
        (related LESS_OR_EQUAL ids.asScalar()) to "<="
      )
    )

    val integers = (SELECT.COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) FROM IMMUTABLE_VALUE_WITH_FIELDS)
    assertEachSql(
      query = query,
      expected = { operator ->
        """$where $operator (SELECT "immutable_value_with_fields"."integer" """ +
            """FROM "main"."immutable_value_with_fields")"""
      },
      cases = listOf(
        (related IS integers.asScalar()) to "=",
        (related IS_NOT integers.asScalar()) to "!=",
        (related IN integers) to "IN",
        (related NOT_IN integers) to "NOT IN",
        (related GREATER_THAN integers.asScalar()) to ">",
        (related GREATER_OR_EQUAL integers.asScalar()) to ">=",
        (related LESS_THAN integers.asScalar()) to "<",
        (related LESS_OR_EQUAL integers.asScalar()) to "<="
      )
    )
  }
}
