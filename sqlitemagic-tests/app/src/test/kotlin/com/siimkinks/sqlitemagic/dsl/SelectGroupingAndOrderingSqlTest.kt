package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.CONCAT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.TRIM
import org.junit.jupiter.api.Test

internal class SelectGroupingAndOrderingSqlTest {
  @Test
  fun `GROUP BY renders SQL`() {
    val base = selectAll("entity_with_relationship")
    val id = """"entity_with_relationship"."id""""
    val value = """"entity_with_relationship"."value""""
    val relatedEntity = """"entity_with_relationship"."related_entity""""
    val count = """"entity_with_relationship"."count""""

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.VALUE)
      .assertSql("$base GROUP BY $value")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
      .assertSql("$base GROUP BY $relatedEntity")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY arrayOf(
      ENTITY_WITH_RELATIONSHIP.ID,
      ENTITY_WITH_RELATIONSHIP.VALUE,
      ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    ))
      .assertSql("$base GROUP BY $id, $value, $relatedEntity")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.VALUE
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS 1990))
      .assertSql("$base GROUP BY $value HAVING $count = ?")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY arrayOf(ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS 1990))
      .assertSql("$base GROUP BY $value, $relatedEntity HAVING $count = ?")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
      .assertSql(
        """$base LEFT JOIN "main"."immutable_value_with_fields" GROUP BY $value, $relatedEntity """ +
            """HAVING $count = "immutable_value_with_fields"."integer""""
      )

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS true))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" GROUP BY "simple_mutable_entity"."boxed_boolean" """ +
            """HAVING "simple_mutable_entity"."primitive_boolean" = ?"""
      )

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" GROUP BY "simple_mutable_entity"."boxed_boolean" """ +
            """HAVING "simple_mutable_entity"."primitive_boolean" = "simple_mutable_entity"."primitive_boolean""""
      )
  }

  @Test
  fun `GROUP BY renders SQL when aliased`() {
    val base = """${selectAll("m")} AS "m""""
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val s = IMMUTABLE_VALUE_WITH_FIELDS AS "s"
    val a = SIMPLE_MUTABLE_ENTITY AS "a"

    (SELECT
        FROM m
        GROUP_BY m.VALUE)
      .assertSql("""$base GROUP BY "m"."value"""")

    (SELECT
        FROM m
        GROUP_BY m.RELATED_ENTITY)
      .assertSql("""$base GROUP BY "m"."related_entity"""")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.ID, m.VALUE, m.RELATED_ENTITY))
      .assertSql("""$base GROUP BY "m"."id", "m"."value", "m"."related_entity"""")

    (SELECT
        FROM m
        GROUP_BY m.VALUE
        HAVING (m.COUNT IS 1990))
      .assertSql("""$base GROUP BY "m"."value" HAVING "m"."count" = ?""")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS 1990))
      .assertSql("""$base GROUP BY "m"."value", "m"."related_entity" HAVING "m"."count" = ?""")

    (SELECT
        FROM m
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
      .assertSql(
        """$base LEFT JOIN "main"."immutable_value_with_fields" GROUP BY "m"."value", "m"."related_entity" """ +
            """HAVING "m"."count" = "immutable_value_with_fields"."integer""""
      )

    (SELECT
        FROM m
        LEFT_JOIN s
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS s.INTEGER))
      .assertSql(
        """$base LEFT JOIN "main"."immutable_value_with_fields" AS "s" """ +
            """GROUP BY "m"."value", "m"."related_entity" HAVING "m"."count" = "s"."integer""""
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS true))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" GROUP BY "simple_mutable_entity"."boxed_boolean" """ +
            """HAVING "simple_mutable_entity"."primitive_boolean" = ?"""
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS true))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" AS "a" GROUP BY "a"."boxed_boolean" """ +
            """HAVING "a"."primitive_boolean" = ?"""
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" GROUP BY "simple_mutable_entity"."boxed_boolean" """ +
            """HAVING "simple_mutable_entity"."primitive_boolean" = "simple_mutable_entity"."primitive_boolean""""
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS a.PRIMITIVE_BOOLEAN))
      .assertSql(
        """$base LEFT JOIN "main"."simple_mutable_entity" AS "a" GROUP BY "a"."boxed_boolean" """ +
            """HAVING "a"."primitive_boolean" = "a"."primitive_boolean""""
      )
  }

  @Test
  fun `ORDER BY renders SQL`() {
    val base = selectAll("entity_with_relationship")
    val count = ENTITY_WITH_RELATIONSHIP.COUNT
    val value = ENTITY_WITH_RELATIONSHIP.VALUE
    val countSql = """"entity_with_relationship"."count""""
    val valueSql = """"entity_with_relationship"."value""""

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY count.ASC())
      .assertSql("$base ORDER BY $countSql ASC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(count.ASC(), value.ASC()))
      .assertSql("$base ORDER BY $countSql ASC, $valueSql ASC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(count.ASC(), value.ASC(), ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.ASC()))
      .assertSql("""$base ORDER BY $countSql ASC, $valueSql ASC, "entity_with_relationship"."related_entity" ASC""")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY count.DESC())
      .assertSql("$base ORDER BY $countSql DESC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(count.DESC(), value.ASC()))
      .assertSql("$base ORDER BY $countSql DESC, $valueSql ASC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(count.ASC(), value.DESC()))
      .assertSql("$base ORDER BY $countSql ASC, $valueSql DESC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY TRIM(value).DESC())
      .assertSql("$base ORDER BY TRIM($valueSql) DESC")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY (count CONCAT " " CONCAT value).ASC())
      .assertSql("$base ORDER BY ($countSql || ?) || $valueSql ASC")
  }

  @Test
  fun `LIMIT renders SQL`() {
    val base = selectAll("entity_with_relationship")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LIMIT 4)
      .assertSql("$base LIMIT 4")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LIMIT 55
        OFFSET 6)
      .assertSql("$base LIMIT 55 OFFSET 6")
  }
}
