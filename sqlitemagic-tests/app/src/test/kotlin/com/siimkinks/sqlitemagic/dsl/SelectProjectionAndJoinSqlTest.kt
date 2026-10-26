package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.CompiledSelect
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.EmbeddedValueEntityTable.Companion.EMBEDDED_VALUE_ENTITY
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.EntityWithStringIdRelationshipTable.Companion.ENTITY_WITH_STRING_ID_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.QueryCompositionAuthorTable.Companion.QUERY_COMPOSITION_AUTHOR
import com.siimkinks.sqlitemagic.QueryCompositionAuthorViewTable.Companion.QUERY_COMPOSITION_AUTHOR_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.mapTo
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

internal class SelectProjectionAndJoinSqlTest {
  private val simpleEntityBody = """SELECT "simple_mutable_entity"."id" AS "simple_mutable_entity.id", """ +
      """"simple_mutable_entity"."value" AS "simple_mutable_entity.value", """ +
      """"simple_mutable_entity"."boxed_boolean" AS "simple_mutable_entity.boxed_boolean", """ +
      """"simple_mutable_entity"."primitive_boolean" AS "simple_mutable_entity.primitive_boolean" """ +
      """FROM "main"."simple_mutable_entity""""

  private val queryCompositionAuthorViewIdRequired = "QueryCompositionAuthorView.id is required; " +
      """select "query_composition_author_view"."query_composition_author.id""""

  @Test
  fun `SELECT all FROM aliased table renders SQL`() {
    (SELECT FROM (ENTITY_WITH_RELATIONSHIP AS "b"))
      .assertSql(
        """SELECT "b"."id", "b"."value", "b"."related_entity", "b"."count" """ +
            """FROM "main"."entity_with_relationship" AS "b""""
      )
  }

  @Test
  fun `SELECT single column renders SQL`() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
      .assertSql(
        """SELECT "entity_with_relationship"."related_entity" FROM "main"."entity_with_relationship""""
      )

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
      .assertSql(
        """SELECT DISTINCT "entity_with_relationship"."related_entity" FROM "main"."entity_with_relationship""""
      )
  }

  @Test
  fun `SELECT single column FROM aliased table renders SQL`() {
    val b = ENTITY_WITH_RELATIONSHIP AS "b"

    (SELECT
        COLUMN b.RELATED_ENTITY
        FROM b)
      .assertSql("""SELECT "b"."related_entity" FROM "main"."entity_with_relationship" AS "b"""")

    (SELECT
        DISTINCT b.RELATED_ENTITY
        FROM b)
      .assertSql("""SELECT DISTINCT "b"."related_entity" FROM "main"."entity_with_relationship" AS "b"""")
  }

  @Test
  fun `SELECT all FROM aliased subquery renders SQL`() {
    val b = (SELECT FROM SIMPLE_MUTABLE_ENTITY) AS "b"
    val expected = """SELECT "b"."simple_mutable_entity.id", "b"."simple_mutable_entity.value", """ +
        """"b"."simple_mutable_entity.boxed_boolean", "b"."simple_mutable_entity.primitive_boolean" """ +
        """FROM ($simpleEntityBody) AS "b""""

    val plan: CompiledSelect<SimpleMutableEntity> = (SELECT FROM b).compile()
    assertThat(plan.sql).isEqualTo(expected)

    (SELECT FROM (b AS "b"))
      .assertSql(expected)
  }

  @Test
  fun `SELECT single column FROM subquery renders SQL`() {
    val all = SELECT FROM SIMPLE_MUTABLE_ENTITY

    (SELECT
        COLUMN all[SIMPLE_MUTABLE_ENTITY.VALUE]
        FROM all)
      .assertSql("""SELECT "simple_mutable_entity.value" FROM ($simpleEntityBody)""")

    (SELECT
        DISTINCT all[SIMPLE_MUTABLE_ENTITY.VALUE]
        FROM all)
      .assertSql("""SELECT DISTINCT "simple_mutable_entity.value" FROM ($simpleEntityBody)""")
  }

  @Test
  fun `SELECT single column FROM aliased subquery renders SQL`() {
    val a = (SELECT FROM SIMPLE_MUTABLE_ENTITY) AS "a"

    val plan: CompiledSelect<String?> = (SELECT
        COLUMN a[SIMPLE_MUTABLE_ENTITY.VALUE]
        FROM a)
      .compile()
    assertThat(plan.sql).isEqualTo("""SELECT "a"."simple_mutable_entity.value" FROM ($simpleEntityBody) AS "a"""")

    (SELECT
        DISTINCT a[SIMPLE_MUTABLE_ENTITY.VALUE]
        FROM a)
      .assertSql("""SELECT DISTINCT "a"."simple_mutable_entity.value" FROM ($simpleEntityBody) AS "a"""")
  }

  @Test
  fun `SELECT column renders SQL`() {
    val plan: CompiledSelect<String?> = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .compile()
    assertThat(plan.sql)
      .isEqualTo("""SELECT "entity_with_relationship"."value" FROM "main"."entity_with_relationship"""")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .assertSql("""SELECT DISTINCT "entity_with_relationship"."value" FROM "main"."entity_with_relationship"""")
  }

  @Test
  fun `SELECT column renders SQL when aliased`() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val plan: CompiledSelect<String?> = (SELECT
        COLUMN m.VALUE
        FROM m)
      .compile()
    assertThat(plan.sql)
      .isEqualTo("""SELECT "m"."value" FROM "main"."entity_with_relationship" AS "m"""")

    val selectedValue = m.VALUE AS """output.alias"name"""
    val aliasedPlan: CompiledSelect<String?> = (SELECT
        COLUMN selectedValue
        FROM m)
      .compile()
    assertThat(aliasedPlan.sql).isEqualTo(
      """SELECT "m"."value" AS "output.alias""name" """ +
          """FROM "main"."entity_with_relationship" AS "m""""
    )

    (SELECT
        DISTINCT m.VALUE
        FROM m)
      .assertSql("""SELECT DISTINCT "m"."value" FROM "main"."entity_with_relationship" AS "m"""")
  }

  @Test
  fun `SELECT columns renders SQL`() {
    val columns = """"entity_with_relationship"."related_entity", "entity_with_relationship"."value", """ +
        """"entity_with_relationship"."count""""

    SELECT
      .COLUMNS(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.COUNT)
      .FROM(ENTITY_WITH_RELATIONSHIP)
      .mapTo(ENTITY_WITH_RELATIONSHIP)
      .assertSql("""SELECT $columns FROM "main"."entity_with_relationship"""")

    SELECT
      .DISTINCT(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.COUNT)
      .FROM(ENTITY_WITH_RELATIONSHIP)
      .mapTo(ENTITY_WITH_RELATIONSHIP)
      .assertSql("""SELECT DISTINCT $columns FROM "main"."entity_with_relationship"""")

    SELECT
      .COLUMNS(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, SIMPLE_MUTABLE_ENTITY.ID)
      .FROM(ENTITY_WITH_RELATIONSHIP)
      .INNER_JOIN(SIMPLE_MUTABLE_ENTITY)
      .USING(ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY, SIMPLE_MUTABLE_ENTITY.ID)
      .assertSql(
        """SELECT "entity_with_relationship"."related_entity", "simple_mutable_entity"."id" """ +
            """FROM "main"."entity_with_relationship" INNER JOIN "main"."simple_mutable_entity" """ +
            """USING ("related_entity", "id")"""
      )
  }

  @Test
  fun `SELECT columns renders SQL when aliased`() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val countColumn = m.COUNT AS "count_alias"
    val columns = """"m"."related_entity", "m"."value", "m"."count" AS "count_alias""""

    SELECT
      .COLUMNS(m.RELATED_ENTITY, m.VALUE, countColumn)
      .FROM(m)
      .mapTo(m)
      .assertSql("""SELECT $columns FROM "main"."entity_with_relationship" AS "m"""")

    SELECT
      .DISTINCT(m.RELATED_ENTITY, m.VALUE, countColumn)
      .FROM(m)
      .mapTo(m)
      .assertSql("""SELECT DISTINCT $columns FROM "main"."entity_with_relationship" AS "m"""")
  }

  @TestFactory
  fun `partial mappings without required values reject at compile`() = listOf(
    rejectedMappingCase(
      label = "required scalar",
      query = SELECT
        .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.ID)
        .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
        .mapTo(IMMUTABLE_VALUE_WITH_FIELDS),
      expectedMessage = "ImmutableValueWithFields.stringValue is required; " +
          """select "immutable_value_with_fields"."string_value""""
    ),
    rejectedMappingCase(
      label = "required relationship",
      query = SELECT
        .COLUMNS(ENTITY_WITH_STRING_ID_RELATIONSHIP.ID, ENTITY_WITH_STRING_ID_RELATIONSHIP.VALUE)
        .FROM(ENTITY_WITH_STRING_ID_RELATIONSHIP)
        .mapTo(ENTITY_WITH_STRING_ID_RELATIONSHIP),
      expectedMessage = "EntityWithStringIdRelationship.relatedEntity is required; select its string_id_entity fields"
    ),
    rejectedMappingCase(
      label = "required leaf of a selected optional embedded value",
      query = SELECT
        .COLUMNS(
          EMBEDDED_VALUE_ENTITY.ID,
          EMBEDDED_VALUE_ENTITY.REQUIRED_LABEL,
          EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LATITUDE,
          EMBEDDED_VALUE_ENTITY.REQUIRED_COORDINATES_LONGITUDE,
          EMBEDDED_VALUE_ENTITY.REQUIRED_TRANSFORMED_VALUE,
          EMBEDDED_VALUE_ENTITY.OPTIONAL_LABEL
        )
        .FROM(EMBEDDED_VALUE_ENTITY)
        .mapTo(EMBEDDED_VALUE_ENTITY),
      expectedMessage = "EmbeddedValueEntity.optionalDetails.coordinates.latitude is required; " +
          """select "embedded_value_entity"."optional_coordinates_latitude""""
    ),
    rejectedMappingCase(
      label = "view id",
      query = SELECT
        .COLUMN(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_NAME)
        .FROM(QUERY_COMPOSITION_AUTHOR_VIEW)
        .mapTo(QUERY_COMPOSITION_AUTHOR_VIEW),
      expectedMessage = queryCompositionAuthorViewIdRequired
    ),
    rejectedMappingCase(
      label = "view id beside a joined table id",
      query = SELECT
        .COLUMNS(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_NAME, QUERY_COMPOSITION_AUTHOR.ID)
        .FROM(QUERY_COMPOSITION_AUTHOR_VIEW)
        .INNER_JOIN(QUERY_COMPOSITION_AUTHOR)
        .ON(QUERY_COMPOSITION_AUTHOR_VIEW.QUERY_COMPOSITION_AUTHOR_NAME IS QUERY_COMPOSITION_AUTHOR.NAME)
        .mapTo(QUERY_COMPOSITION_AUTHOR_VIEW),
      expectedMessage = queryCompositionAuthorViewIdRequired
    )
  )

  private fun rejectedMappingCase(
    label: String,
    query: SelectQuery<*, *>,
    expectedMessage: String
  ) = dynamicTest(label) {
    assertThat(assertThrows<IllegalArgumentException> { query.compile() })
      .hasMessageThat()
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `joins render SQL`() {
    val related = SIMPLE_MUTABLE_ENTITY AS "related"
    val natural = SIMPLE_MUTABLE_ENTITY AS "natural"
    val using = SIMPLE_MUTABLE_ENTITY AS "using"
    val crossed = SIMPLE_MUTABLE_ENTITY AS "crossed"
    val prefix = """SELECT "entity_with_relationship"."value" FROM "main"."entity_with_relationship""""
    val leftJoin = """ LEFT JOIN "main"."simple_mutable_entity" AS "related" ON """ +
        """"entity_with_relationship"."related_entity" = "related"."id""""

    val left = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN related
        ON (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS related.ID))
    left.assertSql("$prefix$leftJoin")

    val naturalJoined = (left NATURAL_JOIN natural)
    naturalJoined.assertSql("""$prefix$leftJoin NATURAL JOIN "main"."simple_mutable_entity" AS "natural"""")

    val usingJoined = (naturalJoined
        INNER_JOIN using
        USING using.ID)
    usingJoined.assertSql(
      """$prefix$leftJoin NATURAL JOIN "main"."simple_mutable_entity" AS "natural"""" +
          """ INNER JOIN "main"."simple_mutable_entity" AS "using" USING ("id")"""
    )

    (usingJoined
        CROSS_JOIN crossed
        USING arrayOf(crossed.ID, crossed.VALUE))
      .assertSql(
        """$prefix$leftJoin NATURAL JOIN "main"."simple_mutable_entity" AS "natural"""" +
            """ INNER JOIN "main"."simple_mutable_entity" AS "using" USING ("id")""" +
            """ CROSS JOIN "main"."simple_mutable_entity" AS "crossed" USING ("id", "value")"""
      )

    (SELECT
        COLUMN COMPLEX_OBJECT_WITH_SAME_LEAFS.ID
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        CROSS_JOIN ENTITY_WITH_RELATIONSHIP
        CROSS_JOIN SIMPLE_MUTABLE_ENTITY)
      .assertSql(
        """SELECT "complex_object_with_same_leafs"."id" FROM "main"."complex_object_with_same_leafs" """ +
            """CROSS JOIN "main"."entity_with_relationship" CROSS JOIN "main"."simple_mutable_entity""""
      )
  }

  @Test
  fun `join conditions compare relationship IDs`() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val other = ENTITY_WITH_RELATIONSHIP AS "other"
    val prefix = """SELECT "m"."value" FROM "main"."entity_with_relationship" AS "m" """ +
        """LEFT JOIN "main"."entity_with_relationship" AS "other" ON """

    (SELECT
        COLUMN m.VALUE
        FROM m
        LEFT_JOIN other
        ON (m.RELATED_ENTITY IS_NOT other.RELATED_ENTITY))
      .assertSql("""$prefix"m"."related_entity" != "other"."related_entity"""")
    (SELECT
        COLUMN m.VALUE
        FROM m
        LEFT_JOIN other
        ON (m.RELATED_ENTITY IS_NOT other.ID))
      .assertSql("""$prefix"m"."related_entity" != "other"."id"""")
    (SELECT
        COLUMN m.VALUE
        FROM m
        LEFT_JOIN other
        ON (other.RELATED_ENTITY IS 101L))
      .assertSql("""$prefix"other"."related_entity" = ?""")
  }
}
