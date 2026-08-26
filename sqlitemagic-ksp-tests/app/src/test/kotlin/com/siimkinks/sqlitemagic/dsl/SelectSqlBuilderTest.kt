package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.BETWEEN
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.CROSS_JOIN
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DISTINCT
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EXCEPT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.Expr
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GLOB
import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.GROUP_BY
import com.siimkinks.sqlitemagic.HAVING
import com.siimkinks.sqlitemagic.IN
import com.siimkinks.sqlitemagic.INNER_JOIN
import com.siimkinks.sqlitemagic.INTERSECT
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.JOIN
import com.siimkinks.sqlitemagic.LEFT_JOIN
import com.siimkinks.sqlitemagic.LEFT_OUTER_JOIN
import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.LIKE
import com.siimkinks.sqlitemagic.LIMIT
import com.siimkinks.sqlitemagic.NATURAL_JOIN
import com.siimkinks.sqlitemagic.NOT_BETWEEN
import com.siimkinks.sqlitemagic.NOT_IN
import com.siimkinks.sqlitemagic.NumericColumn
import com.siimkinks.sqlitemagic.OFFSET
import com.siimkinks.sqlitemagic.ON
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.ORDER_BY
import com.siimkinks.sqlitemagic.RAW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.Select.avg
import com.siimkinks.sqlitemagic.Select.concat
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.UNION
import com.siimkinks.sqlitemagic.UNION_ALL
import com.siimkinks.sqlitemagic.USING
import com.siimkinks.sqlitemagic.ValueParserType.BYTE
import com.siimkinks.sqlitemagic.ValueParserType.DOUBLE
import com.siimkinks.sqlitemagic.ValueParserType.FLOAT
import com.siimkinks.sqlitemagic.ValueParserType.INTEGER
import com.siimkinks.sqlitemagic.ValueParserType.LONG
import com.siimkinks.sqlitemagic.ValueParserType.SHORT
import com.siimkinks.sqlitemagic.ValueParserType.STRING
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.WITH_ARGS
import com.siimkinks.sqlitemagic.asColumn
import com.siimkinks.sqlitemagic.concat
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.isEqualTo
import com.siimkinks.sqlitemagic.minus
import com.siimkinks.sqlitemagic.parsesWith
import com.siimkinks.sqlitemagic.plus
import com.siimkinks.sqlitemagic.rem
import com.siimkinks.sqlitemagic.replace
import com.siimkinks.sqlitemagic.times
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer
import com.siimkinks.sqlitemagic.with
import org.junit.jupiter.api.Test

class SelectSqlBuilderTest : DSLTests {
  @Test
  fun rawSelect() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity")
      .compile()
      .isEqualTo("SELECT * FROM simple_mutable_entity")
  }

  @Test
  fun rawSelectWithArgs() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        WITH_ARGS arrayOf("foo"))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedArgs = arrayOf("foo")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        WITH_ARGS arrayOf("foo")
        FROM SIMPLE_MUTABLE_ENTITY)
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedObservedTables = arrayOf("simple_mutable_entity"),
        expectedArgs = arrayOf("foo")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?"
        FROM SIMPLE_MUTABLE_ENTITY
        WITH_ARGS arrayOf("foo"))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?",
        expectedObservedTables = arrayOf("simple_mutable_entity"),
        expectedArgs = arrayOf("foo")
      )
  }

  @Test
  fun rawSelectWithObservedTable() {
    (SELECT
        RAW "SELECT * FROM simple_mutable_entity"
        FROM SIMPLE_MUTABLE_ENTITY)
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity",
        expectedObservedTables = arrayOf("simple_mutable_entity")
      )

    (SELECT
        RAW "SELECT * FROM entity_with_relationship, simple_mutable_entity"
        FROM arrayOf(ENTITY_WITH_RELATIONSHIP, SIMPLE_MUTABLE_ENTITY))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM entity_with_relationship, simple_mutable_entity",
        expectedObservedTables = arrayOf("entity_with_relationship", "simple_mutable_entity")
      )

    (SELECT
        RAW "SELECT * FROM simple_mutable_entity"
        FROM listOf(SIMPLE_MUTABLE_ENTITY))
      .compile()
      .isEqualTo(
        expectedSql = "SELECT * FROM simple_mutable_entity",
        expectedObservedTables = arrayOf("simple_mutable_entity")
      )
  }

  @Test
  fun selectAllFrom() {
    (SELECT FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT * FROM entity_with_relationship ")

    (SELECT.DISTINCT FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT * FROM entity_with_relationship ")
  }

  @Test
  fun selectAllFromSubquery() {
    (SELECT FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM (SELECT * FROM simple_mutable_entity ) ")

    (SELECT.DISTINCT
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT DISTINCT * FROM (SELECT * FROM simple_mutable_entity ) ")
  }

  @Test
  fun compoundSelectWithArgs() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity UNION " +
            "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?  "
      )
  }

  @Test
  fun compoundSelectWithArgsInBothSelects() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")
        UNION
        (SELECT
            FROM SIMPLE_MUTABLE_ENTITY
            WHERE (SIMPLE_MUTABLE_ENTITY.VALUE IS "foo")))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=? UNION " +
            "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value=?  "
      )
  }

  @Test
  fun unionSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM simple_mutable_entity UNION SELECT * FROM simple_mutable_entity  ")
  }

  @Test
  fun unionSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        ) FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity UNION " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun unionSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        UNION
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity UNION " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun unionAllSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM simple_mutable_entity UNION ALL SELECT * FROM simple_mutable_entity  ")
  }

  @Test
  fun unionAllSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity UNION ALL " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun unionAllSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        UNION_ALL
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity UNION ALL " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun intersectSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM simple_mutable_entity INTERSECT SELECT * FROM simple_mutable_entity  ")
  }

  @Test
  fun intersectSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity INTERSECT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun intersectSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        INTERSECT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity INTERSECT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun exceptSelect() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT * FROM simple_mutable_entity EXCEPT SELECT * FROM simple_mutable_entity  ")
  }

  @Test
  fun exceptSelectExplicitColumns() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity EXCEPT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun exceptSelectExplicitAllColumns() {
    (SELECT
        COLUMNS arrayOf(
      SIMPLE_MUTABLE_ENTITY.ID,
      SIMPLE_MUTABLE_ENTITY.VALUE,
      SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
      SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
    )
        FROM SIMPLE_MUTABLE_ENTITY
        EXCEPT
        (SELECT
            COLUMNS arrayOf(
          SIMPLE_MUTABLE_ENTITY.ID,
          SIMPLE_MUTABLE_ENTITY.VALUE,
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN,
          SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
            FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo(
        "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity EXCEPT " +
            "SELECT simple_mutable_entity.id,simple_mutable_entity.value," +
            "simple_mutable_entity.boxed_boolean,simple_mutable_entity.primitive_boolean " +
            "FROM simple_mutable_entity  "
      )
  }

  @Test
  fun selectAllFromAliased() {
    (SELECT FROM (ENTITY_WITH_RELATIONSHIP AS "b"))
      .isEqualTo("SELECT * FROM entity_with_relationship AS b ")
  }

  @Test
  fun selectAllFromAliasedSubquery() {
    val expected = "SELECT * FROM (SELECT * FROM simple_mutable_entity ) AS b "

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
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM entity_with_relationship ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.related_entity FROM entity_with_relationship ")
  }

  @Test
  fun selectSingleColumnFromSubquery() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM (SELECT * FROM simple_mutable_entity ) ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM (SELECT FROM SIMPLE_MUTABLE_ENTITY))
      .isEqualTo("SELECT DISTINCT entity_with_relationship.related_entity FROM (SELECT * FROM simple_mutable_entity ) ")
  }

  @Test
  fun selectSingleColumnFromAliased() {
    val b = ENTITY_WITH_RELATIONSHIP AS "b"

    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM b)
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM entity_with_relationship AS b ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM b)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.related_entity FROM entity_with_relationship AS b ")
  }

  @Test
  fun selectSingleColumnFromAliasedSubquery() {
    val a = (SELECT
        FROM SIMPLE_MUTABLE_ENTITY)
      .toTable("a")

    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM a)
      .isEqualTo("SELECT entity_with_relationship.related_entity FROM (SELECT * FROM simple_mutable_entity ) AS a ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM a)
      .isEqualTo(
        "SELECT DISTINCT entity_with_relationship.related_entity FROM " +
            "(SELECT * FROM simple_mutable_entity ) AS a "
      )
  }

  @Test
  fun selectColumn() {
    (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT entity_with_relationship.value FROM entity_with_relationship ")

    (SELECT
        DISTINCT ENTITY_WITH_RELATIONSHIP.VALUE
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT DISTINCT entity_with_relationship.value FROM entity_with_relationship ")
  }

  @Test
  fun selectColumnAliased() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    (SELECT
        COLUMN m.VALUE
        FROM m)
      .isEqualTo("SELECT m.value FROM entity_with_relationship AS m ")

    (SELECT
        DISTINCT m.VALUE
        FROM m)
      .isEqualTo("SELECT DISTINCT m.value FROM entity_with_relationship AS m ")
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
            "entity_with_relationship.count FROM entity_with_relationship "
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
            "entity_with_relationship.count FROM entity_with_relationship "
      )
  }

  @Test
  fun selectColumnsAliased() {
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val countColumn = m.COUNT AS "count_alias"
    (SELECT
        COLUMNS arrayOf(m.RELATED_ENTITY, m.VALUE, countColumn)
        FROM m)
      .isEqualTo("SELECT m.related_entity,m.value,m.count AS 'count_alias' FROM entity_with_relationship AS m ")

    (SELECT
        DISTINCT arrayOf(m.RELATED_ENTITY, m.VALUE, countColumn)
        FROM m)
      .isEqualTo(
        "SELECT DISTINCT m.related_entity,m.value,m.count AS 'count_alias' " +
            "FROM entity_with_relationship AS m "
      )
  }

  @Test
  fun joins() {
    var expected = "SELECT * FROM entity_with_relationship LEFT OUTER JOIN simple_mutable_entity ON " +
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
    expected += "NATURAL JOIN simple_mutable_entity "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (
      SIMPLE_MUTABLE_ENTITY USING arrayOf(
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
        SIMPLE_MUTABLE_ENTITY.ID
      )
    ))
    expected += "INNER JOIN simple_mutable_entity USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (
      SIMPLE_MUTABLE_ENTITY USING arrayOf(
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY,
        SIMPLE_MUTABLE_ENTITY.ID
      )
    ))
    expected += "CROSS JOIN simple_mutable_entity USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        JOIN ENTITY_WITH_RELATIONSHIP
        JOIN SIMPLE_MUTABLE_ENTITY)
      .isEqualTo("SELECT * FROM complex_object_with_same_leafs , entity_with_relationship , simple_mutable_entity ")
  }

  @Test
  fun joinsAliased() {
    var expected = "SELECT * FROM entity_with_relationship AS m LEFT OUTER JOIN simple_mutable_entity AS a ON " +
        "m.related_entity=a.id "

    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val a = SIMPLE_MUTABLE_ENTITY AS "a"
    var sqlNode = (SELECT
        FROM m
        LEFT_OUTER_JOIN (a ON (m.RELATED_ENTITY IS a.ID)))
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode NATURAL_JOIN a)
    expected += "NATURAL JOIN simple_mutable_entity AS a "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (a USING arrayOf(m.RELATED_ENTITY, a.ID)))
    expected += "INNER JOIN simple_mutable_entity AS a USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (a USING arrayOf(m.RELATED_ENTITY, a.ID)))
    expected += "CROSS JOIN simple_mutable_entity AS a USING (related_entity,id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM (COMPLEX_OBJECT_WITH_SAME_LEAFS AS "c")
        JOIN m
        JOIN a)
      .isEqualTo(
        "SELECT * FROM complex_object_with_same_leafs AS c , entity_with_relationship AS m , " +
            "simple_mutable_entity AS a "
      )
  }

  @Test
  fun whereCondition() {
    val expectedBase = "SELECT * FROM entity_with_relationship WHERE "

    val titleIs = ENTITY_WITH_RELATIONSHIP.VALUE IS "asd"
    val intIs = ENTITY_WITH_RELATIONSHIP.COUNT IS 1920
    val titleIsNot = ENTITY_WITH_RELATIONSHIP.VALUE IS_NOT "asd"
    val titleIsNotNull = ENTITY_WITH_RELATIONSHIP.VALUE.isNotNull
    val titleIsNull = ENTITY_WITH_RELATIONSHIP.VALUE.isNull
    val titleGlob = ENTITY_WITH_RELATIONSHIP.VALUE GLOB "asd"
    val titleLike = ENTITY_WITH_RELATIONSHIP.VALUE LIKE "asd"
    val lessThan = ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 1990
    val greaterThan = ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 1990
    val between = ENTITY_WITH_RELATIONSHIP.COUNT BETWEEN (1910 AND 2000)
    val notBetween = ENTITY_WITH_RELATIONSHIP.COUNT NOT_BETWEEN (1910 AND 2000)
    val `in` = ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(1910, 1999, 1920)
    val notIn = ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(1910)
    val oneNotIn = ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(1910)

    var expected = "${expectedBase}entity_with_relationship.value=? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE titleIs)
      .isEqualTo(expected, "asd")

    expected = "$expectedBase(entity_with_relationship.value=? AND entity_with_relationship.count=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (titleIs AND intIs))
      .isEqualTo(expected, "asd", "1920")

    expected = "${expectedBase}entity_with_relationship.count BETWEEN ? AND ? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE between)
      .isEqualTo(expected, "1910", "2000")

    expected = "${expectedBase}entity_with_relationship.count NOT BETWEEN ? AND ? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE notBetween)
      .isEqualTo(expected, "1910", "2000")

    expected = "${expectedBase}entity_with_relationship.count IN (?,?,?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE `in`)
      .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause =
      "((((((((entity_with_relationship.value!=? AND " +
          "entity_with_relationship.value IS NOT NULL) AND entity_with_relationship.value IS NULL) " +
          "AND entity_with_relationship.value GLOB ?) AND entity_with_relationship.value LIKE ?) " +
          "AND entity_with_relationship.count<?) AND entity_with_relationship.count>?) " +
          "AND entity_with_relationship.count BETWEEN ? AND ?) AND entity_with_relationship.count NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
      .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected =
      "$expectedBase(((entity_with_relationship.count IN (?,?,?) AND " +
          "entity_with_relationship.count NOT IN (?,?,?)) AND entity_with_relationship.count IN (?)) " +
          "AND entity_with_relationship.count NOT IN (?)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) OR entity_with_relationship.value!=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) AND " +
        "(entity_with_relationship.value!=? AND entity_with_relationship.value IS NOT NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected =
      "$expectedBase(entity_with_relationship.count IN (?,?,?) OR " +
          "(entity_with_relationship.count BETWEEN ? AND ? AND entity_with_relationship.value IS NOT NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR (between AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected =
      "$expectedBase(entity_with_relationship.count IN (?,?,?) AND " +
          "((entity_with_relationship.value!=? OR entity_with_relationship.value IS NOT NULL) OR " +
          "entity_with_relationship.value IS NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) OR entity_with_relationship.value!=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun whereConditionAliased() {
    val expectedBase = "SELECT * FROM entity_with_relationship AS m WHERE "

    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val titleIs = m.VALUE IS "asd"
    val intIs = m.COUNT IS 1920
    val titleIsNot = m.VALUE IS_NOT "asd"
    val titleIsNotNull = m.VALUE.isNotNull
    val titleIsNull = m.VALUE.isNull
    val titleGlob = m.VALUE GLOB "asd"
    val titleLike = m.VALUE LIKE "asd"
    val lessThan = m.COUNT LESS_THAN 1990
    val greaterThan = m.COUNT GREATER_THAN 1990
    val between = m.COUNT BETWEEN (1910 AND 2000)
    val notBetween = m.COUNT NOT_BETWEEN (1910 AND 2000)
    val `in` = m.COUNT IN arrayOf(1910, 1999, 1920)
    val notIn = m.COUNT NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = m.COUNT IN arrayOf(1910)
    val oneNotIn = m.COUNT NOT_IN arrayOf(1910)

    var expected = expectedBase + "m.value=? "
    (SELECT FROM m WHERE titleIs)
      .isEqualTo(expected, "asd")

    expected = "$expectedBase(m.value=? AND m.count=?) "
    (SELECT FROM m WHERE (titleIs AND intIs))
      .isEqualTo(expected, "asd", "1920")

    expected = expectedBase + "m.count BETWEEN ? AND ? "
    (SELECT FROM m WHERE between)
      .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.count NOT BETWEEN ? AND ? "
    (SELECT FROM m WHERE notBetween)
      .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.count IN (?,?,?) "
    (SELECT FROM m WHERE `in`)
      .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause = "((((((((m.value!=? AND m.value IS NOT NULL) AND m.value IS NULL) " +
        "AND m.value GLOB ?) AND m.value LIKE ?) AND m.count<?) AND m.count>?) " +
        "AND m.count BETWEEN ? AND ?) AND m.count NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM m WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
      .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected = expectedBase + "(((m.count IN (?,?,?) AND m.count NOT IN (?,?,?)) " +
        "AND m.count IN (?)) AND m.count NOT IN (?)) "
    (SELECT FROM m WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = "$expectedBase(m.count IN (?,?,?) OR m.value!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(m.count IN (?,?,?) AND (m.value!=? AND m.value IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected =
      "$expectedBase(m.count IN (?,?,?) OR (m.count BETWEEN ? AND ? AND m.value IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` OR (between AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected = "$expectedBase(m.count IN (?,?,?) AND ((m.value!=? OR m.value IS NOT NULL) OR m.value IS NULL)) "
    (SELECT FROM m WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(m.count IN (?,?,?) OR m.value!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun columnNotRedefinedWhenAliased() {
    var expected = "SELECT entity_with_relationship.*,entity_with_relationship.value || ' ' || " +
        "entity_with_relationship.count AS 'search_column' " +
        "FROM entity_with_relationship " +
        "WHERE entity_with_relationship.value=search_column "
    val searchColumn = concat(
      ENTITY_WITH_RELATIONSHIP.VALUE,
      " ".asColumn,
      ENTITY_WITH_RELATIONSHIP.COUNT
    ) AS "search_column"
    (SELECT
        COLUMNS arrayOf(ENTITY_WITH_RELATIONSHIP.all(), searchColumn)
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE IS (searchColumn)))
      .isEqualTo(expected)

    expected = "SELECT trim(entity_with_relationship.value) AS 'trimmed_title' " +
        "FROM entity_with_relationship " +
        "WHERE trim(entity_with_relationship.value)=trimmed_title "
    val trimmedTitle = ENTITY_WITH_RELATIONSHIP.VALUE.trim() AS "trimmed_title"
    (SELECT
        COLUMN trimmedTitle
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE.trim() IS trimmedTitle))
      .isEqualTo(expected)
  }

  @Test
  fun betweenComplex() {
    val expectedBase = "SELECT * FROM entity_with_relationship "
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val randomSimpleMutableEntity2 = SimpleMutableEntity.newRandom()
    val simpleMutableEntityId = checkNotNull(randomSimpleMutableEntity.id)
    val simpleMutableEntityId2 = checkNotNull(randomSimpleMutableEntity2.id)

    var expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN ? AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND simpleMutableEntityId2)))
      .isEqualTo(expected, simpleMutableEntityId.toString(), simpleMutableEntityId2.toString())

    expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN ? AND simple_mutable_entity.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND SIMPLE_MUTABLE_ENTITY.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN simple_mutable_entity.id AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN simple_mutable_entity.id AND " +
        "entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            SIMPLE_MUTABLE_ENTITY.ID AND ENTITY_WITH_RELATIONSHIP.ID
          )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND " +
        "entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND ENTITY_WITH_RELATIONSHIP.ID
          )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND " +
        "entity_with_relationship.related_entity "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
          )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND simpleMutableEntityId
          )
        ))
      .isEqualTo(expected, simpleMutableEntityId.toString())
  }

  @Test
  fun betweenComplexAliased() {
    val expectedBase = "SELECT * FROM entity_with_relationship AS m "
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val randomSimpleMutableEntity2 = SimpleMutableEntity.newRandom()
    val simpleMutableEntityId = checkNotNull(randomSimpleMutableEntity.id)
    val simpleMutableEntityId2 = checkNotNull(randomSimpleMutableEntity2.id)
    val a = SIMPLE_MUTABLE_ENTITY AS "a"
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND simpleMutableEntityId2)))
      .isEqualTo(expected, simpleMutableEntityId.toString(), simpleMutableEntityId2.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND simple_mutable_entity.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND SIMPLE_MUTABLE_ENTITY.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND a.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND a.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN simple_mutable_entity.id AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN a.id AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (a.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN simple_mutable_entity.id AND m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND m.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN a.id AND m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (a.ID AND m.ID)))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE m.related_entity BETWEEN entity_with_relationship.related_entity AND m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN m.related_entity AND m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (m.RELATED_ENTITY AND m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN entity_with_relationship.related_entity AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN m.related_entity AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (m.RELATED_ENTITY AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())
  }

  @Test
  fun rawExpr() {
    var sql = "simple_mutable_entity.value IS NOT NULL"
    sql.expr.isEqualTo(sql)

    sql = "simple_mutable_entity.value = ?"
    sql.expr("asd").isEqualTo(sql, "asd")

    sql = "simple_mutable_entity.value = ? AND simple_mutable_entity.value != ?"
    sql.expr("asd", "dsa").isEqualTo(sql, "asd", "dsa")
  }

  @Test
  fun rawExprWithNonRawExpr() {
    var sql = "simple_mutable_entity.value = ?"
    var expr = (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa") AND sql.expr("asd")
    expr.isEqualTo("(simple_mutable_entity.value!=? AND $sql)", "dsa", "asd")

    sql = "simple_mutable_entity.value = ?"
    expr = sql.expr("asd") AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa")
    expr.isEqualTo("($sql AND simple_mutable_entity.value!=?)", "asd", "dsa")

    sql = "simple_mutable_entity.value IS NOT NULL"
    expr = SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd" AND sql.expr
    expr.isEqualTo("(simple_mutable_entity.value!=? AND $sql)", "asd")

    sql = "simple_mutable_entity.value IS NOT NULL"
    expr = sql.expr AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")
    expr.isEqualTo("($sql AND simple_mutable_entity.value!=?)", "asd")
  }

  @Test
  fun exprSimple() {
    assertSimpleExpr("=?", SIMPLE_MUTABLE_ENTITY.VALUE IS "asd")
    assertSimpleExpr("!=?", SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")
    assertSimpleExpr(" IN (?)", SIMPLE_MUTABLE_ENTITY.VALUE IN arrayOf("asd"))
    assertSimpleExpr(" NOT IN (?)", SIMPLE_MUTABLE_ENTITY.VALUE NOT_IN arrayOf("asd"))

    assertSimpleColumnExpr("=", SIMPLE_MUTABLE_ENTITY.VALUE IS ENTITY_WITH_RELATIONSHIP.VALUE)
    assertSimpleColumnExpr("!=", SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT ENTITY_WITH_RELATIONSHIP.VALUE)
  }

  private fun assertSimpleExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value%s "

    (SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE expr)
      .isEqualTo(String.format(expectedBase, operator), "asd")
  }

  private fun assertSimpleColumnExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value%s" +
          "entity_with_relationship.value "

    (SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE expr)
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun unaryExpr() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN.isNotNull))
      .isEqualTo("SELECT * FROM simple_mutable_entity WHERE NOT(simple_mutable_entity.primitive_boolean IS NOT NULL) ")

    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !((SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN GREATER_THAN SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN) AND
          SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN.isNotNull))
      .isEqualTo(
        "SELECT * FROM simple_mutable_entity WHERE NOT((simple_mutable_entity.primitive_boolean>" +
            "simple_mutable_entity.boxed_boolean AND simple_mutable_entity.boxed_boolean IS NOT NULL)) "
      )
  }

  @Test
  fun numericExprWithSameType() {
    assertNumericExpr("=?", (ENTITY_WITH_RELATIONSHIP.COUNT IS 4))
    assertNumericExpr("!=?", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT 4))
    assertNumericExpr(" IN (?)", (ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(4)))
    assertNumericExpr(" NOT IN (?)", (ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(4)))
    assertNumericExpr(">?", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 4))
    assertNumericExpr(">=?", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL 4))
    assertNumericExpr("<?", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 4))
    assertNumericExpr("<=?", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL 4))
  }

  @Test
  fun numericExprWithSameColumnType() {
    assertNumericSameTypeExpr("=", (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("!=", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(">", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(
      operator = ">=",
      expr = (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
    )
    assertNumericSameTypeExpr("<", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("<=", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
  }

  @Test
  fun numericExprWithEquivalentColumnType() {
    assertNumericEquivalentTypeExpr("=", (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("!=", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(">", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(
      operator = ">=",
      expr = (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID)
    )
    assertNumericEquivalentTypeExpr("<", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("<=", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID))
  }

  private fun assertNumericExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.count%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(String.format(expectedBase, operator), "4")
  }

  private fun assertNumericSameTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.count%s" +
          "immutable_value_with_fields.integer "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertNumericEquivalentTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.count%s" +
          "immutable_value_with_fields.id "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun complexExprWithSameType() {
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val randomSimpleMutableEntityId = checkNotNull(randomSimpleMutableEntity.id)

    assertComplexSameTypeExpr(
      operator = "=?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS randomSimpleMutableEntityId
    )
    assertComplexSameTypeExpr(
      operator = "!=?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.isNot(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " IN (?)",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.`in`(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " IN (?)",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.`in`(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " NOT IN (?)",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.notIn(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = ">?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.greaterThan(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = ">=?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.greaterOrEqual(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = "<?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.lessThan(randomSimpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = "<=?",
      simple_mutable_entity = randomSimpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.lessOrEqual(randomSimpleMutableEntityId)
    )
  }

  @Test
  fun complexExprWithEquivalentType() {
    val simpleMutableEntityId: Long = 42

    assertComplexEquivalentTypeExpr(
      operator = "=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "!=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = " IN (?)",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN arrayOf(simpleMutableEntityId)
    )
    assertComplexEquivalentTypeExpr(
      operator = " NOT IN (?)",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN arrayOf(simpleMutableEntityId)
    )
    assertComplexEquivalentTypeExpr(
      operator = ">?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = ">=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "<?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "<=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL simpleMutableEntityId
    )
  }

  @Test
  fun complexExprWithSameColumnType() {
    assertComplexSameColumnTypeExpr(
      operator = "=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "!=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = ">",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = ">=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "<",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "<=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
  }

  @Test
  fun complexExprWithEquivalentColumnType() {
    assertComplexEquivalentColumnTypeExpr(
      operator = "=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "!=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = ">",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = ">=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "<",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "<=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL ENTITY_WITH_RELATIONSHIP.ID
    )
  }

  private fun assertComplexSameTypeExpr(operator: String, simple_mutable_entity: SimpleMutableEntity, expr: Expr) {
    val expectedBase = "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(
        String.format(expectedBase, operator),
        simple_mutable_entity.id.toString()
      )
  }

  private fun assertComplexEquivalentTypeExpr(operator: String, value: Long, expr: Expr) {
    val expectedBase = "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(
        String.format(expectedBase, operator),
        value.toString()
      )
  }

  private fun assertComplexSameColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "entity_with_relationship.related_entity "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertComplexEquivalentColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "entity_with_relationship.id "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun exprComplexAliased() {
    val expectedBase = "SELECT * FROM entity_with_relationship AS m "
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase + "WHERE m.related_entity=? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS checkNotNull(randomSimpleMutableEntity.id)))
      .isEqualTo(expected, randomSimpleMutableEntity.id.toString())

    expected = expectedBase + "WHERE m.related_entity=entity_with_relationship.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.RELATED_ENTITY))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=entity_with_relationship.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.ID))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.ID))
      .isEqualTo(expected)
  }

  @Test
  fun joinComplex() {
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val expectedBase = "SELECT * FROM entity_with_relationship "

    var expected = expectedBase +
        "LEFT JOIN entity_with_relationship ON " +
        "entity_with_relationship.related_entity!=entity_with_relationship.related_entity "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        )))
      .isEqualTo(expected)

    expected = expectedBase +
        "LEFT JOIN entity_with_relationship ON " +
        "entity_with_relationship.related_entity!=entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID
        )))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN entity_with_relationship ON entity_with_relationship.related_entity=? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP ON (
          ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS checkNotNull(randomSimpleMutableEntity.id)
        )))
      .isEqualTo(expected, randomSimpleMutableEntity.id.toString())
  }

  @Test
  fun joinComplexAliased() {
    val randomSimpleMutableEntity = SimpleMutableEntity.newRandom()
    val expectedBase = "SELECT * FROM entity_with_relationship AS m "
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase +
        "LEFT JOIN entity_with_relationship ON " +
        "m.related_entity!=entity_with_relationship.related_entity "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN entity_with_relationship AS m ON m.related_entity!=m.related_entity "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS_NOT m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN entity_with_relationship ON m.related_entity!=entity_with_relationship.id "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN entity_with_relationship AS m ON m.related_entity!=m.id "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS_NOT m.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN entity_with_relationship ON m.related_entity=? "
    (SELECT
        FROM m
        LEFT_JOIN (ENTITY_WITH_RELATIONSHIP.on(m.RELATED_ENTITY IS checkNotNull(randomSimpleMutableEntity.id))))
      .isEqualTo(expected, randomSimpleMutableEntity.id.toString())

    expected = expectedBase + "LEFT JOIN entity_with_relationship AS m ON m.related_entity=? "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.RELATED_ENTITY IS checkNotNull(randomSimpleMutableEntity.id))))
      .isEqualTo(expected, randomSimpleMutableEntity.id.toString())
  }

  @Test
  fun groupByTest() {
    val expectedBase = "SELECT * FROM entity_with_relationship "

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
            "LEFT JOIN immutable_value_with_fields GROUP BY entity_with_relationship.value," +
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
            "LEFT JOIN simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
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
            "LEFT JOIN simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=simple_mutable_entity.primitive_boolean "
      )
  }

  @Test
  fun groupByTestAliased() {
    val expectedBase = "SELECT * FROM entity_with_relationship AS m "
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
            "LEFT JOIN immutable_value_with_fields GROUP BY m.value,m.related_entity " +
            "HAVING m.count=immutable_value_with_fields.integer "
      )

    (SELECT
        FROM m
        LEFT_JOIN s
        GROUP_BY arrayOf(m.VALUE, m.RELATED_ENTITY)
        HAVING (m.COUNT IS s.INTEGER))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN immutable_value_with_fields AS s GROUP BY m.value,m.related_entity " +
            "HAVING m.count=s.integer "
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS true))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=? ",
        BooleanTransformer.objectToDbValue(true)!!.toString()
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS true))
      .isEqualTo(
        expectedBase + "LEFT JOIN simple_mutable_entity AS a GROUP BY a.boxed_boolean HAVING a.primitive_boolean=? ",
        BooleanTransformer.objectToDbValue(true)!!.toString()
      )

    (SELECT
        FROM m
        LEFT_JOIN SIMPLE_MUTABLE_ENTITY
        GROUP_BY SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN
        HAVING (SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN IS SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN simple_mutable_entity GROUP BY simple_mutable_entity.boxed_boolean " +
            "HAVING simple_mutable_entity.primitive_boolean=simple_mutable_entity.primitive_boolean "
      )

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS a.PRIMITIVE_BOOLEAN))
      .isEqualTo(
        expectedBase +
            "LEFT JOIN simple_mutable_entity AS a GROUP BY a.boxed_boolean " +
            "HAVING a.primitive_boolean=a.primitive_boolean "
      )
  }

  @Test
  fun orderByTest() {
    val expectedBase = "SELECT * FROM entity_with_relationship "

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
    val expectedBase = "SELECT * FROM entity_with_relationship "

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

  @Test
  fun simpleSubquery() {
    assertSimpleSubquery("=") { SIMPLE_MUTABLE_ENTITY.VALUE IS it }

    assertSimpleSubquery("!=") { SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT it }

    assertSimpleSubquery(" IN ") { SIMPLE_MUTABLE_ENTITY.VALUE IN it }

    assertSimpleSubquery(" NOT IN ") { SIMPLE_MUTABLE_ENTITY.VALUE NOT_IN it }
  }

  private fun assertSimpleSubquery(operator: String, callback: (SelectNode<String, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM simple_mutable_entity WHERE simple_mutable_entity.value%s" +
          "(SELECT immutable_value_with_fields.string_value FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun numericSubquery() {
    assertSameTypeNumericSubquery("=") { ENTITY_WITH_RELATIONSHIP.COUNT IS it }
    assertSameTypeNumericSubquery("!=") { ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT it }
    assertSameTypeNumericSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.COUNT IN it }
    assertSameTypeNumericSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN it }
    assertSameTypeNumericSubquery(">") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN it }
    assertSameTypeNumericSubquery(">=") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL it }
    assertSameTypeNumericSubquery("<") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN it }
    assertSameTypeNumericSubquery("<=") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL it }

    assertEquivalentTypeNumericSubquery(">") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN it }
    assertEquivalentTypeNumericSubquery(">=") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL it }
    assertEquivalentTypeNumericSubquery("<") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN it }
    assertEquivalentTypeNumericSubquery("<=") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL it }
  }

  private fun assertSameTypeNumericSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.count%s" +
          "(SELECT immutable_value_with_fields.integer FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertEquivalentTypeNumericSubquery(
    operator: String,
    callback: (SelectNode<out Number, Select1, *>) -> Expr
  ) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.count%s" +
          "(SELECT immutable_value_with_fields.a_double FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun complexSubquery() {
    assertSameTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertSameTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertSameTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertSameTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertSameTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertSameTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertSameTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertSameTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }


    assertIdTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertIdTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertIdTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertIdTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertIdTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertIdTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertIdTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertIdTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }


    assertEquivalentTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertEquivalentTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertEquivalentTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertEquivalentTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertEquivalentTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertEquivalentTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertEquivalentTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertEquivalentTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }
  }

  private fun assertSameTypeComplexSubquery(operator: String, callback: (SelectNode<Long?, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT entity_with_relationship.related_entity FROM entity_with_relationship ) "
    val subQuery = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertIdTypeComplexSubquery(operator: String, callback: (SelectNode<Long?, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT entity_with_relationship.id FROM entity_with_relationship ) "
    val subQuery = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.ID
        FROM ENTITY_WITH_RELATIONSHIP)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertEquivalentTypeComplexSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT immutable_value_with_fields.integer FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun unaryMinus() {
    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - 42)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-42)) FROM entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - -42)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-(-42))) FROM entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - 42.asColumn)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-42)) FROM entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - (-42).asColumn)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-(-42))) FROM entity_with_relationship ")
  }

  @Test
  fun avgFunction() {
    (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.ID
        FROM IMMUTABLE_VALUE_WITH_FIELDS
        WHERE ((avg(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) GREATER_THAN 5555.0)
        AND (avg(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE) LESS_THAN 8888.8)))
      .isEqualTo(
        "SELECT immutable_value_with_fields.id FROM immutable_value_with_fields WHERE " +
            "(avg(immutable_value_with_fields.integer)>? AND " +
            "avg(immutable_value_with_fields.a_double)<?) "
      )
  }

  @Test
  fun concatFunction() {
    var expected = "SELECT simple_mutable_entity.id || simple_mutable_entity.value FROM simple_mutable_entity "
    (SELECT
        COLUMN (SIMPLE_MUTABLE_ENTITY.ID concat SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    expected =
      "SELECT simple_mutable_entity.id || simple_mutable_entity.value || " +
          "simple_mutable_entity.primitive_boolean FROM simple_mutable_entity "
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE, SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    (SELECT
        COLUMN (
          (SIMPLE_MUTABLE_ENTITY.ID concat SIMPLE_MUTABLE_ENTITY.VALUE) concat
            SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
  }

  @Test
  fun replaceFunction() {
    var expected = "SELECT replace(simple_mutable_entity.value,'a','____') FROM simple_mutable_entity "
    (SELECT
        COLUMN SIMPLE_MUTABLE_ENTITY.VALUE.replace("a" with "____")
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    expected = "SELECT replace(simple_mutable_entity.value,'a','____') AS 'asd' FROM simple_mutable_entity "
    (SELECT
        COLUMN (SIMPLE_MUTABLE_ENTITY.VALUE.replace("a" with "____") AS "asd")
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
  }

  @Test
  fun valColumn() {
    var expected = "SELECT simple_mutable_entity.id || ' ' || simple_mutable_entity.value FROM simple_mutable_entity "
    val strVal = " ".asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, strVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    strVal.parsesWith(STRING)

    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val intVal = 3.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, intVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    intVal.parsesWith(INTEGER)

    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val longVal = 3L.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, longVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    longVal.parsesWith(LONG)

    val s: Short = 3
    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val shortVal = s.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, shortVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    shortVal.parsesWith(SHORT)

    val b: Byte = 3
    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val byteVal = b.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, byteVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    byteVal.parsesWith(BYTE)

    val f = 3.3f
    expected = "SELECT simple_mutable_entity.id || 3.3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val floatVal = f.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, floatVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    floatVal.parsesWith(FLOAT)

    val d = 3.3
    expected = "SELECT simple_mutable_entity.id || 3.3 || simple_mutable_entity.value FROM simple_mutable_entity "
    val doubleVal = d.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, doubleVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    doubleVal.parsesWith(DOUBLE)
  }

  @Test
  fun addFunction() {
    assertArithmeticExpression(
      '+',
      { v1, v2 -> v1 + v2 },
      { v1, v2 -> v1 + v2 },
      { v1, v2 -> v1 + v2 })
  }

  @Test
  fun subFunction() {
    assertArithmeticExpression(
      '-',
      { v1, v2 -> v1 - v2 },
      { v1, v2 -> v1 - v2 },
      { v1, v2 -> v1 - v2 })
  }

  @Test
  fun mulFunction() {
    assertArithmeticExpression(
      '*',
      { v1, v2 -> v1 * v2 },
      { v1, v2 -> v1 * v2 },
      { v1, v2 -> v1 * v2 })
  }

  @Test
  fun divFunction() {
    assertArithmeticExpression(
      '/',
      { v1, v2 -> v1 / v2 },
      { v1, v2 -> v1 / v2 },
      { v1, v2 -> v1 / v2 })
  }

  @Test
  fun modFunction() {
    assertArithmeticExpression(
      '%',
      { v1, v2 -> v1 % v2 },
      { v1, v2 -> v1 % v2 },
      { v1, v2 -> v1 % v2 })
  }

  @Test
  fun numericArithmeticExpressionsChained() {
    var expected = "SELECT ((((1+2)*(5-3))/2.0)%10.0) FROM immutable_value_with_fields "
    (SELECT
        COLUMN ((((1.asColumn + 2) * (5.asColumn - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT ((((immutable_value_with_fields.integer+2)*(5-3))/2.0)%10.0) FROM immutable_value_with_fields "
    (SELECT
        COLUMN ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * (5.asColumn - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)
  }

  private fun assertArithmeticExpression(
    op: Char,
    columnCallback: (
      NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>,
      NumericColumn<Short, Short, Number, ImmutableValueWithFields, *>
    ) -> NumericColumn<*, *, *, *, *>,
    columnValueCallback: (
      NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>,
      NumericColumn<Long, Long, Number, *, *>
    ) -> NumericColumn<*, *, *, *, *>,
    valueCallback: (NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>, Int) -> NumericColumn<*, *, *, *, *>
  ) {
    var expected = String.format(
      "SELECT (immutable_value_with_fields.integer%simmutable_value_with_fields.a_short) " +
          "FROM immutable_value_with_fields ",
      op
    )
    (SELECT
        COLUMN columnCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s5) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5L.asColumn)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s(-5)) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, (-5L).asColumn)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s5) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s(-5)) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, -5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)
  }
}
