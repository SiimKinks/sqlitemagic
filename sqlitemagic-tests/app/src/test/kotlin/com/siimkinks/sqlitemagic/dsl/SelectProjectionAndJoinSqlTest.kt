package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.CROSS_JOIN
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DISTINCT
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.INNER_JOIN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.JOIN
import com.siimkinks.sqlitemagic.LEFT_JOIN
import com.siimkinks.sqlitemagic.LEFT_OUTER_JOIN
import com.siimkinks.sqlitemagic.NATURAL_JOIN
import com.siimkinks.sqlitemagic.ON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.USING
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class SelectProjectionAndJoinSqlTest : DSLTests {
  @Test
  fun selectAllFromAliased() {
    (SELECT FROM (ENTITY_WITH_RELATIONSHIP AS "b"))
      .isEqualTo("SELECT * FROM main.entity_with_relationship AS b ")
  }

  @Test
  fun selectAllFromAliasedSubquery() {
    val expected = "SELECT * FROM (SELECT * FROM main.simple_mutable_entity ) AS b "

    val b = (SELECT
        FROM SIMPLE_MUTABLE_ENTITY)
      .toTable("b")
    val b2 = b AS "b"

    (SELECT FROM b)
      .isEqualTo(expected)

    (SELECT FROM b2)
      .isEqualTo(expected)
  }

  @Test
  fun selectSingleColumn() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM main.entity_with_relationship ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.related_entity FROM main.entity_with_relationship ")
  }

  @Test
  fun selectSingleColumnFromSubquery() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM (SELECT * FROM main.simple_mutable_entity ) ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT DISTINCT entity_with_relationship.related_entity " +
            "FROM (SELECT * FROM main.simple_mutable_entity ) "
      )
  }

  @Test
  fun selectSingleColumnFromAliased() {
    val b = ENTITY_WITH_RELATIONSHIP AS "b"

    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM b)
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM main.entity_with_relationship AS b ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM b)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.related_entity FROM main.entity_with_relationship AS b ")
  }

  @Test
  fun selectSingleColumnFromAliasedSubquery() {
    val a = (SELECT
        FROM SIMPLE_MUTABLE_ENTITY)
      .toTable("a")

    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM a)
      .isEqualTo(
        "SELECT entity_with_relationship.related_entity " +
            "FROM (SELECT * FROM main.simple_mutable_entity ) AS a "
      )

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM a)
      .isEqualTo(
        "SELECT DISTINCT entity_with_relationship.related_entity FROM " +
            "(SELECT * FROM main.simple_mutable_entity ) AS a "
      )
  }

  @Test
  fun selectColumn() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT entity_with_relationship.value FROM main.entity_with_relationship ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.value FROM main.entity_with_relationship ")
  }

  @Test
  fun selectColumnAliased() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    (SELECT
        COLUMN m.VALUE
        FROM m)
      .isEqualTo("SELECT m.value FROM main.entity_with_relationship AS m ")

    (SELECT
        DISTINCT m.VALUE
        FROM m)
      .isEqualTo("SELECT DISTINCT m.value FROM main.entity_with_relationship AS m ")
  }

  @Test
  fun selectColumns() {
    (SELECT
        COLUMNS arrayOf(
      ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
      ENTITY_WITH_RELATIONSHIP.VALUE,
      ENTITY_WITH_RELATIONSHIP.COUNT
    )
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo(
        "SELECT entity_with_relationship.related_entity,entity_with_relationship.value," +
            "entity_with_relationship.count FROM main.entity_with_relationship "
      )

    (SELECT
        DISTINCT arrayOf(
      ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
      ENTITY_WITH_RELATIONSHIP.VALUE,
      ENTITY_WITH_RELATIONSHIP.COUNT
    )
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo(
        "SELECT DISTINCT entity_with_relationship.related_entity,entity_with_relationship.value," +
            "entity_with_relationship.count FROM main.entity_with_relationship "
      )
  }

  @Test
  fun selectColumnsAliased() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val countColumn = m.COUNT AS "count_alias"
    (SELECT
        COLUMNS arrayOf(m.RELATED_ENTITY, m.VALUE, countColumn)
        FROM m)
      .isEqualTo("SELECT m.related_entity,m.value,m.count AS 'count_alias' FROM main.entity_with_relationship AS m ")

    (SELECT
        DISTINCT arrayOf(m.RELATED_ENTITY, m.VALUE, countColumn)
        FROM m)
      .isEqualTo(
        "SELECT DISTINCT m.related_entity,m.value,m.count AS 'count_alias' " +
            "FROM main.entity_with_relationship AS m "
      )
  }

  @Test
  fun joins() {
    var expected = "SELECT * FROM main.entity_with_relationship LEFT OUTER JOIN main.simple_mutable_entity ON " +
        "entity_with_relationship.related_entity=simple_mutable_entity.id "
    var sqlNode = (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_OUTER_JOIN (
        SIMPLE_MUTABLE_ENTITY ON (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS SIMPLE_MUTABLE_ENTITY.ID
            )
        ))
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode NATURAL_JOIN SIMPLE_MUTABLE_ENTITY)
    expected += "NATURAL JOIN main.simple_mutable_entity "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (
        SIMPLE_MUTABLE_ENTITY USING arrayOf(
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
          SIMPLE_MUTABLE_ENTITY.ID
        )
        ))
    expected += "INNER JOIN main.simple_mutable_entity USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (
        SIMPLE_MUTABLE_ENTITY USING arrayOf(
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
          SIMPLE_MUTABLE_ENTITY.ID
        )
        ))
    expected += "CROSS JOIN main.simple_mutable_entity USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        JOIN ENTITY_WITH_RELATIONSHIP
        JOIN SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(
        "SELECT * FROM main.complex_object_with_same_leafs , " +
            "main.entity_with_relationship , main.simple_mutable_entity "
      )
  }

  @Test
  fun joinsAliased() {
    var expected = "SELECT * FROM main.entity_with_relationship AS m " +
        "LEFT OUTER JOIN main.simple_mutable_entity AS a ON " +
        "m.related_entity=a.id "

    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val a = SIMPLE_MUTABLE_ENTITY AS "a"
    var sqlNode = (SELECT
        FROM m
        LEFT_OUTER_JOIN (a ON (m.RELATED_ENTITY IS a.ID)))
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode NATURAL_JOIN a)
    expected += "NATURAL JOIN main.simple_mutable_entity AS a "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (a USING arrayOf(m.RELATED_ENTITY, a.ID)))
    expected += "INNER JOIN main.simple_mutable_entity AS a USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (a USING arrayOf(m.RELATED_ENTITY, a.ID)))
    expected += "CROSS JOIN main.simple_mutable_entity AS a USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM (COMPLEX_OBJECT_WITH_SAME_LEAFS AS "c")
        JOIN m
        JOIN a)
      .isEqualTo(
        "SELECT * FROM main.complex_object_with_same_leafs AS c , main.entity_with_relationship AS m , " +
            "main.simple_mutable_entity AS a "
      )
  }

  @Test
  fun joinComplex() {
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val expectedBase = "SELECT * FROM main.entity_with_relationship "

    var expected = expectedBase +
        "LEFT JOIN main.entity_with_relationship ON " +
        "entity_with_relationship.related_entity!=entity_with_relationship.related_entity "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        )))
      .isEqualTo(expected)

    expected = expectedBase +
        "LEFT JOIN main.entity_with_relationship ON " +
        "entity_with_relationship.related_entity!=entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID
        )))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN main.entity_with_relationship ON entity_with_relationship.related_entity=? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS checkNotNull(simpleMutableEntity.id)
        )))
      .isEqualTo(expected, simpleMutableEntity.id.toString())
  }

  @Test
  fun joinComplexAliased() {
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val expectedBase = "SELECT * FROM main.entity_with_relationship AS m "
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase +
        "LEFT JOIN main.entity_with_relationship ON " +
        "m.related_entity!=entity_with_relationship.related_entity "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN main.entity_with_relationship AS m ON m.related_entity!=m.related_entity "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS_NOT m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase +
        "LEFT JOIN main.entity_with_relationship ON m.related_entity!=entity_with_relationship.id "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN main.entity_with_relationship AS m ON m.related_entity!=m.id "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS_NOT m.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN main.entity_with_relationship ON m.related_entity=? "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS checkNotNull(simpleMutableEntity.id))))
      .isEqualTo(expected, simpleMutableEntity.id.toString())

    expected = expectedBase + "LEFT JOIN main.entity_with_relationship AS m ON m.related_entity=? "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS checkNotNull(simpleMutableEntity.id))))
      .isEqualTo(expected, simpleMutableEntity.id.toString())
  }
}
