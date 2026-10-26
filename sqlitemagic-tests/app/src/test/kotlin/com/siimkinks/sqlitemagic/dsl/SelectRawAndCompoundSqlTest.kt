package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EXCEPT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.INTERSECT
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.UNION
import com.siimkinks.sqlitemagic.UNION_ALL
import com.siimkinks.sqlitemagic.mapTo
import org.junit.jupiter.api.Test

internal class SelectRawAndCompoundSqlTest {
  private fun Map<String, SelectQuery<*, *>>.sql() = mapValues { (_, query) -> query.compile().sql }

  @Test
  fun `raw SELECT queries keep their SQL`() {
    val all = "SELECT * FROM simple_mutable_entity"
    val filtered = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"

    assertThat(
      mapOf(
        "infix" to (SELECT RAW all)
          .toCursor()
          .sql,
        "argument, observed table and read columns" to SELECT
          .RAW(filtered, "foo")
          .observes(SIMPLE_MUTABLE_ENTITY)
          .readAs(
            SIMPLE_MUTABLE_ENTITY.ID,
            SIMPLE_MUTABLE_ENTITY.VALUE,
            SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
            SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
          )
          .compile()
          .sql
      )
    ).isEqualTo(
      mapOf(
        "infix" to all,
        "argument, observed table and read columns" to filtered
      )
    )
  }

  @Test
  fun `SELECT all FROM table renders SQL`() {
    val columns = """"entity_with_relationship"."id", "entity_with_relationship"."value", """ +
        """"entity_with_relationship"."related_entity", "entity_with_relationship"."count""""

    assertThat(
      mapOf(
        "plain" to (SELECT FROM ENTITY_WITH_RELATIONSHIP),
        "DISTINCT" to (SELECT.DISTINCT FROM ENTITY_WITH_RELATIONSHIP)
      ).sql()
    ).isEqualTo(
      mapOf(
        "plain" to """SELECT $columns FROM "main"."entity_with_relationship"""",
        "DISTINCT" to """SELECT DISTINCT $columns FROM "main"."entity_with_relationship""""
      )
    )
  }

  @Test
  fun `SELECT all FROM subquery renders SQL`() {
    val outputs = """"simple_mutable_entity.id", "simple_mutable_entity.value", """ +
        """"simple_mutable_entity.boxed_boolean", "simple_mutable_entity.primitive_boolean""""
    val body = """SELECT "simple_mutable_entity"."id" AS "simple_mutable_entity.id", """ +
        """"simple_mutable_entity"."value" AS "simple_mutable_entity.value", """ +
        """"simple_mutable_entity"."boxed_boolean" AS "simple_mutable_entity.boxed_boolean", """ +
        """"simple_mutable_entity"."primitive_boolean" AS "simple_mutable_entity.primitive_boolean" """ +
        """FROM "main"."simple_mutable_entity""""

    assertThat(
      mapOf(
        "plain" to (SELECT FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY)),
        "DISTINCT" to (SELECT.DISTINCT FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      ).sql()
    ).isEqualTo(
      mapOf(
        "plain" to "SELECT $outputs FROM ($body)",
        "DISTINCT" to "SELECT DISTINCT $outputs FROM ($body)"
      )
    )
  }

  @Test
  fun `compound operators combine entity arms`() {
    assertThat(
      mapOf(
        "UNION" to (SELECT FROM SIMPLE_MUTABLE_ENTITY UNION (SELECT FROM SIMPLE_MUTABLE_ENTITY)),
        "UNION ALL" to (SELECT FROM SIMPLE_MUTABLE_ENTITY UNION_ALL (SELECT FROM SIMPLE_MUTABLE_ENTITY)),
        "INTERSECT" to (SELECT FROM SIMPLE_MUTABLE_ENTITY INTERSECT (SELECT FROM SIMPLE_MUTABLE_ENTITY)),
        "EXCEPT" to (SELECT FROM SIMPLE_MUTABLE_ENTITY EXCEPT (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      ).sql()
    ).isEqualTo(
      listOf("UNION", "UNION ALL", "INTERSECT", "EXCEPT").associateWith {
        "$SIMPLE_MUTABLE_ENTITY_SELECT $it $SIMPLE_MUTABLE_ENTITY_SELECT"
      }
    )
  }

  @Test
  fun `compound arms keep their filters`() {
    val filtered = """$SIMPLE_MUTABLE_ENTITY_SELECT WHERE "simple_mutable_entity"."value" = ?"""

    assertThat(
      mapOf(
        "right arm" to (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            UNION
            (SELECT
                FROM SIMPLE_MUTABLE_ENTITY
                WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo"))),
        "both arms" to (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")
            UNION
            (SELECT
                FROM SIMPLE_MUTABLE_ENTITY
                WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")))
      ).sql()
    ).isEqualTo(
      mapOf(
        "right arm" to "$SIMPLE_MUTABLE_ENTITY_SELECT UNION $filtered",
        "both arms" to "$filtered UNION $filtered"
      )
    )
  }

  @Test
  fun `compound arms list their columns`() {
    val entities = SELECT FROM SIMPLE_MUTABLE_ENTITY
    val rows = SELECT
      .COLUMNS(
        SIMPLE_MUTABLE_ENTITY.ID,
        SIMPLE_MUTABLE_ENTITY.VALUE,
        SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
        SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
      )
      .FROM(SIMPLE_MUTABLE_ENTITY)
    val partialEntities = rows.mapTo(SIMPLE_MUTABLE_ENTITY)
    val fieldLists = SELECT
      .COLUMNS(SIMPLE_MUTABLE_ENTITY.all())
      .FROM(SIMPLE_MUTABLE_ENTITY)

    assertThat(
      mapOf(
        "entity and partial-entity arms" to (entities UNION partialEntities),
        "typed-row arms" to (rows UNION_ALL rows),
        "field-list arms" to (fieldLists INTERSECT fieldLists),
        "partial-entity and entity arms" to (partialEntities EXCEPT entities)
      ).sql()
    ).isEqualTo(
      mapOf(
        "entity and partial-entity arms" to "$SIMPLE_MUTABLE_ENTITY_SELECT UNION $SIMPLE_MUTABLE_ENTITY_SELECT",
        "typed-row arms" to "$SIMPLE_MUTABLE_ENTITY_SELECT UNION ALL $SIMPLE_MUTABLE_ENTITY_SELECT",
        "field-list arms" to "$SIMPLE_MUTABLE_ENTITY_SELECT INTERSECT $SIMPLE_MUTABLE_ENTITY_SELECT",
        "partial-entity and entity arms" to "$SIMPLE_MUTABLE_ENTITY_SELECT EXCEPT $SIMPLE_MUTABLE_ENTITY_SELECT"
      )
    )
  }
}
