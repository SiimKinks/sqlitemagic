package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GROUP_BY
import com.siimkinks.sqlitemagic.HAVING
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LEFT_JOIN
import com.siimkinks.sqlitemagic.LIMIT
import com.siimkinks.sqlitemagic.OFFSET
import com.siimkinks.sqlitemagic.ORDER_BY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.asColumn
import com.siimkinks.sqlitemagic.concat
import com.siimkinks.sqlitemagic.isEqualTo
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer
import org.junit.jupiter.api.Test

class SelectGroupingAndOrderingSqlTest : DSLTests {
  @Test
  fun groupByTest() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship "

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.VALUE)
      .isEqualTo(expectedBase + "GROUP BY entity_with_relationship.value ")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
      .isEqualTo(expectedBase + "GROUP BY entity_with_relationship.related_entity ")


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY arrayOf(
      ENTITY_WITH_RELATIONSHIP.ID,
      ENTITY_WITH_RELATIONSHIP.VALUE,
      ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    ))
      .isEqualTo(
        expectedBase +
            "GROUP BY entity_with_relationship.id,entity_with_relationship.value," +
            "entity_with_relationship.related_entity "
      )


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY ENTITY_WITH_RELATIONSHIP.VALUE
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS 1990))
      .isEqualTo(
        expectedBase + "GROUP BY entity_with_relationship.value HAVING entity_with_relationship.count=? ",
        "1990"
      )


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        GROUP_BY arrayOf(ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS 1990))
      .isEqualTo(
        expectedBase +
            "GROUP BY entity_with_relationship.value,entity_with_relationship.related_entity " +
            "HAVING entity_with_relationship.count=? ",
        "1990"
      )


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(ENTITY_WITH_RELATIONSHIP.VALUE, ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)
        HAVING (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.immutable_value_with_fields GROUP BY entity_with_relationship.value," +
            "entity_with_relationship.related_entity HAVING entity_with_relationship.count=" +
            "immutable_value_with_fields.integer "
      )


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS true))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=? ",
        BooleanTransformer.objectToDbValue(true)!!.toString()
      )


    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=simple_mutable_entity.primitive_boolean "
      )
  }

  @Test
  fun groupByTestAliased() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship AS m "
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val s = IMMUTABLE_VALUE_WITH_FIELDS AS "s"
    val a = SIMPLE_MUTABLE_ENTITY AS "a"

    (SELECT
        FROM m
        GROUP_BY m.VALUE)
      .isEqualTo(expectedBase + "GROUP BY m.value ")

    (SELECT
        FROM m
        GROUP_BY m.RELATED_ENTITY)
      .isEqualTo(expectedBase + "GROUP BY m.related_entity ")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.ID, m.VALUE, m.RELATED_ENTITY))
      .isEqualTo(expectedBase + "GROUP BY m.id,m.value,m.related_entity ")

    (SELECT
        FROM m
        GROUP_BY m.VALUE
        HAVING (m.COUNT IS 1990))
      .isEqualTo(expectedBase + "GROUP BY m.value HAVING m.count=? ", "1990")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS 1990))
      .isEqualTo(expectedBase + "GROUP BY m.value,m.related_entity HAVING m.count=? ", "1990")

    (SELECT
        FROM m
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.immutable_value_with_fields GROUP BY m.value,m.related_entity " +
            "HAVING m.count=immutable_value_with_fields.integer "
      )

    (SELECT
        FROM m
        LEFT_JOIN s
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS s.INTEGER))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.immutable_value_with_fields AS s GROUP BY m.value,m.related_entity " +
            "HAVING m.count=s.integer "
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS true))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=? ",
        BooleanTransformer.objectToDbValue(true)!!.toString()
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS true))
      .isEqualTo(
        expectedBase + "LEFT JOIN main.simple_mutable_entity AS a " +
            "GROUP BY a.boxed_boolean HAVING a.primitive_boolean=? ",
        BooleanTransformer.objectToDbValue(true)!!.toString()
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=simple_mutable_entity.primitive_boolean "
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS a.PRIMITIVE_BOOLEAN))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN main.simple_mutable_entity AS a GROUP BY a.boxed_boolean " +
            "HAVING a.primitive_boolean=a.primitive_boolean "
      )
  }

  @Test
  fun orderByTest() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship "

    var expected = expectedBase + "ORDER BY entity_with_relationship.count ASC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY ENTITY_WITH_RELATIONSHIP.COUNT.asc())
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY entity_with_relationship.count ASC,entity_with_relationship.value ASC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(ENTITY_WITH_RELATIONSHIP.COUNT.asc(), ENTITY_WITH_RELATIONSHIP.VALUE.asc()))
      .isEqualTo(expected)

    expected = expectedBase +
        "ORDER BY entity_with_relationship.count ASC,entity_with_relationship.value ASC," +
        "entity_with_relationship.related_entity ASC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(
      ENTITY_WITH_RELATIONSHIP.COUNT.asc(),
      ENTITY_WITH_RELATIONSHIP.VALUE.asc(),
      ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.asc()
    ))
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY entity_with_relationship.count DESC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY ENTITY_WITH_RELATIONSHIP.COUNT.desc())
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY entity_with_relationship.count DESC,entity_with_relationship.value ASC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(ENTITY_WITH_RELATIONSHIP.COUNT.desc(), ENTITY_WITH_RELATIONSHIP.VALUE.asc()))
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY entity_with_relationship.count ASC,entity_with_relationship.value DESC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY arrayOf(ENTITY_WITH_RELATIONSHIP.COUNT.asc(), ENTITY_WITH_RELATIONSHIP.VALUE.desc()))
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY trim(entity_with_relationship.count) DESC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY ENTITY_WITH_RELATIONSHIP.COUNT.trim().desc())
      .isEqualTo(expected)

    expected = expectedBase + "ORDER BY entity_with_relationship.count || ' ' || entity_with_relationship.value ASC "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        ORDER_BY ((ENTITY_WITH_RELATIONSHIP.COUNT concat " ".asColumn) concat ENTITY_WITH_RELATIONSHIP.VALUE).asc())
      .isEqualTo(expected)
  }

  @Test
  fun limitTest() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship "

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LIMIT 4)
      .isEqualTo(expectedBase + "LIMIT 4 ")

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LIMIT 55
        OFFSET 6)
      .isEqualTo(expectedBase + "LIMIT 55 OFFSET 6 ")
  }
}
